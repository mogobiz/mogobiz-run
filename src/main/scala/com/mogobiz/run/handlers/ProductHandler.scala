package com.mogobiz.run.handlers

import java.text.SimpleDateFormat
import java.util.{UUID, Date, Calendar, Locale}

import com.mogobiz.es.EsClient
import com.mogobiz.es.EsClient._
import com.mogobiz.pay.model.Mogopay.Account
import com.mogobiz.run.config.Settings
import com.mogobiz.run.es._
import com.mogobiz.json.{JacksonConverter, JsonUtil}
import com.mogobiz.run.exceptions.{CommentAlreadyExistsException, NotAuthorizedException}
import com.mogobiz.run.learning.UserActionRegistration
import com.mogobiz.run.model.Learning.UserAction
import com.mogobiz.run.model.RequestParameters._
import com.mogobiz.run.model._
import com.mogobiz.run.services.RateBoService
import com.mogobiz.run.utils.Paging
import com.sksamuel.elastic4s.ElasticDsl.{update => esupdate4s, search => esearch4s, delete => esdelete4s, _}
import com.sksamuel.elastic4s.FilterDefinition
import com.sksamuel.elastic4s.source.DocumentSource
import org.elasticsearch.action.get.MultiGetItemResponse
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.search.{SearchHit, SearchHits}
import org.elasticsearch.search.aggregations.bucket.terms.Terms
import org.elasticsearch.search.sort.SortOrder
import org.json4s.JsonAST.{JObject, JArray, JNothing}
import org.json4s.JsonDSL._
import org.json4s._

import scala.util.{Failure, Success, Try}

class ProductHandler extends JsonUtil {

  val rateService = RateBoService
  private val fieldsToRemoveForProductSearchRendering = List("skus", "features", "resources", "datePeriods", "intraDayPeriods")

  def queryProductsByCriteria(storeCode: String, productRequest: ProductRequest): JValue = {
    if(productRequest.hasPromotion.getOrElse(false) && productRequest.promotionId.isEmpty){
      return Paging.wrap(0, JArray(List.empty), productRequest)
    }
    val _query = productRequest.name match {
      case Some(s) =>
        esearch4s in storeCode -> "product" query {
          matchQuery("name", s)
        }
      case None => esearch4s in storeCode -> "product" query { matchall }
    }

    val lang = if (productRequest.lang == "_all") "" else s"${productRequest.lang}."

    val filters: List[FilterDefinition] = List(
      createOrFilterBySplitValues(productRequest.id, v => createTermFilter("id", Some(v))),
      createOrFilterBySplitValues(productRequest.code, v => createTermFilter("code", Some(v))),
      createOrFilterBySplitValues(productRequest.xtype, v => createTermFilter("xtype", Some(v))),
      createOrFilterBySplitValues(productRequest.categoryPath, v => createRegexFilter("path", Some(v))),
      createOrFilterBySplitValues(productRequest.brandId, v => createTermFilter("brand.id", Some(v))),
      createOrFilterBySplitValues(productRequest.tagName.map(_.toLowerCase), v => createNestedTermFilter("tags", "tags.name.raw", Some(v))),
      createOrFilterBySplitValues(productRequest.notations, v => createNestedTermFilter("notations","notations.notation", Some(v))),
      createOrFilterBySplitKeyValues(productRequest.priceRange, (min, max) => createNumericRangeFilter("price", min, max)),
      createOrFilterBySplitValues(productRequest.creationDateMin, v => createRangeFilter("dateCreated", Some(v), None)),
      createOrFilterBySplitValues(productRequest.promotionId, v => createTermFilter("coupons.id", Some(v))),
      createFeaturedRangeFilters(productRequest),
      createAndOrFilterBySplitKeyValues(productRequest.property, (k, v) => createTermFilter(k, Some(v))),
      createFeaturesFilters(productRequest),
      createVariationsFilters(productRequest)
    ).flatten

    val fieldsToExclude = getAllExcludedLanguagesExceptAsList(storeCode, productRequest.lang) ::: fieldsToRemoveForProductSearchRendering
    val _size: Int = productRequest.maxItemPerPage.getOrElse(100)
    val _from: Int = productRequest.pageOffset.getOrElse(0) * _size
    val _sort = productRequest.orderBy.getOrElse("name.raw")
    val _sortOrder = productRequest.orderDirection.getOrElse("asc")
    lazy val currency = queryCurrency(storeCode, productRequest.currencyCode)
    val response: SearchHits = EsClient.searchAllRaw(
      filterRequest(_query, filters)
        sourceExclude (fieldsToExclude: _*)
        from _from
        size _size
        sort {
        by field _sort order SortOrder.valueOf(_sortOrder.toUpperCase)
      }
    )
    val hits: JArray = response.getHits
    val products: JValue = hits.map {
      hit => renderProduct(hit, productRequest.countryCode, productRequest.currencyCode, productRequest.lang, currency, fieldsToRemoveForProductSearchRendering)
    }
    Paging.wrap(response.getTotalHits.toInt, products, productRequest)
  }

  def queryProductsByFulltextCriteria(storeCode: String, params: FullTextSearchProductParameters): JValue = {
    val fieldNames = List("name", "description", "descriptionAsText", "keywords", "path", "category.path")
    val fields: List[String] = fieldNames.foldLeft(List[String]())((A, B) => A ::: getIncludedFieldWithPrefixAsList(storeCode, "", B, params.lang))
    val includedFields: List[String] = List("id") ::: (if (params.highlight) List.empty
    else {
      fieldNames ::: fields
    })
    val highlightedFields: List[String] = fieldNames.foldLeft(List[String]())((A, B) => A ::: getHighlightedFieldsAsList(storeCode, B, params.lang))

    val filters: List[FilterDefinition] = List(
      createOrRegexAndTypeFilter(
        List(
          TypeField("product", "category.path"),
          TypeField("category", "path")
        ),
        params.categoryPath)
    ).flatten

    def _req(_type:String) = {
      val req = esearch4s in storeCode types _type

      if (params.highlight) {
        req highlighting ((fieldNames ::: highlightedFields).map(s => highlight(s)): _*)
      }

      if (filters.nonEmpty) {
        if (filters.size > 1)
          req query {
            params.query
          } query {
            filteredQuery query {
              params.query
            } filter {
              and(filters: _*)
            }
          }
        else
          req query {
            params.query
          } query {
            filteredQuery query {
              params.query
            } filter filters(0)
          }
      }
      else {
        req query {
          params.query
        }
      }
      req from 0 size params.size
      req fields (fieldNames ::: fields: _*) sourceInclude (includedFields: _*)
    }

    val response:List[SearchHits] = multiSearchRaw{
      List(
        _req("category"),
        _req("product"),
        _req("brand"),
        _req("tag"))
    }.toList.flatten

    val rawResult = if (params.highlight) {
      for{
        searchHits:SearchHits <- response
        json:JValue = searchHits
        hits = json \ "hits"
      }yield
      for {
        JObject(result) <- hits.children.children
        JField("_type", JString(_type)) <- result
        JField("_source", JObject(_source)) <- result
        JField("highlight", JObject(highlight)) <- result
      } yield _type -> (_source ::: highlight)
    }.flatten else {
      for{
        searchHits:SearchHits <- response
        json:JValue = searchHits
        hits = json \ "hits"
      }yield
      for {
        JObject(result) <- hits.children.children
        JField("_type", JString(_type)) <- result
        JField("_source", JObject(_source)) <- result
      } yield _type -> _source
    }.flatten

    rawResult.groupBy(_._1).map {
      case (_cat, v) => (_cat, v.map(_._2))
    }

  }

  def getProductsFeatures(storeCode: String, params: CompareProductParameters): JValue = {
    val idList = params.ids.split(",").toList

    val includedFields: List[String] = List("id", "name", "features.name", "features.value") :::
      getIncludedFieldWithPrefixAsList(storeCode, "", "name", params.lang) :::
      getIncludedFieldWithPrefixAsList(storeCode, "features.", "name", params.lang) :::
      getIncludedFieldWithPrefixAsList(storeCode, "features.", "value", params.lang)

    val docs: Array[MultiGetItemResponse] = EsClient.loadRaw(multiget(idList.map(id => get id id from storeCode -> "product" fields (includedFields: _*)): _*))

    val allLanguages: List[String] = getStoreLanguagesAsList(storeCode)

    // Permet de traduire la value ou le name d'une feature
    def translateFeature(feature: JValue, esProperty: String, targetPropery: String): List[JField] = {
      val value = extractJSonProperty(feature, esProperty)

      if (params.lang.equals("_all")) {
        allLanguages.map { lang: String => {
          val v = extractJSonProperty(extractJSonProperty(feature, lang), esProperty)
          if (v == JNothing) JField("-", "-")
          else JField(lang, v)
        }
        }.filter { v: JField => v._1 != "-"} :+ JField(targetPropery, value)
      }
      else {
        val valueInGivenLang = extractJSonProperty(extractJSonProperty(feature, params.lang), esProperty)
        if (valueInGivenLang == JNothing) List(JField(targetPropery, value))
        else List(JField(targetPropery, valueInGivenLang))
      }
    }

    import org.json4s.native.JsonMethods._
    implicit def json4sFormats: Formats = DefaultFormats

    val featuresByNameAndIds: List[(Long, List[(String, JValue, List[JField])])] = (for {
      doc <- docs
      id = doc.getId.toLong
      result: JObject = parse(doc.getResponse.getSourceAsString).extract[JObject]
    } yield {
      val featuresByName: List[(String, JValue, List[JField])] = for {
        JArray(features) <- result \ "features"
        JObject(feature) <- features
        JField("name", JString(name)) <- feature
      } yield (name, JObject(feature), translateFeature(feature, "value", "value"))
      (id, featuresByName)
    }).toList

    // Liste des ids des produits comparés
    val ids: List[Long] = featuresByNameAndIds.map { idAndFeaturesByName: (Long, List[(String, JValue, List[JField])]) => idAndFeaturesByName._1}.distinct

    // Liste des noms des features (avec traduction des noms des features)
    val featuresName: List[(String, List[JField])] = {
      for {feature: (String, JValue, List[JField]) <-
           featuresByNameAndIds.map { idAndFeaturesByName: (Long, List[(String, JValue, List[JField])]) => idAndFeaturesByName._2}.
             flatMap { featuresByName: List[(String, JValue, List[JField])] => featuresByName}
      } yield (feature._1, translateFeature(feature._2, "name", "label"))
    }.distinct

    // List de JObject correspond au contenu de la propriété "result" du résultat de la méthode
    // On construit pour chaque nom de feature et pour chaque id de produit
    // la résultat de la comparaison en gérant les traductions et en mettant "-"
    // si une feature n'existe pas pour un produit
    val resultContent: List[JObject] = featuresName.map { featureName: (String, List[JField]) =>

      // Contenu pour la future propriété "values" du résultat
      val valuesList = ids.map { id: Long =>
        val featuresByName: Option[(Long, List[(String, JValue, List[JField])])] = featuresByNameAndIds.find { idAndFeaturesByName: (Long, List[(String, JValue, List[JField])]) => idAndFeaturesByName._1 == id}
        if (featuresByName.isDefined) {
          val feature: Option[(String, JValue, List[JField])] = featuresByName.get._2.find { nameAndFeature: (String, JValue, List[JField]) => nameAndFeature._1 == featureName._1}
          if (feature.isDefined) {
            JObject(feature.get._3)
          }
          else JObject(List(JField("value", "-")))
        }
        else JObject(List(JField("value", "-")))
      }

      // Récupération des valeurs différents pour le calcul de l'indicateur
      val uniqueValue = valuesList.map { valueObject: JValue =>
        extractJSonProperty(valueObject, "value") match {
          case s: JString => s.s
          case _ => "-"
        }
      }.distinct

      JObject(featureName._2 :+ JField("values", valuesList) :+ JField("indicator", if (uniqueValue.size == 1) "0" else "1"))
    }

    val jFieldIds = JField("ids", JArray(ids.map { id: Long => JString(String.valueOf(id))}))
    var jFieldResult = JField("result", JArray(resultContent))
    JObject(List(jFieldIds, jFieldResult))
  }

  def getProductDetails(store: String, params: ProductDetailsRequest, productId: Long, uuid: String): JValue = {
    if (params.historize) {
      // We store in User history only if it is a end user action
      UserActionRegistration.register(store, uuid, productId.toString, UserAction.View)
      addToHistory(store, productId, uuid)
    }
    queryProductById(store, productId, params)
  }

  def getProductDates(storeCode: String, date:Option[String], productId: Long, uuid: String): JValue = {
    val filters: List[FilterDefinition] = List(createTermFilter("id", Some(s"$id"))).flatten
    val hits: SearchHits = EsClient.searchAllRaw(
      filterRequest(esearch4s in storeCode -> "product", filters) sourceInclude (List("datePeriods", "intraDayPeriods"): _*)
    )
    val inPeriods: List[IntraDayPeriod] = hits.getHits.map(hit => hit.field("intraDayPeriods").getValue[List[IntraDayPeriod]]).flatten.toList
    val outPeriods: List[EndPeriod] = hits.getHits.map(hit => hit.field("datePeriods").getValue[List[EndPeriod]]).flatten.toList
    //date or today
    val now = Calendar.getInstance().getTime
    val today = getCalendar(sdf.parse(sdf.format(now)))
    var startCalendar = getCalendar(sdf.parse(date.getOrElse(sdf.format(now))))
    if (startCalendar.compareTo(today) < 0) {
      startCalendar = today
    }
    val endCalendar = getCalendar(startCalendar.getTime)
    endCalendar.add(Calendar.MONTH, 1)
    def checkDate(currentDate: Calendar, endCalendar: Calendar, acc: List[String]): List[String] = {
      if (!currentDate.before(endCalendar)) acc
      else {
        currentDate.add(Calendar.DAY_OF_YEAR, 1)
        if (isDateIncluded(inPeriods, currentDate) && !isDateExcluded(outPeriods, currentDate)) {
          val date = sdf.format(currentDate.getTime)
          checkDate(currentDate, endCalendar, date :: acc)
        } else {
          checkDate(currentDate, endCalendar, acc)
        }
      }
    }
    implicit def json4sFormats: Formats = DefaultFormats
    checkDate(getCalendar(startCalendar.getTime), endCalendar, List()).reverse
  }

  def getProductTimes(storeCode: String, date:Option[String], productId: Long, uuid: String): JValue = {
    val filters: List[FilterDefinition] = List(createTermFilter("id", Some(s"$id"))).flatten
    val hits: SearchHits = EsClient.searchAllRaw(
      filterRequest(esearch4s in storeCode -> "product", filters) sourceInclude (List("intraDayPeriods"): _*)
    )
    val intraDayPeriods: List[IntraDayPeriod] = hits.getHits.map(hit => hit.field("intraDayPeriods").getValue[List[IntraDayPeriod]]).flatten.toList
    //date or today
    val day = getCalendar(sdf.parse(date.getOrElse(sdf.format(Calendar.getInstance().getTime))))
    implicit def json4sFormats: Formats = DefaultFormats
    //TODO refacto this part with the one is in isIncluded method
    intraDayPeriods.filter {
      //get the matching periods
      period => {
        val dow = day.get(Calendar.DAY_OF_WEEK)
        //TODO rework weekday definition !!!
        val included = dow match {
          case Calendar.MONDAY => period.weekday1
          case Calendar.TUESDAY => period.weekday2
          case Calendar.WEDNESDAY => period.weekday3
          case Calendar.THURSDAY => period.weekday4
          case Calendar.FRIDAY => period.weekday5
          case Calendar.SATURDAY => period.weekday6
          case Calendar.SUNDAY => period.weekday7
        }
        val cond = included &&
          day.getTime.compareTo(period.startDate) >= 0 &&
          day.getTime.compareTo(period.endDate) <= 0
        cond
      }
    }.map {
      //get the start date hours value only
      period => {
        // the parsed date returned by ES is parsed according to the server Timezone and so it returns 16 (parsed value) instead of 15 (ES value)
        // but what it must return is the value from ES
        hours.format(getFixedDate(period.startDate).getTime)
      }
    }
  }

  def updateProductNotations(storeCode: String, productId: Long, values: List[JValue]): Boolean = {
    /* marche pas avec un script :(
    val script = "ctx._source.notations4=notations"
    //val notations = compact(render(JField("notations", JArray(values))))
    val notations = compact(render(values))
    println(notations)
    val notations_str ="""[{"notation":"2","nbcomments":2},{"notation":"3","nbcomments":2},{"notation":"5","nbcomments":2},{"notation":"1","nbcomments":1}]"""
    println(notations_str)
    val req = esupdate4s id productId in storeCode -> "product" script script params {"notations" -> Map("notation" -> 2) }
    println(req)
    val res = EsClient.updateRaw(req)
    println(res)
    */

    val res = EsClient.loadRaw(get(productId) from storeCode -> "product").get
    val v1 = res.getVersion
    val product = response2JValue(res)

    val notations = JObject(("notations", JArray(values)))
    val updatedProduct = (product removeField { f => f._1 == "notations"}) merge notations
    val res2 = EsClient.updateRaw(esupdate4s id productId in storeCode -> "product" doc updatedProduct retryOnConflict 4)
    //res2.getVersion > v1
    true
  }

  def deleteComment(storeCode: String, productId: Long, account: Option[Account], commentId: String): Unit = {
    val userId = account.map{ c => c.uuid }.getOrElse(throw new NotAuthorizedException(""))
    findComment(storeCode, productId, userId).find{comment => comment.id == commentId}.getOrElse(throw new NotAuthorizedException(""))

    EsClient.deleteRaw(esdelete4s id commentId from s"${commentIndex(storeCode)}/comment" refresh true)
  }

  @throws[NotAuthorizedException]
  def updateComment(storeCode: String, productId: Long, account: Option[Account], commentId: String, params: CommentPutRequest): Unit = {
    val userId = account.map{ c => c.uuid }.getOrElse(throw new NotAuthorizedException(""))
    val oldComment = findComment(storeCode, productId, userId).find{comment => comment.id == commentId}.getOrElse(throw new NotAuthorizedException(""))

    val newComment = oldComment.copy(
      subject = params.subject.getOrElse(oldComment.subject),
      comment = params.comment.getOrElse(oldComment.comment),
      notation = params.notation.getOrElse(oldComment.notation)
    )

    EsClient.updateRaw(esupdate4s id commentId in commentIndex(storeCode) -> "comment" doc new DocumentSource {
      override def json: String = JacksonConverter.serialize(newComment)
    } retryOnConflict 4)
  }

  def noteComment(storeCode: String, productId: Long, commentId: String, params: NoteCommentRequest) : Unit = {
    val script = if (params.note == 1) "ctx._source.useful = ctx._source.useful + 1"
    else "ctx._source.notuseful = ctx._source.notuseful + 1"
    val req = esupdate4s id commentId in commentIndex(storeCode) -> "comment" script script retryOnConflict 4
    EsClient.updateRaw(req)
  }

  @throws[NotAuthorizedException]
  @throws[CommentAlreadyExistsException]
  def createComment(storeCode: String, productId: Long, req: CommentRequest, account: Option[Account]): Comment = {
    require(!storeCode.isEmpty)
    require(productId > 0)

    val userId = account.map{ c => c.uuid }.getOrElse(throw new NotAuthorizedException(""))
    val surname = account.map{ c => c.firstName.getOrElse("") + " " + c.lastName.getOrElse("") }.getOrElse(throw new NotAuthorizedException(""))

    findComment(storeCode, productId, userId).map{comment => throw new CommentAlreadyExistsException()}

    val comment = Try(Comment(UUID.randomUUID().toString, userId, surname, req.notation, req.subject, req.comment, req.externalCode, req.created, productId))
    comment match {
      case Success(s) =>
        Comment(EsClient.index[Comment](commentIndex(storeCode), s), userId, surname, req.notation, req.subject, req.comment, req.externalCode, req.created, productId)
      case Failure(f) => throw f
    }
  }

  private def findComment(storeCode: String, productId: Long, userId: String) = {
    val query = must(termQuery("productId", s"$productId"),matchQuery("userId", userId))
    EsClient.searchRaw(esearch4s in commentIndex(storeCode) -> "comment" query query).map(deserializeComment)
  }

  def deserializeComment(hit: SearchHit): Comment = {
    val c: Comment = JacksonConverter.deserialize[Comment](hit.getSourceAsString)
    Comment(hit.getId, c.userId, c.surname, c.notation, c.subject, c.comment, c.externalCode, c.created, c.productId, c.useful, c.notuseful)
  }

  def getComment(storeCode: String, productId: Long, req: CommentGetRequest): JValue = {

    val size = req.maxItemPerPage.getOrElse(100)
    val from = req.pageOffset.getOrElse(0) * size
    val filters: List[FilterDefinition] = List(createTermFilter("productId", Some(s"$productId"))).flatten
    val hits: SearchHits = EsClient.searchAllRaw(
      filterRequest(esearch4s in commentIndex(storeCode) -> "comment", filters)
        from from
        size size
        sort {
        by field "created" order SortOrder.DESC
      }
    )
    val comments: List[Comment] = hits.getHits.map(deserializeComment).toList
    val paging = Paging.add(hits.getTotalHits.toInt, comments, req)
    import org.json4s._
    import org.json4s.jackson.Serialization
    implicit val formats = Serialization.formats(NoTypeHints)
    Extraction.decompose(paging)
  }

  def getProductHistory(storeCode: String, sessionId: String, currency: Option[String], country: Option[String], lang: String): List[JValue] = {
    implicit def json4sFormats: Formats = DefaultFormats
    val ids: List[Long] =
      EsClient.loadRaw(get id sessionId from(historyIndex(storeCode), "history")) match {
        case Some(s) => (response2JValue(s) \ "productIds").extract[List[String]].map(_.toLong).reverse
        case None => List.empty
      }
    if (ids.isEmpty) List()
    else getProductsByIds(
      storeCode, ids, ProductDetailsRequest(historize = false, None, currency, country, lang)
    )
  }

  def getProductsByNotation(storeCode: String, lang: String): List[JValue] = {
    val productsByNotation: SearchResponse = EsClient().execute(
      esearch4s in s"${storeCode}_comment" types "comment" aggs {
        aggregation terms "products" field "productId" order Terms.Order.aggregation("avg_notation", false) aggregations (
          aggregation avg "avg_notation" field "notation"
          )
      }
    ).await
    import scala.collection.JavaConversions._
    val ids: List[Long] =
      (for (
        bucket: Terms.Bucket <- productsByNotation.getAggregations.get[Terms]("products").getBuckets
      ) yield {
        bucket.getKey.toLong
      }).toList
    if (ids.isEmpty) List() else getProductsByIds(storeCode, ids, new ProductDetailsRequest(false, None, None, None, lang))
  }

  def querySuggestions(storeCode: String, productId: Long, lang: String): JArray = {
    val fieldsToExclude = getAllExcludedLanguagesExceptAsList(storeCode, lang) ::: fieldsToRemoveForProductSearchRendering
    val req = esearch4s in storeCode -> "suggestion" filter termFilter("suggestion._parent", productId)
    println(req._builder.toString)
    val response: SearchHits = EsClient.searchAllRaw(req)
    response.getHits
  }

  /**
   * Query for products given a list of product ids
   *
   * @param store - store
   * @param ids - product ids
   * @param req - product request
   * @return products
   */
  private def getProductsByIds(store: String, ids: List[Long], req: ProductDetailsRequest): List[JValue] = {
    lazy val currency = queryCurrency(store, req.currency)
    val products: List[MultiGetItemResponse] = EsClient.loadRaw(multiget(ids.map(id => get id id from store -> "product"): _*)).toList
    for (product <- products if !product.isFailed) yield {
      val p: JValue = product.getResponse
      renderProduct(p, req.country, req.currency, req.lang, currency, List())
    }
  }

  private def commentIndex(store: String): String = {
    s"${store}_comment"
  }

  private def historyIndex(store: String): String = {
    s"${store}_history"
  }

  /**
   * Add the product (id) to the list of the user visited products through its sessionId
   * @param store - store code
   * @param productId - product id
   * @param sessionId - session id
   * @return
   */
  private def addToHistory(store: String, productId: Long, sessionId: String): Boolean = {

    //add to the end because productIds is an ArrayList (performance issue if we use ArrayList.add(0,_) => so last is newer
    val script = "if(ctx._source.productIds.contains(pid)){ ctx._source.productIds.remove(ctx._source.productIds.indexOf(pid))}; " +
      "ctx._source.productIds += pid;" +
      "if (ctx._source.productIds.size() > " + Settings.visitedProduct.max + ") {ctx._source.productIds.remove(0)}"
    EsClient.updateRaw(
      esupdate4s id sessionId in s"${historyIndex(store)}/history" script
        script
        params {
        "pid" -> s"$productId"
      } upsert {
        "productIds" -> Seq(s"$productId")
      } retryOnConflict 4
    )
    true
  }

  /**
   * get the product detail
   * @param store - store code
   * @param id - product id
   * @param req - request details
   * @return
   */
  private def queryProductById(store: String, id: Long, req: ProductDetailsRequest): Option[JValue] = {
    lazy val currency = queryCurrency(store, req.currency)
    val response = multiSearchRaw(
      List(
        esearch4s in store -> "product" query {
          ids(List(s"$id"))
        },
        esearch4s in store -> "stock" filter termFilter("productId", id)
      )
    )
    response(0) match {
      case Some(p) =>
        val product = renderProduct(p.getHits()(0), req.country, req.currency, req.lang, currency, List())
        response(1) match {
          case Some(s) =>
            Some(JObject(product.asInstanceOf[JObject].obj :+ JField("stocks", s.getHits)))
          case None => Some(product)
        }
      case None => None
    }
  }

  def renderProduct(product: JsonAST.JValue, country: Option[String], currency: Option[String], lang: String, cur: com.mogobiz.run.model.Currency, fieldsToRemove: List[String]): JsonAST.JValue = {
    if (country.isEmpty || currency.isEmpty) {
      product
    } else {
      implicit def json4sFormats: Formats = DefaultFormats

      val jprice = product \ "price"
      /*
      println(jprice)
      val price = jprice match {
        case JInt(v) =>  v.longValue()
        case JLong(v) => v
      }*/
      val price = jprice.extract[Int].toLong //FIXME ???

      val lrt = for {
        localTaxRate@JObject(x) <- product \ "taxRate" \ "localTaxRates"
        if x contains JField("country", JString(country.get.toUpperCase)) //WARNING toUpperCase ??
        JField("rate", value) <- x} yield value

      val taxRate = lrt.headOption match {
        case Some(JDouble(x)) => x
        case Some(JInt(x)) => x.toDouble
        //      case Some(JNothing) => 0.toDouble
        case _ => 0.toDouble
      }

      val locale = new Locale(lang)
      val endPrice = price + (price * taxRate / 100d)
      val formatedPrice = rateService.formatPrice(price, cur, locale)
      val formatedEndPrice = rateService.formatPrice(endPrice, cur, locale)
      val additionalFields = ("localTaxRate" -> taxRate) ~
        ("endPrice" -> endPrice) ~
        ("formatedPrice" -> formatedPrice) ~
        ("formatedEndPrice" -> formatedEndPrice)

      val renderedProduct = product merge additionalFields
      //removeFields(renderedProduct,fieldsToRemove)
      renderedProduct
    }
    /* calcul prix Groovy
    def localTaxRates = product['taxRate'] ? product['taxRate']['localTaxRates'] as List<Map> : []
    def localTaxRate = localTaxRates?.find { ltr ->
      ltr['country'] == country && (!state || ltr['stateCode'] == state)
    }
    def taxRate = localTaxRate ? localTaxRate['rate'] : 0f
    def price = product['price'] ?: 0l
    def endPrice = (price as long) + ((price * taxRate / 100f) as long)
    product << ['localTaxRate': taxRate,
    formatedPrice: format(price as long, currency, locale, rate as double),
    formatedEndPrice: format(endPrice, currency, locale, rate as double)
    */
  }

  private val sdf = new SimpleDateFormat("yyyy-MM-dd")
  //THH:mm:ssZ
  private val hours = new SimpleDateFormat("HH:mm")

  /**
   * Renvoie le filtres permettant de filtrer les produits mis en avant
   * si la requête le demande
   * @param req - product request
   * @return FilterDefinition
   */
  private def createFeaturedRangeFilters(req: ProductRequest): Option[FilterDefinition] = {
    if (req.featured.getOrElse(false)) {
      val today = sdf.format(Calendar.getInstance().getTime)
      val list = List(
        createRangeFilter("startFeatureDate", None, Some(s"$today")),
        createRangeFilter("stopFeatureDate", Some(s"$today"), None)
      ).flatten
      Some(and(list: _*))
    }
    else None
  }

  /**
   * Renvoie le filtre pour les features
   * @param req - product request
   * @return FilterDefinition
   */
  private def createFeaturesFilters(req: ProductRequest): Option[FilterDefinition] = {
    createAndOrFilterBySplitKeyValues(req.feature, (k, v) => {
      Some(
        must(
          List(
            createNestedTermFilter("features", s"features.name.raw", Some(k)),
            createNestedTermFilter("features", s"features.value.raw", Some(v))
          ).flatten: _*
        )
      )
    })
  }

  /**
   * Renvoie la liste des filtres pour les variations
   * @param req - product request
   * @return FilterDefinition
   */
  private def createVariationsFilters(req: ProductRequest): Option[FilterDefinition] = {
    createAndOrFilterBySplitKeyValues(req.variations, (k, v) => {
      Some(
        or(
          must(
            List(
              createTermFilter(s"skus.variation1.name.raw", Some(k)),
              createTermFilter(s"skus.variation1.value.raw", Some(v))
            ).flatten:_*
          )
          ,must(
            List(
              createTermFilter(s"skus.variation2.name.raw", Some(k)),
              createTermFilter(s"skus.variation2.value.raw", Some(v))
            ).flatten:_*
          )
          ,must(
            List(
              createTermFilter(s"skus.variation3.name.raw", Some(k)),
              createTermFilter(s"skus.variation3.value.raw", Some(v))
            ).flatten:_*
          )
        )
      )
    })
  }
  private def getCalendar(d: Date): Calendar = {
    val cal = Calendar.getInstance()
    cal.setTime(d)
    cal
  }

  /**
   * Fix the date according to the timezone
   * @param d - date
   * @return
   */
  private def getFixedDate(d: Date): Calendar = {
    val fixeddate = Calendar.getInstance()
    fixeddate.setTime(new Date(d.getTime - fixeddate.getTimeZone.getRawOffset))
    fixeddate
  }

  /**
   *
   * http://tutorials.jenkov.com/java-date-time/java-util-timezone.html
   * http://stackoverflow.com/questions/19330564/scala-how-to-customize-date-format-using-simpledateformat-using-json4s
   *
   */

  private def isDateIncluded(periods: List[IntraDayPeriod], day: Calendar): Boolean = {

    periods.exists(period => {
      val dow = day.get(Calendar.DAY_OF_WEEK)
      //TODO rework weekday definition !!!
      val included = dow match {
        case Calendar.MONDAY => period.weekday1
        case Calendar.TUESDAY => period.weekday2
        case Calendar.WEDNESDAY => period.weekday3
        case Calendar.THURSDAY => period.weekday4
        case Calendar.FRIDAY => period.weekday5
        case Calendar.SATURDAY => period.weekday6
        case Calendar.SUNDAY => period.weekday7
      }

      val cond = included &&
        day.getTime.compareTo(period.startDate) >= 0 &&
        day.getTime.compareTo(period.endDate) <= 0
      cond
    })

  }

  private def isDateExcluded(periods: List[EndPeriod], day: Calendar): Boolean = {
    periods.exists(period => {
      day.getTime.compareTo(period.startDate) >= 0 && day.getTime.compareTo(period.endDate) <= 0
    })
  }

}

object ProductDao extends JsonUtil {

  def get(storeCode: String, id: Long) : Option[Mogobiz.Product] = {
    // Création de la requête
    val req = esearch4s in storeCode -> "product" filter termFilter("product.id", id)

    // Lancement de la requête
    EsClient.search[Mogobiz.Product](req)
  }

  def getProductAndSku(storeCode: String, skuId: Long) : Option[(Mogobiz.Product, Mogobiz.Sku)] = {
    // Création de la requête
    val req = esearch4s in storeCode -> "product" filter termFilter("product.skus.id", skuId)

    // Lancement de la requête
    val productOpt = EsClient.search[Mogobiz.Product](req)
    if (productOpt.isDefined) Some((productOpt.get, productOpt.get.skus.find(sku => sku.id == skuId).get))
    else None
  }

  def getSkusIdByCoupon(storeCode: String, couponId: Long): List[Long] = {
    // Création de la requête
    val req = esearch4s in storeCode -> "product" filter termFilter("product.skus.coupons.id", couponId)

    // Lancement de la requête
    val productsList = EsClient.searchAll[Mogobiz.Product](req)

    // Extract des id des Skus
    productsList.toList.flatMap(p => p.skus.filter{sku => sku.coupons.exists(c => c.id == couponId)}.map(sku => sku.id))
  }

}
