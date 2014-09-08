package com.mogobiz.es

import java.text.SimpleDateFormat
import java.util.{Calendar, Date, Locale}

import com.mogobiz.es.EsClient._
import com.mogobiz.model
import com.mogobiz.model._
import com.mogobiz.services.RateBoService
import com.mogobiz.utils.JacksonConverter
import com.mogobiz.vo.Paging
import com.sksamuel.elastic4s.ElasticDsl.{search => esearch4s, update => esupdate4s, _}
import com.sksamuel.elastic4s.FilterDefinition
import com.typesafe.scalalogging.slf4j.Logger
import org.elasticsearch.action.get.{GetResponse, MultiGetItemResponse}
import org.elasticsearch.search.SearchHits
import org.elasticsearch.search.sort.SortOrder
import org.json4s.JsonAST.{JArray, JNothing, JObject}
import org.json4s.JsonDSL._
import org.json4s._
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

/**
 *
 * Created by Christophe on 18/02/14.
 */

object ElasticSearchClient {

  private val log = Logger(LoggerFactory.getLogger("ElasticSearchClient"))

  val rateService = RateBoService

  private def historyIndex(store:String):String = {
    s"${store}_history"
  }

  /**
   * Returns the ES index for store preferences user
   * @param store
   * @return
   */
  private def prefsIndex(store:String):String = {
    s"${store}_prefs"
  }

  private def cartIndex(store:String):String = {
    s"${store}_cart"
  }

  private def commentIndex(store:String):String = {
    s"${store}_comment"
  }

  /**
   * Saves the preferences user
   * @param store : store to use
   * @param uuid : uuid of the user
   * @param prefs : preferences of the user
   * @return
   */
  def savePreferences(store: String, uuid: String, prefs: Prefs): Boolean = {
    EsClient.updateRaw(esupdate4s id uuid in s"${prefsIndex(store)}/prefs" docAsUpsert true docAsUpsert{
      "productsNumber" -> prefs.productsNumber
    } retryOnConflict 4)
    true
  }

  def getPreferences(store: String, uuid: String): Prefs = {
    EsClient.load[Prefs](_store=prefsIndex(store), _uuid=uuid) match {
      case Some(s) => s
      case None => Prefs(10)
    }
  }

  /**
   *
   * @param store
   * @param lang
   * @return
   */
  def queryCountries(store: String, lang: String): JValue = {
    EsClient.searchAllRaw(
      esearch4s in store -> "country" sourceExclude(createExcludeLang(store, lang) :+ "imported" :_*)
    ).getHits
  }

  /**
   *
   * @param store
   * @param lang
   * @return
   */
  def queryCurrencies(store: String, lang: String): JValue = {
    EsClient.searchAllRaw(
      esearch4s in store -> "rate" sourceExclude(createExcludeLang(store, lang) :+ "imported" :_*)
    ).getHits
  }

  def getCurrencies(store: String, lang: String): Seq[model.Currency] = {
    EsClient.searchAll[model.Currency](
      esearch4s in store -> "rate" sourceExclude(createExcludeLang(store, lang) :+ "imported" :_*)
    )
  }

  /**
   * Effectue la recheche de brands dans ES
   * @param store code store
   * @param qr parameters
   * @return
   */
  def queryBrands(store: String, qr: BrandRequest): JValue = {
    val req = esearch4s in store -> "product"
    var filters:List[FilterDefinition] = if(!qr.hidden) List(termFilter("category.hide", "false")) else List.empty
    qr.categoryPath match {
      case Some(s) =>
        filters :+= regexFilter("category.path", s".*${s.toLowerCase}.*")
      case None => // nothing to do
    }
    val results : JArray = EsClient.searchAllRaw(filterRequest(req, filters) sourceExclude(createExcludeLang(store, qr.lang) :+ "imported" :_*)).getHits
    results \ "brand"
  }

  /**
   *
   * @param store
   * @param hidden
   * @param inactive
   * @param lang
   * @return
   */
  def queryTags(store: String, hidden:Boolean, inactive:Boolean, lang:String): JValue = {
    EsClient.searchAllRaw(
      esearch4s in store -> "tag" sourceInclude("id", if (lang == "_all") "*.*" else s"$lang.*")
    ).getHits
  }

  /**
   *
   * @param store
   * @return
   */
  def queryStoreLanguages(store: String): JValue = {
    val languages:JValue = EsClient.searchRaw(esearch4s in store -> "i18n" sourceInclude "languages" ) match {
      case Some(s) => s.asInstanceOf[JValue]
      case None => JNothing
    }
    languages  \ "languages"
  }

  def getStoreLanguagesAsList(store: String): List[String] = {
    EsClient.searchRaw(esearch4s in store -> "i18n" sourceInclude "languages" ) match {
      case Some(s) => s.field("languages").value()
      case None => List.empty
    }
  }

  def getAllExcludedLanguagesExcept(store: String, langRequested: String): List[String] = {
    val storeLanguagesList: List[String] = getStoreLanguagesAsList(store)
    if (langRequested.isEmpty) storeLanguagesList
    else storeLanguagesList.filter {
      lang => lang != langRequested
    }
  }

  def getAllExcludedLanguagesExceptAsList(store: String, lang: String): List[String] = {
    getAllExcludedLanguagesExcept(store,lang).flatMap {
      l => l :: "*." + l :: Nil
    }
  }

  def getLangFieldsWithPrefixAsList(store: String, preField: String, field: String): List[String] = {
    getStoreLanguagesAsList(store).flatMap {
      l => preField + l + "." + field :: Nil
    }
  }

  def getIncludedFieldWithPrefixAsList(store: String, preField:  String, field: String, lang: String) : List[String] = {
    if ("_all".equals(lang)) {
      getLangFieldsWithPrefixAsList(store, preField, field)
    } else {
      List(preField + lang + "." + field)
    }
  }

  def getHighlightedFieldsAsList(store: String, field: String, lang: String): List[String] = {
    if ("_all".equals(lang))
      getStoreLanguagesAsList(store).flatMap {
        l => l + "." + field :: Nil
      }
    else List(s"$lang.$field")
  }

  /**
   * retreive categories for given request<br/>
   * if brandId is set, the caller must test distinct results
   * @param store
   * @param qr
   * @return
   */
  def queryCategories(store: String, qr: CategoryRequest): JValue = {
    var filters:List[FilterDefinition] = if(!qr.hidden) List(termFilter("category.hide", "false")) else List.empty
    val req = qr.brandId match {
      case Some(s) =>
        filters +:= termFilter("brand.id", s)
        if(qr.parentId.isDefined) filters +:= termFilter("category.parentId", qr.parentId.get)
        if(qr.categoryPath.isDefined) filters +:= regexFilter("category.path", s".*${qr.categoryPath.get.toLowerCase}.*")
        esearch4s in store -> "product"
      case None =>
        if(qr.parentId.isDefined) filters +:= termFilter("parentId", qr.parentId.get)
        else if(qr.categoryPath.isEmpty) missingFilter("parentId") existence true includeNull true
        if(qr.categoryPath.isDefined) filters +:= regexFilter("path", s".*${qr.categoryPath.get.toLowerCase}.*")
        esearch4s in store -> "category"
    }
    EsClient.searchAllRaw(filterRequest(req, filters) sourceExclude(createExcludeLang(store, qr.lang) :+ "imported" :_*)).getHits
  }


  def queryProductsByCriteria(store: String, req: ProductRequest): JValue = {
    val query = req.name match {
      case Some(s) =>
        esearch4s in store -> "product" query {
          matchQuery("name", s)
        }
      case None => esearch4s in store -> "product"
    }
    val filters:List[FilterDefinition] = (List(
      createTermFilter("code", req.code),
      createTermFilter("xtype", req.xtype),
      createRegexFilter("path", req.categoryPath),
      createTermFilter("brand.id", req.brandId),
      createTermFilter("tags.name", req.tagName),
      createNumericRangeFilter("price", req.priceMin, req.priceMax),
      createRangeFilter("creationDate", req.creationDateMin, None)
    ) ::: createFeaturedRangeFilters(req.featured.getOrElse(false))).flatten
    val fieldsToExclude = getAllExcludedLanguagesExceptAsList(store, req.lang) ::: fieldsToRemoveForProductSearchRendering
    val _size: Int = req.maxItemPerPage.getOrElse(100)
    val _from: Int = req.pageOffset.getOrElse(0) * _size
    val _sort = req.orderBy.getOrElse("name")
    val _sortOrder = req.orderDirection.getOrElse("asc")
    lazy val currency = getCurrencies(store, req.lang).find(_.code == req.currencyCode) getOrElse defaultCurrency
    val response:SearchHits = EsClient.searchAllRaw(
      filterRequest(query, filters)
      sourceExclude(fieldsToExclude :_*)
      from _from
      size _size
      sort {
        by field _sort order SortOrder.valueOf(_sortOrder.toUpperCase)
      }
    )
    val hits:JArray = response.getHits
    val products:JValue = hits.map{
      hit => renderProduct(hit, req.countryCode, req.currencyCode, req.lang, currency, fieldsToRemoveForProductSearchRendering)
    }
    Paging.wrap(response.getTotalHits, products, req)
  }

  private val defaultCurrency = Currency(currencyFractionDigits = 2, rate = 0.01d, name="Euro", code = "EUR") //FIXME trouvez autre chose

  private val fieldsToRemoveForProductSearchRendering = List("skus", "features", "resources", "datePeriods", "intraDayPeriods")

  def renderProduct(product: JsonAST.JValue, country: Option[String], currency: Option[String], lang: String, cur: model.Currency, fieldsToRemove: List[String]): JsonAST.JValue = {
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
      val formatedPrice = rateService.format(price, currency.get, locale, cur.rate)
      val formatedEndPrice = rateService.format(endPrice, currency.get, locale, cur.rate)
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

  private def removeFields(product: JValue, fields: List[String]): JValue = {
    if (fields.isEmpty) product
    else {
      removeFields(product removeField {
        f => f._1 == fields.head
      }, fields.tail)
    }
  }



  private val sdf = new SimpleDateFormat("yyyy-MM-dd") //THH:mm:ssZ
  private val hours = new SimpleDateFormat("HH:mm")

  def queryProductDetails(store: String, params: ProductDetailsRequest, productId: Long, sessionId: String): JValue = {
    /*cookie("mogobiz_uuid") { cookie =>
    val uuid = cookie.content*/
    if (params.historize) {
      addToHistory(store, productId.toLong, sessionId)
    }
    queryProductById(store, productId.toLong, params)
  }

  /**
   * Add the product (id) to the list of the user visited products through its sessionId
   * @param store
   * @param productId
   * @param sessionId
   * @return
   */
  def addToHistory(store:String,productId:Long,sessionId:String) : Boolean = {
    val query = s"""{"script":"ctx._source.productIds.contains(pid) ? (ctx.op = \\"none\\") : ctx._source.productIds += pid","params":{"pid":$productId},"upsert":{"productIds":[$productId]}}"""
    EsClient.updateRaw(esupdate4s id sessionId in s"${historyIndex(store)}/history" script query)
    true
  }

  /**
   * get the product detail
   * @param store
   * @param id
   * @param req
   * @return
   */
  def queryProductById(store: String, id: Long, req: ProductDetailsRequest): Option[JValue] = {
    lazy val currency = getCurrency(store, req.currency, req.lang)
    val product:Option[GetResponse] = EsClient.loadRaw(get id id from store -> "product")
    product match{
      case Some(p) => Some(renderProduct(p, req.country, req.currency, req.lang, currency, List()))
      case None => None
    }
  }

  def getCurrency(store:String,currencyCode:Option[String],lang:String):model.Currency = {
    assert(!store.isEmpty)
    assert(!lang.isEmpty)

    currencyCode match {
      case Some(s) => Try(getCurrencies(store, lang).find(_.code == s) getOrElse defaultCurrency) getOrElse defaultCurrency
      case None =>
        log.warn(s"No currency code supplied: fallback to programmatic default currency: ${defaultCurrency.code}, ${defaultCurrency.rate}")
        defaultCurrency
    }
  }


  /**
   * Get multiple products features
   * @param store
   * @param params
   * @return a list of products
   */
  def getProductsFeatures(store: String, params: CompareProductParameters): JValue = {
    val idList = params.ids.split(",").toList

    val includedFields:List[String] = List("id", "name", "features.name", "features.value") :::
      getIncludedFieldWithPrefixAsList(store, "", "name", params.lang) :::
      getIncludedFieldWithPrefixAsList(store, "features.", "name", params.lang) :::
      getIncludedFieldWithPrefixAsList(store, "features.", "value", params.lang)

    val products:JArray = EsClient.loadRaw(multiget(idList.map(id => get id id from store -> "product" fields(includedFields:_*)):_*))

    var allLanguages: List[String] = getStoreLanguagesAsList(store)

    // Permet de traduire la value ou le name d'une feature
    def translateFeature(feature: JValue, esProperty: String, targetPropery: String): List[JField] = {
      val value = extractJSonProperty(feature, esProperty)

      if (params._lang.equals("_all")) {
        allLanguages.map { lang: String => {
          val v = extractJSonProperty(extractJSonProperty(feature, lang), esProperty)
          if (v == JNothing) JField("-", "-")
          else JField(lang, v)
        }}.filter{v: JField => v._1 != "-"} :+ JField(targetPropery, value)
      }
      else {
        val valueInGivenLang = extractJSonProperty(extractJSonProperty(feature, params._lang), esProperty)
        if (valueInGivenLang == JNothing) List(JField(targetPropery, value))
        else List(JField(targetPropery, valueInGivenLang))
      }
    }

    val featuresByNameAndIds : List[(BigInt, List[(String, JValue, List[JField])])] = for {
      JObject(result) <- products
      JField("id", JInt(id)) <- result
    } yield {
      val featuresByName : List[(String, JValue, List[JField])] = for {
        JField("features", JArray(features)) <- result
        JObject(feature) <- features
        JField("name", JString(name)) <- feature
      } yield (name, JObject(feature), translateFeature(feature, "value", "value"))
      (id, featuresByName)
    }

    // Liste des ids des produits comparés
    val ids: List[BigInt] = featuresByNameAndIds.map { idAndFeaturesByName: (BigInt, List[(String, JValue, List[JField])]) => idAndFeaturesByName._1}.distinct

    // Liste des noms des features (avec traduction des noms des features)
    val featuresName : List[(String, List[JField])] = {for {feature: (String, JValue, List[JField]) <-
                                                            featuresByNameAndIds.map { idAndFeaturesByName: (BigInt, List[(String, JValue, List[JField])]) => idAndFeaturesByName._2}.
                                                              flatMap {featuresByName: List[(String, JValue, List[JField])] => featuresByName}
    } yield (feature._1, translateFeature(feature._2, "name", "label"))}.distinct

    // List de JObject correspond au contenu de la propriété "result" du résultat de la méthode
    // On construit pour chaque nom de feature et pour chaque id de produit
    // la résultat de la comparaison en gérant les traductions et en mettant "-"
    // si une feature n'existe pas pour un produit
    val resultContent: List[JObject] = featuresName.map { featureName: (String, List[JField]) =>

      // Contenu pour la future propriété "values" du résultat
      val valuesList = ids.map { id: BigInt =>
        val featuresByName: Option[(BigInt, List[(String, JValue, List[JField])])] = featuresByNameAndIds.find{ idAndFeaturesByName: (BigInt, List[(String, JValue, List[JField])]) => idAndFeaturesByName._1 == id }
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
      val uniqueValue = valuesList.map {valueObject: JValue =>
        extractJSonProperty(valueObject, "value") match {
          case s: JString => s.s
          case _ => "-"
        }
      }.distinct

      JObject(featureName._2 :+ JField("values", valuesList) :+ JField("indicator", if (uniqueValue.size == 1) "0" else "1"))
    }

    val jFieldIds = JField("ids", JArray(ids.map {id: BigInt => JString(String.valueOf(id))}))
    var jFieldResult = JField("result", JArray(resultContent))
    JObject(List(jFieldIds, jFieldResult))
  }

  private def extractJSonProperty(source: JValue, property: String): JValue = {
    extractJSonProperty(source, property, JNothing)
  }

  private def extractJSonProperty(source: JValue, property: String, defaultValue: JValue): JValue = {
    source match {
      case o: JObject =>
        o.obj.find {p: JField => p._1 == property}.getOrElse(JField(property, defaultValue))._2
      case _ => defaultValue
    }
  }

  /**
   * Fulltext products search
   * @param store
   * @param params
   * @return a list of products
   */
  def queryProductsByFulltextCriteria(store: String, params: FullTextSearchProductParameters): JValue = {
    val fields:List[String] = List("name") ::: getIncludedFieldWithPrefixAsList(store, "", "name", params._lang)
    val includedFields:List[String] = (if(params.highlight) List.empty else List("name")) ::: fields
    val highlightedFields = List("name") ::: getHighlightedFieldsAsList(store, "name", params.lang)
    val req =
      if(params.highlight){
        esearch4s in store types(List("product", "category", "brand"):_*) highlighting{
          highlightedFields.mkString(",")
        }
      }
      else{
        esearch4s in store types(List("product", "category", "brand"):_*)
      }
    val hits:JArray = EsClient.searchAllRaw(req sourceInclude(includedFields:_*)).getHits
    val rawResult =
      if (params.highlight) {
        for {
          JObject(result) <- hits.children.children
          JField("_type", JString(_type)) <- result
          JField("_source", JObject(_source)) <- result
          JField("highlight", JObject(highlight)) <- result
        } yield _type -> (_source ::: highlight)
      } else {
        for {
          JObject(result) <- hits.children.children
          JField("_type", JString(_type)) <- result
          JField("_source", JObject(_source)) <- result
        } yield _type -> _source
      }
    rawResult.groupBy(_._1).map {
      case (_cat, v) => (_cat, v.map(_._2))
    }
  }



  /**
   * Return all the valid date according the specified periods
   * @param store
   * @param id
   * @param req
   * @return
   */
  def queryProductDates(store: String, id: Long, req: ProductDatesRequest): JValue = {
    val filters:List[FilterDefinition] = List(createTermFilter("id", Some(s"$id"))).flatten
    val hits:SearchHits = EsClient.searchAllRaw(
      filterRequest(esearch4s in store -> "product", filters) sourceInclude(List("datePeriods", "intraDayPeriods"):_*)
    )
    val inPeriods:List[IntraDayPeriod] = hits.getHits.map(hit => hit.field("intraDayPeriods").getValue[List[IntraDayPeriod]]).flatten.toList
    val outPeriods:List[EndPeriod] = hits.getHits.map(hit => hit.field("datePeriods").getValue[List[EndPeriod]]).flatten.toList
    //date or today
    val now = Calendar.getInstance().getTime
    val today = getCalendar(sdf.parse(sdf.format(now)))
    var startCalendar = getCalendar(sdf.parse(req.date.getOrElse(sdf.format(now))))
    if (startCalendar.compareTo(today) < 0) {
      startCalendar = today
    }
    val endCalendar = getCalendar(startCalendar.getTime)
    endCalendar.add(Calendar.MONTH, 1)
    def checkDate(currentDate:Calendar,endCalendar: Calendar, acc: List[String]) : List[String] = {
      if(!currentDate.before(endCalendar)) acc
      else{
        currentDate.add(Calendar.DAY_OF_YEAR, 1)
        if (isDateIncluded(inPeriods, currentDate) && !isDateExcluded(outPeriods, currentDate)) {
          val date = sdf.format(currentDate.getTime)
          checkDate(currentDate, endCalendar, date::acc)
        }else{
          checkDate(currentDate, endCalendar, acc)
        }
      }
    }
    implicit def json4sFormats: Formats = DefaultFormats
    checkDate(getCalendar(startCalendar.getTime), endCalendar, List()).reverse
  }

  /**
   * Get all starting times for a date in the valid periods
   * @param store
   * @param id
   * @param req
   * @return
   */
  def queryProductTimes(store:String , id:Long, req: ProductTimesRequest) : JValue = {
    val filters:List[FilterDefinition] = List(createTermFilter("id", Some(s"$id"))).flatten
    val hits:SearchHits = EsClient.searchAllRaw(
      filterRequest(esearch4s in store -> "product", filters) sourceInclude(List("intraDayPeriods"):_*)
    )
    val intraDayPeriods:List[IntraDayPeriod] = hits.getHits.map(hit => hit.field("intraDayPeriods").getValue[List[IntraDayPeriod]]).flatten.toList
    //date or today
    val day = getCalendar(sdf.parse(req.date.getOrElse(sdf.format(Calendar.getInstance().getTime))))
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
    }.map{
      //get the start date hours value only
      period => {
        // the parsed date returned by ES is parsed according to the server Timezone and so it returns 16 (parsed value) instead of 15 (ES value)
        // but what it must return is the value from ES
        hours.format(getFixedDate(period.startDate).getTime)
      }
    }
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
  private def getFixedDate(d: Date):Calendar = {
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

  /**
   * Fix the date according to the timezone
   * @param cal - calendar
   * @return
   */
  private def getFixedDate(cal: Calendar):Calendar = {
    val fixeddate = Calendar.getInstance
    fixeddate.setTime(new Date(cal.getTime.getTime - fixeddate.getTimeZone.getRawOffset))
    fixeddate
  }

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

  /**
   * Query for products given a list of product ids
   *
   * @param store - store
   * @param ids - product ids
   * @param req - product request
   * @return products
   */
  def getProductsByIds(store: String, ids: List[Long], req: ProductDetailsRequest): List[JValue] = {
    lazy val currency = getCurrency(store, req.currency, req.lang)
    val products:List[MultiGetItemResponse] = EsClient.loadRaw(multiget(ids.map(id => get id id from store -> "product"):_*)).toList
    for(product <- products if !product.isFailed) yield {
      val p:JValue = product.getResponse
      renderProduct(p, req.country, req.currency, req.lang, currency, List())
    }
  }

  /**
   * Get the list of products visited by a user (sessionId)
   * @param store - store
   * @param sessionId - user session id
   * @return - products
   */
  def getProductHistory(store:String, sessionId:String) : List[Long] = {
   EsClient.loadRaw(get id sessionId from(historyIndex(store), "history")) match {
     case Some(s) => s.getField("productIds").getValues.asInstanceOf[List[Long]]
     case None => List.empty
   }
  }

  def createComment(store:String,productId:Long,c:CommentRequest): Comment = {
    require(!store.isEmpty)
    require(productId > 0)
    val comment = Try(Comment(None,c.userId,c.surname,c.notation,c.subject,c.comment,c.created,productId))
    comment match {
      case Success(s) =>
        Comment(Some(EsClient.index[Comment](commentIndex(store), s)),c.userId,c.surname,c.notation,c.subject,c.comment,c.created,productId)
      case Failure(f) => throw f
    }
  }

  def updateComment(store:String, productId:Long, commentId:String, useful: Boolean) : Boolean = {
    val query = s"""{"script":"if(useful){ctx._source.useful +=1}else{ctx._source.notuseful +=1}","params":{"useful":$useful}}"""
    val req = esupdate4s id commentId in s"${commentIndex(store)}/comment" script query
    EsClient.updateRaw(req)
    true
  }

  def getComments(store:String, productId:Long , req:CommentGetRequest) : Paging[Comment] = {
    val size = req.maxItemPerPage.getOrElse(100)
    val from = req.pageOffset.getOrElse(0) * size
    val filters:List[FilterDefinition] = List(createTermFilter("productId", Some(s"$productId"))).flatten
    val hits:SearchHits = EsClient.searchAllRaw(
      filterRequest(esearch4s in commentIndex(store) -> "comment", filters)
        from from
        size size
        sort {
          by field "created" order SortOrder.DESC
        }
    )
    val comments:List[Comment] = hits.getHits.map(JacksonConverter.deserializeComment).toList
    Paging.add(hits.getTotalHits.toInt, comments, req)
  }

  private def isDateExcluded(periods:List[EndPeriod] , day:Calendar ) : Boolean  = {
    periods.exists(period => {
      day.getTime.compareTo(period.startDate) >= 0 && day.getTime.compareTo(period.endDate) <= 0
    })
  }

  /**
   * if lang == "_all" returns an empty list
   * else returns the list of all languages except the given language
   * @param store - store
   * @param lang - language
   * @return excluded languages
   */
  private def createExcludeLang(store: String, lang: String): List[String] = {
    if (lang == "_all") List()
    else getStoreLanguagesAsList(store).filter{case l: String => l != lang}.collect { case l:String =>
      "*." + l
    }
  }

  private def filterRequest(req:SearchDefinition, filters:List[FilterDefinition]) : SearchDefinition =
    if(filters.nonEmpty){
      if(filters.size > 1)
        req filter {
          and (filters:_*)
        }
      else
        req filter{
          filters(0)
        }
    }
    else req

  private def createTermFilter(field:String, value:Option[Any]) : Option[FilterDefinition] = {
    value match{
      case Some(s) => Some(termFilter(field, s))
      case None => None
    }
  }

  private def createRegexFilter(field:String, value:Option[String]) : Option[FilterDefinition] = {
    value match{
      case Some(s) => Some(regexFilter(field, s".*${s.toLowerCase}.*"))
      case None => None
    }
  }

  private def createRangeFilter(field:String, _gte:Option[String], _lte: Option[String]) : Option[FilterDefinition] = {
    val req = _gte match{
      case Some(s) => Some(rangeFilter(field) gte s)
      case None => None
    }
    _lte match {
      case Some(s) => if(req.isDefined) Some(req.get lte s) else Some(rangeFilter(field) lte s)
      case None => None
    }
  }

  private def createNumericRangeFilter(field:String, _gte:Option[Long], _lte: Option[Long]) : Option[FilterDefinition] = {
    val req = _gte match{
      case Some(s) => Some(numericRangeFilter(field) gte s)
      case None => None
    }
    _lte match {
      case Some(s) => if(req.isDefined) Some(req.get lte s) else Some(numericRangeFilter(field) lte s)
      case None => None
    }
  }

  private def createFeaturedRangeFilters(featured:Boolean) : List[Option[FilterDefinition]] = {
    if(featured){
      val today = sdf.format(Calendar.getInstance().getTime)
      List(
        createRangeFilter("startFeatureDate", None, Some(s"$today")),
        createRangeFilter("stopFeatureDate", Some(s"$today"), None)
      )
    }
    List.empty
  }

}
