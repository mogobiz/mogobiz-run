package com.mogobiz

import akka.actor.ActorSystem
import akka.io.IO
import akka.pattern.ask
import com.mogobiz.config.Settings
import com.mogobiz.es.EsClient
import EsClient._
import com.sksamuel.elastic4s.ElasticDsl.{search => search4s, _}
import com.typesafe.scalalogging.slf4j.Logger
import org.slf4j.LoggerFactory
import scala.concurrent._
import scala.concurrent.duration._
import spray.can.Http
import spray.client.pipelining._
import spray.util._
import org.json4s.native.JsonMethods._
import org.json4s._
import org.json4s.JsonDSL._
import java.util._
import java.text.{SimpleDateFormat, NumberFormat}
import scala.List
import com.mogobiz.vo.{Paging}
import org.json4s.JsonAST.{JObject, JNothing}
import scala.util.{Failure, Try}
import spray.http.HttpResponse
import com.mogobiz.vo.Comment
import scala.util.Success
import spray.http.HttpRequest
import com.mogobiz.vo.CommentRequest
import org.json4s.JsonAST.JArray
import com.mogobiz.vo.CommentGetRequest

/**
 * Created by Christophe on 18/02/14.
 */

object ElasticSearchClient /*extends Actor*/ {

  implicit val system = ActorSystem("es-client")

  import system.dispatcher

  import Settings.DB._

  // execution context for futures

  //  def queryRoot(): Future[HttpResponse] = pipeline(Get(route("/")))
  //  override def receive: Actor.Receive = execute

  //private val logger = Logging(system,this)
  private val log = Logger(LoggerFactory.getLogger("ElasticSearchClient"))

  val rateService = RateBoService

  val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
  /*
  val pipeline: Future[SendReceive] =
    for (
      Http.HostConnectorInfo(connector, _) <-
      IO(Http) ? Http.HostConnectorSetup(EsHost, port = EsHttpPort)
    ) yield sendReceive(connector)
*/

  private val EsFullURL = s"$EsHost:$EsHttpPort"

  private def route(url: String): String = EsFullURL + url

  private def historyIndex(store:String):String = {
    return s"${store}_history"
  }

  /**
   * Returns the ES index for store preferences user
   * @param store
   * @return
   */
  private def prefsIndex(store:String):String = {
    return s"${store}_prefs"
  }

  private def cartIndex(store:String):String = {
    return s"${store}_cart"
  }

  private def commentIndex(store:String):String = {
    return s"${store}_comment"
  }

  /**
   * Saves the preferences user
   * @param store : store to use
   * @param uuid : uuid of the user
   * @param prefs : preferences of the user
   * @return
   */
  def savePreferences(store: String, uuid: String, prefs: Prefs): Future[Boolean] = {
    val query = s"""
            | {
            |   "productsNumber": ${prefs.productsNumber}
            | }
            """.stripMargin

    val response: Future[HttpResponse] = pipeline(Put(route("/" + prefsIndex(store) + "/prefs/" + uuid), query))
    response.flatMap { response => {
      Future{response.status.isSuccess}
    }
    }
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
      search4s in store -> "country" sourceExclude(createExcludeLang(store, lang) :+ "imported" :_*)
    )
  }

  /**
   *
   * @param store
   * @param lang
   * @return
   */
  def queryCurrencies(store: String, lang: String): Future[JValue] = {
    val esRequest = createESRequest(createExcludeLang(store, lang) :+ "imported")
    val response: Future[HttpResponse]  = search(store, "rate", esRequest)
    response.flatMap {
      response => {
        if (response.status.isSuccess) {
          val json = parse(response.entity.asString)
          val subset = json \ "hits" \ "hits" \ "_source"
          //println("subset="+subset)
          //println("subset.children="+subset.children)
          //Future{subset.children}

    Future{
            subset match{
              case JObject(o) => List(subset)
              case _ => subset
            }
          }

        }else{
          println("WARNING: rates not found => returning empty list")
          Future{List()}
        }
      }
    }
  }

  def getCurrencies(store: String, lang: String): Seq[Currency] = {
    EsClient.searchAll[Currency](
      select in store types "rate" query createESRequest(createExcludeLang(store, lang) :+ "imported")
    )
  }

  /**
   * Effectue la recheche de brands dans ES
   * @param store code store
   * @param qr parameters
   * @return
   */
  def queryBrands(store: String, qr: BrandRequest): Future[JValue] = {
    if (qr.categoryPath.isDefined) {
      val exclude = createExcludeLang(store, qr.lang) :+ "imported"

      val hideFilter = if (!qr.hidden) createTermFilterWithStr("category.hide", Option("false")) else ""
      val categoryPathFilter = createExpRegFilter("category.path", qr.categoryPath)

      val query = createESRequest(exclude, createFilter(createAndFilter(List(hideFilter, categoryPathFilter))))
      val response = search(store, "product", query)

      response.flatMap {
        resp => {
          if (resp.status.isSuccess) {
            val json = parse(resp.entity.asString)
            val subset = json \ "hits" \ "hits" \ "_source" \ "brand"
            val result = subset match {
              case JNothing => JArray(List())
              case o:JObject => JArray(List(o))
              case a:JArray => JArray(a.children.distinct)
              case _ => subset
            }
            Future{result}
          } else {
            //TODO log l'erreur
            Future{parse(resp.entity.asString)}
            //throw new ElasticSearchClientException(resp.status.reason)
          }
        }
      }
    }
    else {
      val exclude = createExcludeLang(store, qr.lang) :+ "imported"

      val hideFilter = if (!qr.hidden) createTermFilterWithStr("hide", Option("false")) else ""

      val query = createESRequest(exclude, createFilter(hideFilter))
      val response = search(store, "brand", query)

      response.flatMap {
        resp => {
          if (resp.status.isSuccess) {
            val json = parse(resp.entity.asString)
            val subset = json \ "hits" \ "hits" \ "_source"
            Future{subset}
          } else {
            //TODO log l'erreur
            Future{parse(resp.entity.asString)}
            //throw new ElasticSearchClientException(resp.status.reason)
          }
        }
      }
    }
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
      search4s in store -> "tag" sourceInclude("id", if (lang == "_all") "*.*" else s"$lang.*")
    )
  }

  /**
   *
   * @param store
   * @return
   */
  def queryStoreLanguages(store: String): JValue = {
    val languages:JValue = EsClient.searchRaw(search4s in store -> "i18n" sourceInclude "languages" ) match {
      case Some(s) => s.asInstanceOf[JValue]
      case None => JNothing
    }
    languages  \ "languages"
  }

  def getStoreLanguagesAsList(store: String): List[String] = {
    EsClient.searchRaw(search4s in store -> "i18n" sourceInclude "languages" ) match {
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

  def getAllExcludedLanguagesExceptAsList(store: String,lang: String): List[String] = {
    val langs = getAllExcludedLanguagesExcept(store,lang)
    val langsTokens = langs.flatMap {
      l => l :: "*." + l :: Nil
    }

    langsTokens
  }

  def getAllExcludedLanguagesExceptAsString(store: String,lang: String): String = {
    val langs = getAllExcludedLanguagesExcept(store,lang)
    val langsTokens = langs.flatMap {
      l => l :: "*." + l :: Nil
    }

    langsTokens.mkString("\"", "\",\"", "\"")
  }

  def getLangFieldsWithPrefix(store: String,preField: String, field: String): String = {
    val langs = getStoreLanguagesAsList(store)
    val langsTokens = langs.flatMap {
      l => preField + l + "." + field :: Nil
    }
    langsTokens.mkString("\"", "\", \"", "\"")
  }

  def getIncludedFieldWithPrefix(store: String, preField:  String, field: String, lang: String) : String = {
    {
      if ("_all".equals(lang)) {
        getLangFieldsWithPrefix(store, preField, field)
      } else {
        "\"" + preField + lang + "." + field + "\""
      }
    }
  }

  def getIncludedFields(store: String, field: String, lang: String) : String = {
    getIncludedFieldWithPrefix(store ,"",field,lang)
  }

  def getHighlightedFields(store: String, field: String): String = {
    val langs = getStoreLanguagesAsList(store)
    val langsTokens = langs.flatMap {
      l => l + "." + field :: Nil
    }

    langsTokens.mkString("\"", "\": {}, \"", "\": {}")
  }

  /**
   * retreive categories for given request<br/>
   * if brandId is set, the caller must test distinct results
   * @param store
   * @param qr
   * @return
   */
  def queryCategories(store: String, qr: CategoryRequest): Future[HttpResponse] = {
    if (qr.brandId.isDefined) {
      val exclude = createExcludeLang(store, qr.lang) :+ "imported"

      val brandIdFilter = createTermFilterWithStr("brand.id", qr.brandId)
      val hideFilter = if (!qr.hidden) createTermFilterWithStr("category.hide", Option("false")) else ""
      val parentIdFilter = createTermFilterWithStr("category.parentId", qr.parentId)
      val categoryPathFilter = createExpRegFilter("category.path", qr.categoryPath)

      val query = createESRequest(exclude, createFilter(createAndFilter(List(brandIdFilter, hideFilter, parentIdFilter, categoryPathFilter))))
      search(store, "product", query)
    }
    else {
      val exclude = createExcludeLang(store, qr.lang) :+ "imported"

      val hideFilter = if (!qr.hidden) createTermFilterWithStr("hide", Option("false")) else ""
      val parentIdFilter = if (qr.parentId.isDefined) createTermFilterWithStr("parentId", qr.parentId)
      else if (!qr.categoryPath.isDefined) createMissingFilter("parentId", true, true)
      else ""
      val categoryPathFilter = createExpRegFilter("path", qr.categoryPath)

      val query = createESRequest(exclude, createFilter(createAndFilter(List(hideFilter, parentIdFilter, categoryPathFilter))))
      search(store, "category", query)
    }
  }


  def queryProductsByCriteria(store: String, req: ProductRequest): Future[JValue] = {

    val fieldsToExclude = getAllExcludedLanguagesExceptAsList(store, req.lang) ::: fieldsToRemoveForProductSearchRendering
    //TODO propose a way to request include / exclude fields         "include":["id","price","taxRate"],

    val nameCriteria = createMatchQueryFilter("name", req.name)


    val codeFilter = createTermFilterWithStr("code", req.code)
    val xtypeFilter = createTermFilterWithStr("xtype", req.xtype)
    val categoryPathFilter = createExpRegFilter("path", req.categoryPath)
    val brandFilter = createTermFilterWithNum("brand.id", req.brandId)
    val tagsFilter = createTermFilterWithStr("tags.name", req.tagName)
    val priceFilter = createRangeFilter("price", req.priceMin, req.priceMax)
    val creationDateMinFilter = createRangeFilterWithString("creationDate", req.creationDateMin, None)

    val featuredFilter = createFeaturedFilter(req.featured.getOrElse(false))

    val filters = List(codeFilter, xtypeFilter, categoryPathFilter, brandFilter, tagsFilter, priceFilter, featuredFilter, creationDateMinFilter)

    val size: Int = req.maxItemPerPage.getOrElse(100)
    val from: Int = req.pageOffset.getOrElse(0) * size
    val sort = req.orderBy.getOrElse("name")
    val sortOrder = req.orderDirection.getOrElse("asc")

    val query = createESRequest(fieldsToExclude, nameCriteria, filters, Option(from), Option(size), Option(sort), Option(sortOrder))

    val fresponse: Future[HttpResponse] = search(store, "product", query)
    fresponse.flatMap {
      response => {
        if (response.status.isSuccess) {

          val json = parse(response.entity.asString)
          val subset = json \ "hits" \ "hits" \ "_source"
          val currency = getCurrencies(store, req.lang).find(_.code == req.currencyCode) getOrElse defaultCurrency
          val products: JValue = (subset match {
            case arr: JArray => arr.children
            case obj: JObject => List(obj)
            case _ => List()
          }).map {
            p => renderProduct(p, req.countryCode, req.currencyCode, req.lang, currency, fieldsToRemoveForProductSearchRendering)
          }

//          println("+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++")
//          println(pretty(render(products)))

          val res = Paging.wrap(json,products,req)

          Future{
            res
          }
        } else {
          //TODO log l'erreur
          Future{
            parse(response.entity.asString)
          }
          //throw new ElasticSearchClientException(resp.status.reason)
        }
      }
    }
  }

  private val defaultCurrency = Currency(currencyFractionDigits = 2, rate = 0.01d, name="Euro", code = "EUR") //FIXME trouvez autre chose

  private val fieldsToRemoveForProductSearchRendering = List("skus", "features", "resources", "datePeriods", "intraDayPeriods")

  def renderProduct(product: JsonAST.JValue, country: Option[String], currency: Option[String], lang: String, cur: Currency, fieldsToRemove: List[String]): JsonAST.JValue = {
    if (country.isEmpty || currency.isEmpty) {
      product
    } else {
      implicit def json4sFormats: Formats = DefaultFormats

      val jprice = (product \ "price")
      /*
      println(jprice)
      val price = jprice match {
        case JInt(v) =>  v.longValue()
        case JLong(v) => v
      }*/
      val price = jprice.extract[Int].toLong //FIXME ???

      val lrt = for {
        localTaxRate@JObject(x) <- product \ "taxRate" \ "localTaxRates"
        if (x contains JField("country", JString(country.get.toUpperCase()))) //WARNING toUpperCase ??
        JField("rate", value) <- x} yield value

      val taxRate = lrt.headOption match {
        case Some(JDouble(x)) => x
        case Some(JInt(x)) => x.toDouble
        //      case Some(JNothing) => 0.toDouble
        case _ => 0.toDouble
      }

      val locale = new Locale(lang);
      val endPrice = price + (price * taxRate / 100d)
      val formatedPrice = rateService.format(price, currency.get, locale, cur.rate)
      val formatedEndPrice = rateService.format(endPrice, currency.get, locale, cur.rate)
      val additionalFields = (
        ("localTaxRate" -> taxRate) ~
          ("endPrice" -> endPrice) ~
          ("formatedPrice" -> formatedPrice) ~
          ("formatedEndPrice" -> formatedEndPrice)
        )

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

  private def createFeaturedFilter(doFilter: Boolean): String = {
    if (!doFilter) ""
    else {
      val today = sdf.format(Calendar.getInstance().getTime())
      s"""
       {"range": {"startFeatureDate": {"lte": "$today"}}},{"range": {"stopFeatureDate": { "gte": "$today"}}}
       """.stripMargin
    }
  }


  private def createRangeFilter(field: String, gte: Option[Long], lte: Option[Long]): String = {

    if (gte.isEmpty && lte.isEmpty) ""
    else {
      val gteStr = if (gte.isEmpty) ""
      else {
        val gteVal = gte.get
        s""" "gte":$gteVal """.stripMargin
      }

      val lteStr = if (lte.isEmpty) ""
      else {
        val lteVal = lte.get
        s""" "lte":$lteVal """.stripMargin
      }

      val range = (gteStr :: lteStr :: Nil).filter {
        str => !str.isEmpty
      }
      if (range.isEmpty) ""
      val ranges = range.mkString(",")
      s"""{
        "range": {
          "$field": { $ranges }
        }
      }""".stripMargin
    }
  }

  def queryProductDetails(store: String, params: ProductDetailsRequest, productId: Long, sessionId: String): Future[JValue] = {

    /*cookie("mogobiz_uuid") { cookie =>
    val uuid = cookie.content*/
    if (params.historize) {
      val f = addToHistory(store, productId.toLong, sessionId)
      f onComplete {
        case Success(res) => if (res) println("addToHistory ok") else println("addToHistory failed")
        case Failure(t) => {
          println("addToHistory future failure")
          t.printStackTrace()
        }
      }
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
  def addToHistory(store:String,productId:Long,sessionId:String) : Future[Boolean] = {

    val query = s"""{"script":"ctx._source.productIds.contains(pid) ? (ctx.op = \\"none\\") : ctx._source.productIds += pid","params":{"pid":$productId},"upsert":{"productIds":[$productId]}}"""

    val fresponse: Future[HttpResponse] = pipeline(Post(route("/" + historyIndex(store) + "/history/"+sessionId+"/_update"), query))

    fresponse.flatMap {
      response => {
        if (response.status.isSuccess) {
          Future{true}
        } else {
          //TODO log l'erreur
          //println(response.entity.asString)
          Future{false}
        }
      }
    }
  }

  /**
   * get the product detail
   * @param store
   * @param id
   * @param req
   * @return
   */
  def queryProductById(store: String, id: Long, req: ProductDetailsRequest): Future[JValue] = {

    val fresponse: Future[HttpResponse] = pipeline(Get(route("/" + store + "/product/" + id)))
    fresponse.flatMap {
      response => {
        if (response.status.isSuccess) {

          val json = parse(response.entity.asString)
          val subset = json \ "_source"

          val currency = getCurrency(store,req.currency,req.lang)
          val product = renderProduct(subset, req.country, req.currency, req.lang, currency, List())

          Future{product}
        } else {
          //TODO log l'erreur
          Future{parse(response.entity.asString)}
          //throw new ElasticSearchClientException(resp.status.reason)
        }
      }
    }
  }

  def getCurrency(store:String,currencyCode:Option[String],lang:String):Currency = {
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
  def getProductsFeatures(store: String, params: CompareProductParameters): Future[JValue] = {
    var allLangues: List[String] = getStoreLanguagesAsList(store)
    val idList = params.ids.split(",").toList

    def getFetchConfig(id: String, lang: String): String = {
      val nameFields = getIncludedFields(store,"name", lang)
      val featuresNameField = getIncludedFieldWithPrefix(store, "features.", "name", lang)
      val featuresValueField = getIncludedFieldWithPrefix(store, "features.", "value", lang)
      s"""
        {
          "_type": "product",
          "_id": "$id",
          "_source": {
            "include": [
              "id",
              "name",
              $nameFields,
              "features.name",
              "features.value",
              $featuresNameField,
              $featuresValueField
            ]
          }
        }
        """.stripMargin
    }

    val fetchConfigs = idList.map(id => getFetchConfig(id, params._lang)).filter {
      str => !str.isEmpty
    }.mkString("[", ",", "]")

    val multipleGetQueryTemplate = s"""
      {
        "docs": $fetchConfigs
      }
      """.stripMargin

    //println(multipleGetQueryTemplate)

    // Permet de traduire la value ou le name d'une feature
    def translateFeature(feature: JValue, esProperty: String, targetPropery: String): List[JField] = {
      val value = extractJSonProperty(feature, esProperty)

      if (params._lang.equals("_all")) {
        allLangues.map { lang: String => {
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

    /**
     * Fonction internet qui transforme le résultat d'ES dans le résultat de la méthode de comparaison
     * @param response
     * @param params
     * @return
     */
    def httpResponseToFuture(response: HttpResponse, params: CompareProductParameters): Future[JValue] = {
      implicit def formats = DefaultFormats

      if (response.status.isSuccess) {
        val json = parse(response.entity.asString)
        val docsArray = (json \ "docs")


        val featuresByNameAndIds : List[(BigInt, List[(String, JValue, List[JField])])] = for {
          JObject(result) <- (docsArray \ "_source")
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
        Future{JObject(List(jFieldIds, jFieldResult))}
      } else {
        //TODO log l'erreur
        Future{parse(response.entity.asString)}
        //throw new ElasticSearchClientException(resp.status.reason)
      }
    }

    val fresponse: Future[HttpResponse] = pipeline(Post(route("/" + store + "/_mget"), multipleGetQueryTemplate))
    fresponse.flatMap (response => httpResponseToFuture(response,params))
  }

  private def extractJSonProperty(source: JValue, property: String): JValue = {
    extractJSonProperty(source, property, JNothing)
  }

  private def extractJSonProperty(source: JValue, property: String, defaultValue: JValue): JValue = {
    source match {
      case o: JObject => {
        o.obj.find {p: JField => p._1 == property}.getOrElse(JField(property, defaultValue))._2
      }
      case _ => defaultValue
    }
  }

  /**
   * Fulltext products search
   * @param store
   * @param params
   * @return a list of products
   */
  def queryProductsByFulltextCriteria(store: String, params: FullTextSearchProductParameters): Future[JValue] = {

    val fields: String = getIncludedFields(store, "name", params._lang)
    val includedFields = if (params.highlight) {
      ""
    }
    else {
      ", \"name\", " + fields
    }

    val source = s"""
        "_source": {
          "include": [
           "id"$includedFields
          ]
        }
      """.stripMargin

    val textQuery = {
      val text = params.query
      val included = fields
      s"""
       "query": {
          "query_string": {
            "fields": [
              "name",
              $included
            ],
            "query": "$text"
          }
       }
      """.stripMargin
    }

    val highlightedFields = if (params.highlight) if ("_all".equals(params.lang)) {
      getHighlightedFields(store, "name")
    } else {
      "\"" + params.lang + ".name\" : {}"
    }

    val highlightConf = if (params.highlight) {
      s"""
        "highlight": {
          "fields": {
            "name": {},
            $highlightedFields
          }
        }
      """.stripMargin
    } else ""

    val query = List(source, textQuery, highlightConf).filter {
      str => !str.isEmpty
    }.mkString("{", ",", "}")

    def httpResponseToFuture(response: HttpResponse, withHightLight: Boolean): Future[JValue] = {
      if (response.status.isSuccess) {

        val json = parse(response.entity.asString)
        val subset = json \ "hits" \ "hits"

        val rawResult = if (withHightLight) {
          for {
            JObject(result) <- subset.children.children
            JField("_type", JString(_type)) <- result
            JField("_source", JObject(_source)) <- result
            JField("highlight", JObject(highlight)) <- result
          } yield (_type -> (_source ::: highlight))
        } else {
          for {
            JObject(result) <- subset.children.children
            JField("_type", JString(_type)) <- result
            JField("_source", JObject(_source)) <- result
          } yield (_type -> _source)
        }

        val result = rawResult.groupBy(_._1).map {
          case (_cat, v) => (_cat, v.map(_._2))
        }
        //println(compact(render(result)))
        Future{result}

      } else {
        //TODO log l'erreur
        Future{parse(response.entity.asString)}
        //throw new ElasticSearchClientException(resp.status.reason)
      }
    }

    //println(query)
    val fresponse: Future[HttpResponse] = pipeline(Post(route("/" + store + "/product,category,brand/_search"), query))
    fresponse.flatMap(response => httpResponseToFuture(response, params.highlight))

  }



  /**
   * Return all the valid date according the specified periods
   * @param store
   * @param id
   * @param req
   * @return
   */
  def queryProductDates(store: String, id: Long, req: ProductDatesRequest): Future[JValue] = {
    implicit def json4sFormats: Formats = DefaultFormats

    val query = s"""{"_source": {"include": ["datePeriods","intraDayPeriods"]},
      "query": {"filtered": {"filter": {"term": {"id": $id}}}}}"""


    val fresponse: Future[HttpResponse] = pipeline(Post(route("/" + store + "/product/_search"), query))
    fresponse.flatMap {
      response => {
        if (response.status.isSuccess) {

          val json = parse(response.entity.asString)
          val subset = json \ "hits" \ "hits" \ "_source"


          val datePeriods = subset \ "datePeriods"
          val outPeriods = datePeriods.extract[List[EndPeriod]]

          /*match {
            case JNothing => Nil
            case o:JValue =>
          }*/

          val intraDayPeriods = subset \ "intraDayPeriods"
          //TODO test exist or send empty
          //println(pretty(render(intraDayPeriods)))
          val inPeriods = intraDayPeriods.extract[List[IntraDayPeriod]]

          //date or today
          val now = Calendar.getInstance().getTime
          val today = getCalendar(sdf.parse(sdf.format(now)))
          val d = sdf.parse(req.date.getOrElse(sdf.format(now)))
          var startCalendar = getCalendar(d)


          if (startCalendar.compareTo(today) < 0) {
            startCalendar = today
          }

          val endCalendar = getCalendar(startCalendar.getTime())
          endCalendar.add(Calendar.MONTH, 1)

          def checkDate(currentDate:Calendar,endCalendar: Calendar, acc: List[String]) : List[String] = {
            //println("checkDateAcc acc="+acc)
            if(!currentDate.before(endCalendar)) acc
            else{
              currentDate.add(Calendar.DAY_OF_YEAR, 1)

              if (isDateIncluded(inPeriods, currentDate) && !isDateExcluded(outPeriods, currentDate)) {
                val date = sdf.format(currentDate.getTime)
                //println(date)
                checkDate(currentDate,endCalendar,date::acc)
              }else{
                checkDate(currentDate,endCalendar,acc)
              }
            }
          }
          val currentDate = getCalendar(startCalendar.getTime())
          val dates = checkDate(currentDate,endCalendar,List())

          Future{dates.reverse}
        } else {
          //TODO log l'erreur
          Future{parse(response.entity.asString)}
          //throw new ElasticSearchClientException(resp.status.reason)
        }
      }
    }
  }

  /**
   * Get all starting times for a date in the valid periods
   * @param store
   * @param id
   * @param req
   * @return
   */
  def queryProductTimes(store:String , id:Long, req: ProductTimesRequest) : Future[JValue]={

    implicit def json4sFormats: Formats = DefaultFormats

    val query = s"""{"_source": {"include": ["datePeriods","intraDayPeriods"]},
      "query": {"filtered": {"filter": {"term": {"id": $id}}}}}"""


    val fresponse: Future[HttpResponse] = pipeline(Post(route("/" + store + "/product/_search"), query))
    fresponse.flatMap {
      response => {
        if (response.status.isSuccess) {

          val json = parse(response.entity.asString)
          val subset = json \ "hits" \ "hits" \ "_source"

          val intraDayPeriods = subset \ "intraDayPeriods"
          //TODO test exist or send empty
          //println(pretty(render(intraDayPeriods)))
          val inPeriods = intraDayPeriods.extract[List[IntraDayPeriod]]
          //date or today
          val now = Calendar.getInstance().getTime
          val today = getCalendar(sdf.parse(sdf.format(now)))
          val d = sdf.parse(req.date.getOrElse(sdf.format(now)))
          val dateToEval = getCalendar(d)

          val day = dateToEval
          //TODO refacto this part with the one is in isIncluded method
          val startingHours = inPeriods.filter {
            //get the matching periods
            period => {
              val dow = day.get(Calendar.DAY_OF_WEEK)
              //println("dow="+dow)
              //french definication of day of week where MONDAY is = 1
              //val fr_dow = if (dow == 1) 7 else dow - 1
              //TODO rework weekday definition !!!
              //println("fr_dow=" + fr_dow)
              val included = dow match {
                case Calendar.MONDAY => period.weekday1
                case Calendar.TUESDAY => period.weekday2
                case Calendar.WEDNESDAY => period.weekday3
                case Calendar.THURSDAY => period.weekday4
                case Calendar.FRIDAY => period.weekday5
                case Calendar.SATURDAY => period.weekday6
                case Calendar.SUNDAY => period.weekday7
              }

              //println("included?=" + included)
              val cond = (included &&
                day.getTime().compareTo(period.startDate) >= 0 &&
                day.getTime().compareTo(period.endDate) <= 0)
              //println("cond=" + cond)
              cond
            }
          }.map{
            //get the start date hours value only
            period => {
              // the parsed date returned by ES is parsed according to the serveur Timezone and so it returns 16 (parsed value) instead of 15 (ES value)
              // because the my current timezone is GMT+1
              // but what it must return is the value from ES
              hours.format(getFixedDate(period.startDate).getTime)
            }
          }

          Future{startingHours}
        } else {
          //TODO log l'erreur
          Future{parse(response.entity.asString)}
          //throw new ElasticSearchClientException(resp.status.reason)
        }
      }
    }
  }

  private def getCalendar(d: Date): Calendar = {
    val cal = Calendar.getInstance();
    cal.setTime(d);
    cal;
  }

  /**
   * Fix the date according to the timezone
   * @param d
   * @return
   */
  private def getFixedDate(d: Date):Calendar = {
    val fixeddate = Calendar.getInstance();
    fixeddate.setTime(new Date(d.getTime - fixeddate.getTimeZone.getRawOffset))
    fixeddate
  }

  /*http://tutorials.jenkov.com/java-date-time/java-util-timezone.html
  *http://stackoverflow.com/questions/19330564/scala-how-to-customize-date-format-using-simpledateformat-using-json4s
  *
  */
  /**
   * Fix the date according to the timezone
   * @param cal
   * @return
   */
  private def getFixedDate(cal: Calendar):Calendar = {
    val fixeddate = Calendar.getInstance();
    fixeddate.setTime(new Date(cal.getTime.getTime - fixeddate.getTimeZone.getRawOffset))
    fixeddate
  }

  private def isDateIncluded(periods: List[IntraDayPeriod], day: Calendar): Boolean = {
    //    println("isDateIncluded date : " + sdf.format(day.getTime))

    periods.find {
      period => {
        val dow = day.get(Calendar.DAY_OF_WEEK)
        //french definication of day of week where MONDAY is = 1
        //val fr_dow = if (dow == 1) 7 else dow - 1
        //TODO rework weekday definition !!!
        //println("fr_dow=" + fr_dow)
        val included = dow match {
          case Calendar.MONDAY => period.weekday1
          case Calendar.TUESDAY => period.weekday2
          case Calendar.WEDNESDAY => period.weekday3
          case Calendar.THURSDAY => period.weekday4
          case Calendar.FRIDAY => period.weekday5
          case Calendar.SATURDAY => period.weekday6
          case Calendar.SUNDAY => period.weekday7
        }

        //println("included?=" + included)
        val cond = (included &&
          day.getTime().compareTo(period.startDate) >= 0 &&
          day.getTime().compareTo(period.endDate) <= 0)
        //println("cond=" + cond)
        cond
      }
    }.isDefined

  }



  /**
   * Query for products in paralle according to a list of product ids
   *
   * @param store
   * @param ids
   * @param req
   * @return
   */
  def getProductsByIds(store: String, ids: List[Long], req: ProductDetailsRequest): Future[List[JValue]] = {
    implicit def json4sFormats: Formats = DefaultFormats

    //TODO replace with _mget op http://www.elasticsearch.org/guide/en/elasticsearch/guide/current/_retrieving_multiple_documents.html

    val fproducts:List[Future[JValue]] = for{
      id <- ids
    } yield queryProductById(store, id, req)

    //TODO to replace by RxScala Iterable
    val f = Future.sequence(fproducts.toList)

    //TODO try with a for-compr
    f.flatMap {
      list => {
        val validResponses = list.filter {
          json => {
            (json \ "found") match {
              case JBool(res) => res
              case _ => true
            }
          }
        }
        /*
        val jproducts = validResponses.map{
          json => (json \ "_source")
        }
        jproducts
        */

        Future{validResponses}
      }
    }

  }

  /**
   * Get the list of products visited by a user (sessionId)
   * @param store
   * @param sessionId
   * @return
   */
  def getProductHistory(store:String, sessionId:String) : Future[List[Long]] = {
    implicit def json4sFormats: Formats = DefaultFormats

    val fresponse: Future[HttpResponse] = pipeline(Get(route("/" + historyIndex(store) + "/history/"+sessionId)))
    fresponse.flatMap {
      response => {
        if (response.status.isSuccess) {
          val json = parse(response.entity.asString)
          val subset = json \ "_source"
          val productIds = subset \ "productIds"
          Future{productIds.extract[List[Long]]}
        } else {
          //no sessionId available, EmptyList products returned
          Future{List()}
        }
      }
    }
  }

  private def isDateExcluded(periods:List[EndPeriod] , day:Calendar ) : Boolean  = {
    periods.find {
      period => {
        day.getTime().compareTo(period.startDate) >= 0 && day.getTime().compareTo(period.endDate) <= 0
      }
    }.isDefined
  }


  //TODO private def translate(json:JValue):JValue = { }


  def createComment(store:String,productId:Long,c:CommentRequest): Future[Comment] = {
    require(!store.isEmpty)
    require(productId>0)

    //TODO no better solution than this (try/catch) ????
    try{
      c.validate()

      import org.json4s.native.Serialization.write
      implicit def json4sFormats: Formats = DefaultFormats + FieldSerializer[Comment]()

      val comment = Comment(None,c.userId,c.surname,c.notation,c.subject,c.comment,c.created,productId)
      val jsoncomment = write(comment)
      val fresponse: Future[HttpResponse] = pipeline(Post(route("/" + commentIndex(store) + "/comment"),jsoncomment))

      fresponse onFailure { // TODO a revoir, car la route Spray ne recoit pas cette failure
        case e => {println("fresponse failed:"+e.getMessage);Future.failed(new CommentException(CommentException.UNEXPECTED_ERROR,e.getMessage))}
      }
      fresponse.flatMap {
        response => {
          //println(response.entity.asString)
          if (response.status.isSuccess) {
            val json = parse(response.entity.asString)
            val id = (json \"_id").extract[String]

            Future{Comment(Some(id),c.userId,c.surname,c.notation,c.subject,c.comment,c.created,productId)}
          } else {
            //TODO log l'erreur
            //("new ElasticSearchClientException createComment error")
            Future.failed(new ElasticSearchClientException("createComment error"))
          }
        }
      }
    } catch {
      case e:Throwable => Future.failed(e)
    }
  }

  def updateComment(store:String, productId:Long,commentId:String,useful : Boolean) : Future[Boolean] = {

    val query = s"""{"script":"if(useful){ctx._source.useful +=1}else{ctx._source.notuseful +=1}","params":{"useful":$useful}}"""

    val fresponse: Future[HttpResponse] = pipeline(Post(route("/" + commentIndex(store) + "/comment/"+commentId+"/_update?retry_on_conflict=5"),query))

    fresponse.flatMap {
      response => {
        //println(response.entity.asString)
        if (response.status.isSuccess) {
          Future{useful}
        } else {
          Future.failed(CommentException(CommentException.UPDATE_ERROR))
        }
      }
    }
  }

  def getComments(store:String, productId:Long , req:CommentGetRequest) : Future[Paging[Comment]] = {
    implicit def json4sFormats: Formats = DefaultFormats

    val size = req.maxItemPerPage.getOrElse(100)
    val from = req.pageOffset.getOrElse(0) * size

    val query = s"""{
      "sort": {"created": "desc"},"from": $from,"size": $size,
      "query": {"filtered": {"filter": {"term": {"productId": $productId}}}}
    }"""

    val fresponse: Future[HttpResponse] = pipeline(Post(route("/" + commentIndex(store) + "/comment/_search"),query))
    fresponse.flatMap {
      response => {
        //println(response.entity.asString)
        if (response.status.isSuccess) {
          val json = parse(response.entity.asString)
          val hits = (json \"hits" \ "hits")

          val transformedJson = for {
            JObject(hit) <- hits.children
            JField("_id",JString(id)) <- hit
            JField("_source",source) <- hit
          } yield {
            val idField = parse(s"""{"id":"$id"}""")
            val merged = source merge idField
            merged
          }

          val results = JArray(transformedJson).extract[List[Comment]]
          val pagedResults = Paging.add[Comment](json,results,req)
          //val pagedResults = Utils.addPaging[Comment](json,req)
          Future{pagedResults}
        } else {
          Future.failed(new ElasticSearchClientException("getComments error"))
        }
      }
    }

  }

  def queryRoot(): Future[HttpResponse] = pipeline(Get(route("/")))


  def execute(){

    /*
    val request = Get("/")
    val response: Future[HttpResponse] = pipeline.flatMap(_(request))
    */


    val response: Future[HttpResponse] = pipeline(Get(route("/")))

    response onComplete{
      case Success(response) => println(response.entity.asString)
        shutdown()

      case Failure(error) => println(error)
        shutdown()
    }
  }

  private def shutdown(): Unit = {
    IO(Http).ask(Http.CloseAll)(1.second).await
    system.shutdown()
    //shutdown()
  }

  /*
  implicit val system = ActorSystem()
  import system.dispatcher // execution context for futures

  val pipeline: HttpRequest => Future[OrderConfirmation] = (
    addHeader("X-My-Special-Header", "fancy-value")
      ~> addCredentials(BasicHttpCredentials("bob", "secret"))
      ~> encode(Gzip)
      ~> sendReceive
      ~> decode(Deflate)
      ~> unmarshal[OrderConfirmation]
    )
  val response: Future[OrderConfirmation] =
    pipeline(Post("http://example.com/orders", Order(42)))
    */

  /**
   * Create ESRequest with exclude
   * @param exclude
   * @return
   */
  private def createESRequest(exclude: List[String]): String = {
    createESRequest(exclude, "")
  }

  private def createESRequest(exclude: List[String], queryFilter: String): String = {
    createESRequest(exclude, queryFilter, List(), None, None, None, None)
  }

  private def createESRequest(exclude: List[String], queryFilter: String, filter: List[String], from: Option[Int], size: Option[Int], sort: Option[String], sortOrder: Option[String]): String = {
    val excl = exclude.filter(s => !s.isEmpty).collect{case s:String => "\"" + s + "\""}.mkString(",")
    val source = if (excl.isEmpty) ""
                  else s""" |   "_source": {
                            |      "exclude": [
                            |         $excl
                            |      ]
                            |   }""".stripMargin

    val query = if (queryFilter.isEmpty) s""" |   "query": {
                                              |      "filtered" : {
                                              |      }
                                              |   }""".stripMargin
                  else s""" |   "query": {
                            |      "filtered" : {
                            |         $queryFilter
                            |      }
                            |   }""".stripMargin

    val filterPart = {
      val notEmptyFilter = filter.filter{s => !s.isEmpty}.mkString(",")
      if (notEmptyFilter.isEmpty) ""
      else s""" |   "filter": {
                |      "bool" : {
                |         "must" :[
                |            $notEmptyFilter
                |         ]
                |      }
                |   }""".stripMargin
    }

    val sortPart = if (!sort.isDefined || !sortOrder.isDefined) ""
    else s""""sort": {"${sort.get}": "${sortOrder.get}"}"""
    val fromPart = if (!from.isDefined) ""
    else s""""from": ${from.get}"""
    val sizePart = if (!size.isDefined) ""
    else s""""size": ${size.get}"""

    s"""  |{
          |${List(source, query, filterPart, sortPart, fromPart, sizePart).filter(s => !s.isEmpty).mkString(",\n")}
          |}""".stripMargin
  }

  /**
   * if lang == "_all" returns an empty list
   * else returns the list of all languages except the given language
   * @param store
   * @param lang
   * @return
   */
  private def createExcludeLang(store: String, lang: String): List[String] = {
    if (lang == "_all") List()
    else getStoreLanguagesAsList(store).filter{case l: String => l != lang}.collect { case l:String =>
      "*." + l
    }
  }

  /**
   * Extract not empty filters of the list and return :<br/>
   * - an empty string if the list is empty<br/>
   * - the filter if the list contains only one filter<br/>
   * - the "and" filter with the not empty given filters
   *
   * @param list
   * @return
   */
  private def createAndFilter(list: List[String]): String = {
    val nomEmptyList = list.filter { s => !s.isEmpty }
    if (nomEmptyList.isEmpty) ""
    else if (nomEmptyList.length == 0) nomEmptyList.head
    else s"""{"and":[${nomEmptyList.mkString(",")}]}"""
  }

  private def createFilter(filter: String): String = {
    if (filter.isEmpty) ""
    else s""""filter":$filter"""
  }

  /**
   * Create a "regexp" filter for the given fied.<br/>
   * the regexp is build using lower case of the given value
   * @param field
   * @param value
   * @return
   */
  private def createExpRegFilter(field: String, value: Option[String]): String = {
    if (value.isEmpty) ""
    else s"""{"regexp": {"$field": ".*${value.get.toLowerCase()}.*"}}"""
  }

  /**
   * Create a "missing" filter
   * @param field
   * @param existence
   * @param nullValue
   * @return
   */
  private def createMissingFilter(field: String, existence: Boolean, nullValue: Boolean): String = {
    s"""{"missing": {"field": "$field", "existence": $existence, "null_value": $nullValue}}"""
  }

  /**
   * Create a "term" filter
   * @param field
   * @param value
   * @return
   */
  private def createTermFilterWithStr(field: String, value: Option[String]): String = {
    if (value.isEmpty) ""
    else s"""{"term": {"$field": "${value.get}"}}"""
  }

  /**
   * Create a numeric "term" filter
   * @param field
   * @param value
   * @return
   */
  private def createTermFilterWithNum(field: String, value: Option[Int]): String = {
    if (value.isEmpty) ""
    else s"""{"term": {"$field": "${value.get}"}}"""
  }

  private def createRangeFilterWithString(field: String, gte: Option[String], lte: Option[String]): String = {
    if (gte.isEmpty && lte.isEmpty) ""
    else {
      val gteStr = if (gte.isEmpty) ""
      else s""""gte":
                "${gte.get}"
            """

      val lteStr = if (lte.isEmpty) ""
      else s""""lte":
                "${lte.get}"
            """

      val range = List(gteStr, lteStr).filter {str => !str.isEmpty }
      if (range.isEmpty) ""
      else s"""{"range": {"$field": { ${range.mkString(",")}}}}"""
    }
  }

  private def createMatchQueryFilter(field: String, value: Option[String]): String = {
    if (value.isEmpty) ""
    else s""""query": {
                "match": {
                    "$field": {
                      "query": "${value.get}",
                      "operator": "and"
                    }
                }
              }"""
  }

  /**
   * Run the url "/" + store + "/" + typeQuery + "/_search" with the given query
   * @param store
   * @param typeQuery
   * @param query
   * @return
   */
  private def search(store: String, typeQuery: String, query: String): Future[HttpResponse] = {
    //TODO add log mechanisme INFO DEBUG etc.
    //println(query)
    pipeline(Post(route("/" + store + "/" + typeQuery + "/_search"), query))
  }
}
