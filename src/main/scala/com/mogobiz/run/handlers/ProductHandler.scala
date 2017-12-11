/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import java.util.{Calendar, Date, Locale, UUID}

import akka.actor.Props
import com.mogobiz.es.EsClient.multiSearchRaw
import com.mogobiz.es.{EsClient, _}
import com.mogobiz.json.{JacksonConverter, JsonUtil}
import com.mogobiz.pay.model.Account
import com.mogobiz.run.actors.EsUpdateActor
import com.mogobiz.run.actors.EsUpdateActor.ProductNotationsUpdateRequest
import com.mogobiz.run.es._
import com.mogobiz.run.exceptions.{CommentAlreadyExistsException, NotAuthorizedException, NotFoundException}
import com.mogobiz.run.learning.UserActionRegistration
import com.mogobiz.run.model.Learning.UserAction
import com.mogobiz.run.model.Mogobiz.Suggestion
import com.mogobiz.run.model.RequestParameters._
import com.mogobiz.run.model._
import com.mogobiz.run.services.RateBoService
import com.mogobiz.run.utils.Paging
import com.mogobiz.system.ActorSystemLocator
import com.sksamuel.elastic4s.http.ElasticDsl.{delete => esdelete4s, search => esearch4s, update => esupdate4s, _}
import com.sksamuel.elastic4s.http.get.GetResponse
import com.sksamuel.elastic4s.http.search.{SearchHits, SearchResponse}
import com.sksamuel.elastic4s.searches.SearchDefinition
import com.sksamuel.elastic4s.searches.aggs.TermsOrder
import com.sksamuel.elastic4s.searches.queries.{BoolQueryDefinition, QueryDefinition}
import com.sksamuel.elastic4s.searches.sort.FieldSortDefinition
import com.typesafe.scalalogging.Logger
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy
import org.elasticsearch.search.aggregations.bucket.terms.Terms
import org.elasticsearch.search.sort.SortOrder
import org.joda.time.format.DateTimeFormat
import org.json4s
import org.json4s.JsonAST.{JArray, JObject}
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization
import org.slf4j.LoggerFactory
import scalikejdbc.DBSession
import com.mogobiz.utils.GlobalUtil._
import com.sksamuel.elastic4s.script.ScriptDefinition
import com.mogobiz.es._
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class ProductHandler extends JsonUtil {

  private val MIN_NOTATION = 1
  private val MAX_NOTATION = 5
  private val ES_TYPE_PRODUCT = "product"

  val rateService = RateBoService
  private val fieldsToRemoveForProductSearchRendering = List("skus", "features", "resources", "datePeriods", "intraDayPeriods")

  def queryProductsByCriteria(storeCode: String, productRequest: ProductRequest): JValue = {
    if (productRequest.hasPromotion.getOrElse(false) && productRequest.promotionId.isEmpty) {
      return Paging.wrap(0, JArray(List.empty), 0, productRequest)
    }

    val _query: QueryDefinition = productRequest.fullText match {
      case Some(ft) => ft
      case None => productRequest.name match {
        case Some(s) =>
          matchQuery("name", s)
        case None => matchAllQuery
      }
    }

    val lang = if (productRequest.lang == "_all") "" else s"${productRequest.lang}."

    val priceWithVatField = productRequest.countryCode match {
      case Some(countryCode) => s"$countryCode.saleEndPrice"
      case _ => "salePrice"
    }

    val defaultStockFilter: Option[BoolQueryDefinition] = if (productRequest.inStockOnly.getOrElse(true)) {
      val orFilters = List(
        not(existsQuery("stockAvailable")),
        createtermQuery("stockAvailable", Some(true)).get
      )
      Some(should(orFilters))
    } else {
      None
    }

    val filters: List[QueryDefinition] = List(
      defaultStockFilter,
      productRequest.countryCode.map { countryCode => termQuery(s"${countryCode}.enabled", true) },
      createOrFilterBySplitValues(productRequest.id, v => createtermQuery("id", Some(v))),
      createOrFilterBySplitValues(productRequest.code, v => createtermQuery("code", Some(v))),
      createOrFilterBySplitValues(productRequest.xtype, v => createtermQuery("xtype", Some(v))),
      createOrFilterBySplitValues(productRequest.categoryPath, v => createRegexFilter("path", Some(v))),
      createOrFilterBySplitValues(productRequest.brandId, v => createtermQuery("brand.id", Some(v))),
      createOrFilterBySplitValues(productRequest.tagName.map(_.toLowerCase), v => createNestedtermQuery("tags", "tags.name", Some(v))),
      createOrFilterBySplitValues(productRequest.notations, v => createNestedtermQuery("notations", "notations.notation", Some(v))),
      createOrFilterBySplitKeyValues(productRequest.priceRange, (min, max) => createNumericRangeFilter(s"skus.$priceWithVatField", min, max)),
      createOrFilterBySplitValues(productRequest.creationDateMin, v => createRangeFilter("dateCreated", Some(v), None)),
      createOrFilterBySplitValues(productRequest.promotionId, v => createtermQuery("coupons.id", Some(v))),
      createFeaturedRangeFilters(productRequest),
      createAndOrFilterBySplitKeyValues(productRequest.property, (k, v) => createtermQuery(k, Some(v))),
      createFeaturesFilters(productRequest),
      createVariationsFilters(productRequest)
    ).flatten

    val fieldsToExclude = getAllExcludedLanguagesExceptAsList(storeCode, productRequest.lang) ::: fieldsToRemoveForProductSearchRendering
    val _size: Int = productRequest.maxItemPerPage.getOrElse(100)
    val _from: Int = productRequest.pageOffset.getOrElse(0) * _size
    val _sortOrder = productRequest.orderDirection.getOrElse("asc").toLowerCase
    val _sort = productRequest.orderBy.getOrElse("name.raw") match {
      case a if a startsWith "name" => s"${lang}name.raw"
      case b if b startsWith "price" => if (_sortOrder == "desc") {
        productRequest.countryCode match {
          case Some(countryCode) => s"$countryCode.maxSaleEndPrice"
          case _ => "maxSalePrice"
        }
      } else {
        productRequest.countryCode match {
          case Some(countryCode) => s"$countryCode.minSaleEndPrice"
          case _ => "minSalePrice"
        }
      }
      case s => s
    }
    lazy val currency = queryCurrency(storeCode, productRequest.currencyCode)
    val response = EsClient.searchAllRaw(
      filterRequest(esearch4s(storeCode -> "product"), filters, _query)
        sourceExclude (fieldsToExclude)
        from _from
        size _size
        sortBy FieldSortDefinition(_sort).sortMode(_sortOrder).order(SortOrder.valueOf(_sortOrder.toUpperCase))
    )
    val products: Array[json4s.JValue] = response.hits.map { hit =>
      renderProduct(storeCode, hit, productRequest.countryCode, productRequest.currencyCode, productRequest.lang, currency, fieldsToRemoveForProductSearchRendering)
    }
    Paging.wrap(response.total, products.toList, products.length, productRequest)
  }

  def queryProductsByFulltextCriteria(storeCode: String, params: FullTextSearchProductParameters): JValue = {
    val fieldNames = List("name", "description", "descriptionAsText", "keywords", "path", "category.path", "brand.name")
    val _size: Int = params.maxItemPerPage.getOrElse(100)
    val _from: Int = params.pageOffset.getOrElse(0) * _size
    val fields: List[String] = fieldNames.foldLeft(List[String]())((A, B) => A ::: getIncludedFieldWithPrefixAsList(storeCode, "", B, params.lang))
    val includedFields: List[String] = List("id", "name", "path") ::: (if (params.highlight) List.empty
    else {
      fieldNames ::: fields
    })
    val highlightedFields: List[String] = fieldNames.foldLeft(List[String]())((A, B) => A ::: getHighlightedFieldsAsList(storeCode, B, params.lang))

    val filters: List[QueryDefinition] = List(
      createOrRegexAndTypeFilter(
        List(
          TypeField("product", "category.path"),
          TypeField("category", "path"),
          TypeField("brand", "categories"),
          TypeField("tag", "categories")
        ),
        params.categoryPath)
    ).flatten

    def _req(_type: String): SearchDefinition = {
      var req: SearchDefinition =
        if (params.highlight) {
          esearch4s(storeCode -> _type) highlighting (fieldNames ::: highlightedFields).map(s => highlight(s))
        }
        else
          esearch4s(storeCode -> _type)

      val filtersToApply: List[QueryDefinition] = if (_type == "product") {
        filters ++ params.country.map { countryCode => termQuery(s"${countryCode}.enabled", true) }
      } else filters

      if (filtersToApply.nonEmpty) {
        req query {
          params.query
        } postFilter {
          must(filtersToApply)
        }
      }
      else {
        req query {
          params.query
        }
      }
      req storedFields (fieldNames ::: fields) sourceInclude (includedFields)
    }

    val response: List[SearchHits] = multiSearchRaw(
      List(
        _req("category"),
        _req("product") from _from size _size,
        _req("brand"),
        _req("tag"))
    ).toList.flatten

    val prds = response(1)

    val rawResult = if (params.highlight) {
      for {
        searchHits: SearchHits <- response
        json: JValue = searchHits
        hits = json \ "hits"
      } yield for {
        JObject(result) <- hits.children.children
        JField("_type", JString(_type)) <- result
        JField("_source", JObject(_source)) <- result
        JField("highlight", JObject(highlight)) <- result
      } yield _type -> (_source ::: highlight)
    }.flatten
    else {
      for {
        searchHits: SearchHits <- response
        json: JValue = searchHits
        hits = json \ "hits"
      } yield for {
        JObject(result) <- hits.children.children
        JField("_type", JString(_type)) <- result
        JField("_source", JObject(_source)) <- result
      } yield _type -> _source
    }.flatten

    val res = rawResult.groupBy(_._1).map {
      case (_cat, v) if _cat == "product" =>
        (_cat, v.map(_._2))
      case (_cat, v) => (_cat, v.map(_._2))
    }
    val resAsJValue: JValue = res
    Paging.wrap(prds.total, resAsJValue, prds.children.children.size, params)
  }

  def getProductsFeatures(storeCode: String, params: CompareProductParameters): JValue = {
    val idList = params.ids.split(",").toList

    val includedFields: List[String] = List("id", "name", "features.name", "features.value") :::
      getIncludedFieldWithPrefixAsList(storeCode, "", "name", params.lang) :::
      getIncludedFieldWithPrefixAsList(storeCode, "features.", "name", params.lang) :::
      getIncludedFieldWithPrefixAsList(storeCode, "features.", "value", params.lang)

    //    val multigetDefinition = multiget(idList.map(id => get id id from storeCode -> "product" fields includedFields fetchSourceContext true))
    val multigetDefinition = multiget(idList.map(id => get(id).from(storeCode -> "product")))

    val docs: Seq[GetResponse] = EsClient.loadRaw(multigetDefinition)
    val allLanguages: List[String] = getStoreLanguagesAsList(storeCode)

    //    docs.foreach { doc =>
    //      import scala.collection.JavaConverters._
    //      doc.getResponse.getFields().asScala.foreach { case (k, v) =>
    //        println(k+"="+ v.getValues.asScala.mkString("::"))
    //      }
    //    }
    // Permet de traduire la value ou le name d'une feature
    def translateFeature(feature: JValue, esProperty: String, targetPropery: String): List[JField] = {
      val value = extractJSonProperty(feature, esProperty)

      if (params.lang.equals("_all")) {
        allLanguages.map {
          lang: String => {
            val v = extractJSonProperty(extractJSonProperty(feature, lang), esProperty)
            if (v == JNothing) JField("-", "-")
            else JField(lang, v)
          }
        }.filter {
          v: JField => v._1 != "-"
        } :+ JField(targetPropery, value)
      } else {
        val valueInGivenLang = extractJSonProperty(extractJSonProperty(feature, params.lang), esProperty)
        if (valueInGivenLang == JNothing) List(JField(targetPropery, value))
        else List(JField(targetPropery, valueInGivenLang))
      }
    }

    implicit def json4sFormats: Formats = DefaultFormats

    val featuresByNameAndIds: List[(Long, List[(String, JValue, List[JField])])] = (for {
      doc <- docs
      id = doc.id.toLong
      result: JObject = parse(doc.sourceAsString).extract[JObject]
    } yield {
      val featuresByName: List[(String, JValue, List[JField])] = for {
        JArray(features) <- result \ "features"
        JObject(feature) <- features
        JField("name", JString(name)) <- feature
      } yield (name, JObject(feature), translateFeature(feature, "value", "value"))
      (id, featuresByName)
    }).toList

    // Liste des ids des produits comparés
    val ids: List[Long] = featuresByNameAndIds.map {
      idAndFeaturesByName: (Long, List[(String, JValue, List[JField])]) => idAndFeaturesByName._1
    }.distinct

    // Liste des noms des features (avec traduction des noms des features)
    val featuresName: List[(String, List[JField])] = {
      for {
        feature: (String, JValue, List[JField]) <- featuresByNameAndIds.map {
          idAndFeaturesByName: (Long, List[(String, JValue, List[JField])]) => idAndFeaturesByName._2
        }.
          flatMap {
            featuresByName: List[(String, JValue, List[JField])] => featuresByName
          }
      } yield (feature._1, translateFeature(feature._2, "name", "label"))
    }.distinct

    // List de JObject correspond au contenu de la propriété "result" du résultat de la méthode
    // On construit pour chaque nom de feature et pour chaque id de produit
    // la résultat de la comparaison en gérant les traductions et en mettant "-"
    // si une feature n'existe pas pour un produit
    val resultContent: List[JObject] = featuresName.map {
      featureName: (String, List[JField]) =>

        // Contenu pour la future propriété "values" du résultat
        val valuesList = ids.map {
          id: Long =>
            val featuresByName: Option[(Long, List[(String, JValue, List[JField])])] = featuresByNameAndIds.find {
              idAndFeaturesByName: (Long, List[(String, JValue, List[JField])]) => idAndFeaturesByName._1 == id
            }
            if (featuresByName.isDefined) {
              val feature: Option[(String, JValue, List[JField])] = featuresByName.get._2.find {
                nameAndFeature: (String, JValue, List[JField]) => nameAndFeature._1 == featureName._1
              }
              if (feature.isDefined) {
                JObject(feature.get._3)
              } else JObject(List(JField("value", "-")))
            } else JObject(List(JField("value", "-")))
        }

        // Récupération des valeurs différents pour le calcul de l'indicateur
        val uniqueValue = valuesList.map {
          valueObject: JValue =>
            extractJSonProperty(valueObject, "value") match {
              case s: JString => s.s
              case _ => "-"
            }
        }.distinct

        JObject(featureName._2 :+ JField("values", valuesList) :+ JField("indicator", if (uniqueValue.size == 1) "0" else "1"))
    }

    val jFieldIds = JField("ids", JArray(ids.map {
      id: Long => JString(String.valueOf(id))
    }))
    var jFieldResult = JField("result", JArray(resultContent))
    JObject(List(jFieldIds, jFieldResult))
  }

  def getProductDetails(store: String, params: ProductDetailsRequest, productId: Long, uuid: String): JValue = {
    if (params.historize) {
      // We store in User history only if it is a end user action
      UserActionRegistration.register(store, uuid, productId.toString, UserAction.View, 1)
      addToHistory(store, productId, uuid)
    }
    queryProductById(store, productId, params)
  }

  def getProductDates(storeCode: String, date: Option[String], productId: Long, uuid: String): JValue = {
    val filters: List[QueryDefinition] = List(createtermQuery("id", Some(s"$uuid"))).flatten
    val hits: SearchHits = EsClient.searchAllRaw(
      filterRequest(esearch4s(storeCode -> "product"), filters) sourceInclude List("datePeriods", "intraDayPeriods")
    )
    val inPeriods: List[IntraDayPeriod] = {
      hits.hits.flatMap(hit => hit.fields("intraDayPeriods").asInstanceOf[List[IntraDayPeriod]]).toList
    }
    val outPeriods: List[EndPeriod] = {
      hits.hits.flatMap(hit => hit.fields("datePeriods").asInstanceOf[List[EndPeriod]]).toList
    }
    //date or today
    val now = Calendar.getInstance().getTimeInMillis
    val today = sdf.parseDateTime(sdf.print(now)).toCalendar(Locale.getDefault())
    var startCalendar = sdf.parseDateTime(date.getOrElse(sdf.print(now))).toCalendar(Locale.getDefault)
    if (startCalendar.compareTo(today) < 0) {
      startCalendar = today
    }
    val endCalendar = startCalendar.clone().asInstanceOf[Calendar]
    endCalendar.add(Calendar.MONTH, 1)

    def checkDate(currentDate: Calendar, endCalendar: Calendar, acc: List[String]): List[String] = {
      if (!currentDate.before(endCalendar)) acc
      else {
        currentDate.add(Calendar.DAY_OF_YEAR, 1)
        if (isDateIncluded(inPeriods, currentDate) && !isDateExcluded(outPeriods, currentDate)) {
          val date = sdf.print(currentDate.getTimeInMillis)
          checkDate(currentDate, endCalendar, date :: acc)
        } else {
          checkDate(currentDate, endCalendar, acc)
        }
      }
    }

    implicit def json4sFormats: Formats = DefaultFormats

    checkDate(getCalendar(startCalendar.getTime), endCalendar, List()).reverse
  }

  def getProductTimes(storeCode: String, date: Option[String], productId: Long, uuid: String): JValue = {
    val filters: List[QueryDefinition] = List(createtermQuery("id", Some(s"$uuid"))).flatten
    val hits: SearchHits = EsClient.searchAllRaw(
      filterRequest(esearch4s(storeCode -> "product"), filters) sourceInclude List("intraDayPeriods")
    )
    val intraDayPeriods: List[IntraDayPeriod] = {
      hits.hits.flatMap(hit => hit.fields("intraDayPeriods").asInstanceOf[List[IntraDayPeriod]]).toList
    }
    //date or today
    val day = sdf.parseDateTime(date.getOrElse(sdf.print(Calendar.getInstance().getTimeInMillis))).toCalendar(Locale.getDefault)

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
        hours.print(getFixedDate(period.startDate).getTimeInMillis)
      }
    }
  }

  def updateProductNotations(indexEs: String, productId: Long, values: List[JValue]): Boolean = {
    val res: GetResponse = EsClient.loadRaw(get(productId) from indexEs -> "product").get
    val v1 = res.version
    val product = response2JValue(res)

    val notations = JObject(("notations", JArray(values)))
    val notation = Notation(UUID.randomUUID().toString, productId, JacksonConverter.asString(notations))
    val updatedProduct = (product removeField {
      f => f._1 == "notations"
    }) merge notations
    
    EsClient.updateRaw((esupdate4s(productId) in indexEs -> "product" doc JacksonConverter.asString(updatedProduct)).retryOnConflict(4))
    true
  }

  def notifyProductNotationChange(indexEs: String, productId: Long): Unit = {
    val system = ActorSystemLocator()
    val actor = system.actorOf(Props[EsUpdateActor])
    actor ! ProductNotationsUpdateRequest(indexEs, productId)
  }

  def deleteComment(storeCode: String, productId: Long, account: Option[Account], commentId: String): Unit = {
    val transactionalBloc = {
      implicit session: DBSession =>
        val userId = account.map {
          c => c.uuid
        }.getOrElse(throw NotAuthorizedException(""))
        findComment(storeCode, productId, userId).find {
          comment => comment.id == commentId
        }.getOrElse(throw NotAuthorizedException(""))
        BOCommentDao.delete(commentId)
    }
    val successBloc = {
      nbDelete: Int =>
        EsClient.deleteRaw(esdelete4s(commentId) from s"${
          commentIndex(storeCode)
        }/comment" refresh RefreshPolicy.IMMEDIATE)
        notifyProductNotationChange(storeCode, productId)
    }

    runInTransaction(transactionalBloc, successBloc)
  }

  @throws[NotAuthorizedException]
  def updateComment(storeCode: String, productId: Long, account: Option[Account], commentId: String, params: CommentPutRequest): Unit = {
    val transactionalBloc = {
      implicit session: DBSession =>
        val userId = account.map {
          c => c.uuid
        }.getOrElse(throw new NotAuthorizedException(""))
        val oldComment = findComment(storeCode, productId, userId).find {
          comment => comment.id == commentId
        }.getOrElse(throw new NotAuthorizedException(""))

        val newComment = oldComment.copy(
          subject = params.subject.getOrElse(oldComment.subject),
          comment = params.comment.getOrElse(oldComment.comment),
          notation = Math.min(Math.max(params.notation.getOrElse(oldComment.notation), MIN_NOTATION), MAX_NOTATION)
        )

        val comment = BOCommentDao.load(storeCode, commentId).getOrElse(throw new NotAuthorizedException(""))
        BOCommentDao.save(comment, newComment)
        newComment
    }

    val successBloc = {
      newComment: Comment =>
        EsClient.updateRaw(esupdate4s(commentId) in commentIndex(storeCode) -> "comment" doc JacksonConverter.serialize(newComment) refresh RefreshPolicy.IMMEDIATE retryOnConflict 4)
    }

    runInTransaction(transactionalBloc, successBloc)
  }

  def noteComment(storeCode: String, productId: Long, commentId: String, params: NoteCommentRequest): Unit = {
    val transactionalBloc = {
      implicit session: DBSession =>
        BOCommentDao.load(storeCode, commentId).flatMap {
          comment =>
            val newComment = findCommentById(storeCode, commentId).map {
              comment =>
                if (params.note == 1) comment.copy(useful = comment.useful + 1)
                else comment.copy(notuseful = comment.notuseful + 1)
            }
            newComment.map {
              c =>
                BOCommentDao.save(comment, c)
            }
            newComment
        }
    }

    val successBloc = {
      newComment: Option[Comment] =>
        newComment.map {
          c: Comment =>
            val script = if (params.note == 1) "ctx._source.useful = ctx._source.useful + 1"
            else "ctx._source.notuseful = ctx._source.notuseful + 1"
            val req = esupdate4s(commentId) in commentIndex(storeCode) -> "comment" script script retryOnConflict 4
            EsClient.updateRaw(req)
            EsClient.updateRaw(esupdate4s(commentId) in commentIndex(storeCode) -> "comment" doc JacksonConverter.serialize(c) refresh RefreshPolicy.IMMEDIATE retryOnConflict 4)
        }
    }

    runInTransaction(transactionalBloc, successBloc)
  }

  @throws[NotAuthorizedException]
  @throws[CommentAlreadyExistsException]
  def createComment(storeCode: String, productId: Long, req: CommentRequest, account: Option[Account]): Comment = {
    val userId = account.map {
      c => c.uuid
    }.getOrElse(throw new NotAuthorizedException(""))
    val surname = account.map {
      c => c.firstName.getOrElse("") + " " + c.lastName.getOrElse("")
    }.getOrElse(throw new NotAuthorizedException(""))
    val notation = Math.min(Math.max(req.notation, MIN_NOTATION), MAX_NOTATION)
    findComment(storeCode, productId, userId).map {
      comment => throw new CommentAlreadyExistsException()
    }

    val newCommentWithoutId = Comment(UUID.randomUUID().toString, userId, surname, notation, req.subject, req.comment, req.externalCode, req.created, productId)
    val newComment = newCommentWithoutId.copy(id = EsClient.indexLowercase(commentIndex(storeCode), newCommentWithoutId, true))

    val transactionalBloc = {
      implicit session: DBSession =>
        BOCommentDao.create(storeCode, newComment)
        newComment
    }

    val successBloc = {
      newComment: Comment => newComment
    }

    val failure = {
      e: Throwable =>
        EsClient.deleteRaw(esdelete4s(newComment.id) from s"${
          commentIndex(storeCode)
        }/comment" refresh RefreshPolicy.IMMEDIATE)
    }

    runInTransaction(transactionalBloc, successBloc)
  }

  private def findComment(storeCode: String, productId: Long, userId: String): Option[Comment]

  = {
    val query = must(termQuery("productId", s"$productId"), matchQuery("userId", userId))
    EsClient.search[Comment](esearch4s(commentIndex(storeCode) -> "comment") query query)
  }

  private def findCommentById(storeCode: String, commentId: String): Option[Comment]

  = {
    EsClient.load[Comment](commentIndex(storeCode), commentId, "comment")
  }

  def getCommentByExternalCode(storeCode: String, productId: Long, account: Option[Account], externalCode: String): Comment = {
    val userId = account.map {
      c => c.uuid
    }.getOrElse(throw new NotAuthorizedException(""))
    val filters: List[QueryDefinition] = List(
      createtermQuery("productId", Some(s"$productId")),
      createtermQuery("userId", Some(userId)),
      createtermQuery("externalCode", Some(externalCode))
    ).flatten
    val req = esearch4s(commentIndex(storeCode) -> "comment")
    EsClient.search[Comment](filterRequest(req, filters)).getOrElse(throw new NotFoundException(""))
  }

  def getComment(storeCode: String, productId: Long, req: CommentGetRequest): JValue = {

    val size = req.maxItemPerPage.getOrElse(100)
    val from = req.pageOffset.getOrElse(0) * size
    val filters: List[QueryDefinition] = List(createtermQuery("productId", Some(s"$productId"))).flatten
    val comments = EsClient.searchAll[Comment](
      filterRequest(esearch4s(commentIndex(storeCode) -> "comment"), filters)
        from from
        size size
        sortByFieldDesc "created"
    )
    val paging = Paging.add(comments.length, comments, req)
    implicit val formats = Serialization.formats(NoTypeHints)
    Extraction.decompose(paging)
  }

  def getProductHistory(storeCode: String, sessionId: String, currency: Option[String], country: Option[String], lang: String): List[JValue] = {
    implicit def json4sFormats: Formats = DefaultFormats

    val ids: List[Long] =
      EsClient.loadRaw(get(sessionId) from(historyIndex(storeCode), "history")) match {
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
      esearch4s(s"${
        storeCode
      }_comment" -> "comment") aggregations {
        termsAgg("products", "productId") order TermsOrder("avg_notation", asc = false) subaggs avgAgg("avg_notation", "notation")
      }).await
    
    import scala.collection.JavaConversions._
    val ids: List[Long] =
      (for (
        bucket: Terms.Bucket <- productsByNotation.aggregations("products").asInstanceOf[Terms].getBuckets
      ) yield {
        bucket.getKey.toString.toLong
      }).toList
    
    if (ids.isEmpty) List() else getProductsByIds(storeCode, ids, ProductDetailsRequest(false, None, None, None, lang))
  }

  def querySuggestions(storeCode: String, productId: Long, lang: String): JArray = {
    val fieldsToExclude = getAllExcludedLanguagesExceptAsList(storeCode, lang) ::: fieldsToRemoveForProductSearchRendering
    val req = esearch4s(storeCode -> "suggestion") query termQuery("suggestion._parent", productId)
    val response: SearchHits = EsClient.searchAllRaw(req)
    response.hits
  }

  def querySuggestionById(storeCode: String, productId: Long, suggestionId: Long): JValue = {
    val filters: List[QueryDefinition] = List(
      createtermQuery("suggestion._parent", Some(productId)),
      createtermQuery("suggestion.id", Some(suggestionId))
    ).flatten
    EsClient.searchRaw(
      filterRequest(esearch4s(storeCode -> "suggestion"), filters)
    )
  }

  /**
    * Query for products given a list of product ids
    *
    * @param store - store
    * @param ids   - product ids
    * @param req   - product request
    * @return products
    */
  private def getProductsByIds(store: String, ids: List[Long], req: ProductDetailsRequest): List[JValue]

  = {
    lazy val currency = queryCurrency(store, req.currency)
    val products: List[GetResponse] = EsClient.loadRaw(multiget(ids.map(id => get(id) from store -> "product"))).toList
    for (product <- products if product.found) yield {
      val p: JValue = parse(product.sourceAsString)
      renderProduct(store, p, req.country, req.currency, req.lang, currency, List())
    }
  }

  private def commentIndex(store: String): String

  = {
    s"${
      store
    }_comment"
  }

  private def historyIndex(store: String): String

  = {
    s"${
      store
    }_history"
  }

  /**
    * Add the product (id) to the list of the user visited products through its sessionId
    *
    * @param store     - store code
    * @param productId - product id
    * @param sessionId - session id
    * @return
    */
  private def addToHistory(store: String, productId: Long, sessionId: String): Boolean = {
    //add to the end because productIds is an ArrayList (performance issue if we use ArrayList.add(0,_) => so last is newer
    val script = "if(ctx._source.productIds.contains(pid)){ ctx._source.productIds.remove(ctx._source.productIds.indexOf(pid))}; " +
      "ctx._source.productIds += pid;" +
      "if (ctx._source.productIds.size() > " + com.mogobiz.run.config.Settings.VisitedProduct.Max + ") {ctx._source.productIds.remove(0)}"
    EsClient.updateRaw(
      esupdate4s(sessionId) in s"${historyIndex(store)}/history" script ScriptDefinition(script, params = Map("pid" -> s"$productId"))
        upsert {
        "productIds" -> Seq(s"$productId")
      } retryOnConflict 4
    )
    true
  }

  /**
    * get the product detail
    *
    * @param store - store code
    * @param id    - product id
    * @param req   - request details
    * @return
    */
  private def queryProductById(store: String, id: Long, req: ProductDetailsRequest): Option[JValue]

  = {
    lazy val currency = queryCurrency(store, req.currency)
    val response = multiSearchRaw(
      List(
        esearch4s(store -> "product") query {
          idsQuery(List(s"$id"))
        },
        esearch4s(store -> "stock") postFilter termQuery("productId", id)
      )
    )
    response(0) match {
      case Some(p) =>
        val product = renderProduct(store, p.hits(0), req.country, req.currency, req.lang, currency, List(), includeNotations = true)
        response(1) match {
          case Some(s) =>
            Some(JObject(product.asInstanceOf[JObject].obj :+ JField("stocks", s.hits)))
          case None => Some(product)
        }
      case None => None
    }
  }

  /**
    * Update product field "stock_available" to qualifie stock state
    *
    * @param indexEs
    * @param productId
    * @param stockAvailable
    * @return
    */
  def updateStockAvailability(indexEs: String, productId: Long, stockAvailable: Boolean) = {
    //    println(s"product.updateStockAvailability productId=$productId, stockValue=$stockAvailable")
    val script = s"ctx._source.stockAvailable=$stockAvailable"
    EsClient.updateRaw(esupdate4s(productId) in indexEs -> ES_TYPE_PRODUCT script script retryOnConflict 4)
  }

  def renderProduct(storeCode: String, product: JValue, country: Option[String], currency: Option[String], lang: String, cur: com.mogobiz.run.model.Currency, fieldsToRemove: List[String], includeNotations: Boolean = false): JsonAST.JValue = {
    implicit def json4sFormats: Formats = DefaultFormats

    val notation = if (includeNotations) {
      // récupération du nombre de commentaires par note + calcul de la moyenne
      val JInt(productId) = (product \ "id")
      val req = filterRequest(esearch4s(s"${
        storeCode
      }_comment" -> "comment"), List(termQuery("productId", productId.toLong))) aggregations {
        termsAgg("notations", "comment.notation")
      }
      val resagg = EsClient searchAgg req

      def computeSumNoteAndSumNbComment(l: List[(Long, Long)]): (Long, Long) = {
        if (l.isEmpty) (0, 0)
        else {
          val r = computeSumNoteAndSumNbComment(l.tail)
          (r._1 + l.head._1 * l.head._2, r._2 + l.head._2)
        }
      }

      val nbCommentsByNote: List[(Long, Long)] = for {
        JArray(buckets) <- resagg \ "notations" \ "buckets"
        JObject(bucket) <- buckets
        JField("key", JInt(key)) <- bucket
        JField("doc_count", JInt(value)) <- bucket
      } yield (key.longValue(), value.longValue())

      val sumNoteAndComment = computeSumNoteAndSumNbComment(nbCommentsByNote)

      JObject(JField("notations", JObject(
        JField("average", JDouble(if (sumNoteAndComment._2 == 0) 0 else sumNoteAndComment._1.toDouble / sumNoteAndComment._2.toDouble)) ::
          JField("nbPosts", JInt(sumNoteAndComment._2)) ::
          nbCommentsByNote.map(note => JField(s"notation_${
            note._1
          }", JInt(note._2)))
      )))
    } else JNothing

    if (country.isEmpty || currency.isEmpty) {
      product merge notation
    } else {
      val jprice = product \ "price"

      val price = try {
        jprice.extract[Int].toLong
      } catch {
        case NonFatal(e) => 0L
      }

      val lrt = for {
        localTaxRate@JObject(x) <- product \ "taxRate" \ "localTaxRates"
        if x contains JField("country", JString(country.get.toUpperCase)) //WARNING toUpperCase ??
        JField("rate", value) <- x
      } yield value

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

      product merge additionalFields merge notation
    }
  }

  private val sdf =
    DateTimeFormat.forPattern("yyyy-MM-dd")
  //THH:mm:ssZ
  private val hours =
    DateTimeFormat.forPattern("HH:mm")

  /**
    * Renvoie le filtres permettant de filtrer les produits mis en avant
    * si la requête le demande
    *
    * @param req - product request
    * @return QueryDefinition
    */
  private def createFeaturedRangeFilters(req: ProductRequest): Option[QueryDefinition]

  = {
    if (req.featured.getOrElse(false)) {
      val today = sdf.print(Calendar.getInstance().getTimeInMillis)
      val list = List(
        createRangeFilter("startFeatureDate", None, Some(s"$today")),
        createRangeFilter("stopFeatureDate", Some(s"$today"), None)
      ).flatten
      Some(must(list))
    } else None
  }

  /**
    * Renvoie le filtre pour les features
    *
    * @param req - product request
    * @return QueryDefinition
    */
  private def createFeaturesFilters(req: ProductRequest): Option[QueryDefinition]

  = {
    createAndOrFilterBySplitKeyValues(req.feature, (k, v) => {
      Some(
        must(
          List(
            createNestedtermQuery("features", s"features.name.raw", Some(k)),
            createNestedtermQuery("features", s"features.value.raw", Some(v))
          ).flatten
        )
      )
    })
  }

  /**
    * Renvoie la liste des filtres pour les variations
    *
    * @param req - product request
    * @return QueryDefinition
    */
  private def createVariationsFilters(req: ProductRequest): Option[QueryDefinition]

  = {
    createAndOrFilterBySplitKeyValues(req.variations, (k, v) => {
      Some(
        should(
          must(
            List(
              createtermQuery(s"skus.variation1.name.raw", Some(k)),
              createtermQuery(s"skus.variation1.value.raw", Some(v))
            ).flatten
          ), must(
            List(
              createtermQuery(s"skus.variation2.name.raw", Some(k)),
              createtermQuery(s"skus.variation2.value.raw", Some(v))
            ).flatten
          ), must(
            List(
              createtermQuery(s"skus.variation3.name.raw", Some(k)),
              createtermQuery(s"skus.variation3.value.raw", Some(v))
            ).flatten
          )
        )
      )
    })
  }

  private def getCalendar(d: Date): Calendar

  = {
    val cal = Calendar.getInstance()
    cal.setTime(d)
    cal
  }

  /**
    * Fix the date according to the timezone
    *
    * @param d - date
    * @return
    */
  private def getFixedDate(d: Date): Calendar

  = {
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

  private def isDateIncluded(periods: List[IntraDayPeriod], day: Calendar): Boolean

  = {

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

  private def isDateExcluded(periods: List[EndPeriod], day: Calendar): Boolean

  = {
    periods.exists(period => {
      day.getTime.compareTo(period.startDate) >= 0 && day.getTime.compareTo(period.endDate) <= 0
    })
  }

}

object ProductDao extends JsonUtil {

  private val logger = Logger(LoggerFactory.getLogger("ProductDao"))

  def get(storeCode: String, id: Long): Option[Mogobiz.Product] = {
    // Création de la requête
    val req = esearch4s(storeCode -> "product") postFilter termQuery("product.id", id)

    // Lancement de la requête
    EsClient.search[Mogobiz.Product](req)
  }

  def getProductAndSku(indexEs: String, skuId: Long): Option[(Mogobiz.Product, Mogobiz.Sku)] = {
    val result = Try {
      // Création de la requête
      val req = esearch4s(indexEs -> "product") postFilter termQuery("product.skus.id", skuId)

      // Lancement de la requête
      val productOpt = EsClient.search[Mogobiz.Product](req)
      if (productOpt.isDefined) Some((productOpt.get, productOpt.get.skus.find(sku => sku.id == skuId).get))
      else None
    }
    result match {
      case Success(ps) => ps
      case Failure(e) =>
        logger.error("Unabled to load product and sku  " + skuId + " from index " + indexEs, e)
        None
    }
  }

  def getSkusIdByCoupon(storeCode: String, couponId: Long): List[Long] = {
    // Création de la requête
    val req = esearch4s(storeCode -> "product") postFilter termQuery("product.skus.coupons.id", couponId) from 0 size EsClient.MaxSize

    // Lancement de la requête
    val productsList = EsClient.searchAll[Mogobiz.Product](req)

    // Extract des id des Skus
    productsList.toList.flatMap(p => p.skus.filter { sku => sku.coupons != null && sku.coupons.exists(c => c.id == couponId) }.map(sku => sku.id))
  }

}

object SuggestionDao extends JsonUtil {
  def getSuggestionsbyId(storeCode: String, productId: Long): scala.List[Suggestion] = {
    // Création de la requête
    val req = esearch4s(storeCode -> "suggestion") fields("_source", "_parent") postFilter termQuery("productId", productId) from 0 size EsClient.MaxSize

    // Lancement de la requête
    EsClient.searchAll[Mogobiz.Suggestion](req, (hit, fields) => {
      hit.copy(parentId = fields.get("_parent").asInstanceOf[String].toLong)
    }).toList
  }
}
