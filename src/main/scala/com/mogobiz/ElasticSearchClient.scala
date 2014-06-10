package com.mogobiz

import akka.actor.ActorSystem
import akka.io.IO
import akka.pattern.ask
import scala.concurrent._
import scala.concurrent.duration._
import spray.can.Http
import spray.client.pipelining._
import spray.util._
import org.json4s.native.JsonMethods._
import org.json4s._
import org.json4s.JsonDSL._
import java.util.{Date, Calendar, Locale}
import java.text.{SimpleDateFormat, NumberFormat}
import scala.util.Failure
import scala.Some
import spray.http.HttpResponse
import scala.List
import scala.util.Success
import spray.http.HttpRequest
import com.mogobiz.vo.{Comment,CommentRequest,CommentGetRequest, Paging}
import scala.collection.{mutable, immutable}
import org.json4s.JsonAST.{JNothing, JArray, JObject}
import java.util

//import org.json4s.JObject
//import org.json4s.JArray

/**
 * Created by Christophe on 18/02/14.
 */

class ElasticSearchClient /*extends Actor*/ {

  implicit val system = ActorSystem("es-client")

  import system.dispatcher

  // execution context for futures

  //  def queryRoot(): Future[HttpResponse] = pipeline(Get(route("/")))
  //  override def receive: Actor.Receive = execute

  private val ES_URL = "http://localhost"
  private val ES_HTTP_PORT = 9200


  /*
  val pipeline: Future[SendReceive] =
    for (
      Http.HostConnectorInfo(connector, _) <-
      IO(Http) ? Http.HostConnectorSetup(ES_URL, port = ES_HTTP_PORT)
    ) yield sendReceive(connector)
*/

  val pipeline: HttpRequest => Future[HttpResponse] = sendReceive

  private val ES_FULL_URL = ES_URL + ":" + ES_HTTP_PORT

  private def route(url: String): String = ES_FULL_URL + url

  private def historyIndex(store:String):String = {
    return store+"_history"
  }

  /**
   * Returns the ES index for store preferences user
   * @param store
   * @return
   */
  private def prefsIndex(store:String):String = {
    return store+"_prefs"
  }

  private def cartIndex(store:String):String = {
    return store+"_cart"
  }

  private def commentIndex(store:String):String = {
    return store+"_comment"
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
        future(response.status.isSuccess)
      }
    }
  }

  def getPreferences(store: String, uuid: String): Future[Prefs] = {
    implicit def json4sFormats: Formats = DefaultFormats

    val response : Future[HttpResponse] = pipeline(Get(route("/" + prefsIndex(store) + "/prefs/" + uuid)))
    response.flatMap {
      response => {
        if (response.status.isSuccess) {
          val json = parse(response.entity.asString)
          val subset = json \ "_source"
          val prefs : Future[JValue] = future(subset)
          prefs.flatMap { p =>
            future(p.extract[Prefs])
          }
        }
        else {
          future(Prefs(10))
        }
      }
    }
  }

  /**
   *
   * @param store
   * @param lang
   * @return
   */
  def queryCountries(store: String, lang: String): Future[HttpResponse] = {

    val template = (lang: String) =>
      s"""
        | {
        |  "_source": {
        |    "include": [
        |      "id",
        |      "code",
        |      "name",
        |      "$lang.*"
        |    ]
        |   }
        | }
        |
      """.stripMargin

    val plang = if (lang == "_all") "*" else lang
    val query = template(plang)
    println(query)
    val response: Future[HttpResponse] = pipeline(Post(route("/" + store + "/country/_search"), query))
    response
  }


  /**
   *
   * @param store
   * @param lang
   * @return
   */
  def queryCurrencies(store: String, lang: String): Future[JValue] = {

    val template = (lang: String) =>
      s"""
        | {
        |  "_source": {
        |    "include": [
        |      "id",
        |      "currencyFractionDigits",
        |      "rate",
        |      "code",
        |      "name",
        |      "$lang.*"
        |    ]
        |   }
        | }
        |
      """.stripMargin

    val plang = if (lang == "_all") "*" else lang
    val query = template(plang)
    //println(query)
    val response: Future[HttpResponse] = pipeline(Post(route("/" + store + "/rate/_search"), query))
    response.flatMap {
      response => {
        val json = parse(response.entity.asString)
        val subset = json \ "hits" \ "hits" \ "_source"
        future(subset)
      }
    }
  }

  def getCurrencies(store: String, lang: String): Future[List[Currency]] = {
    implicit def json4sFormats: Formats = DefaultFormats

    val currencies = queryCurrencies(store, lang).flatMap {
      json => future(json.extract[List[Currency]])
    }

    currencies
  }

  /**
   * Effectue la recheche de brands dans ES
   * @param store code store
   * @param qr parameters
   * @return
   */
  def queryBrands(store: String, qr: BrandRequest): Future[JValue] = {

    val templateSource = (lang: String, hiddenFilter: String) =>
      s"""
        | {
        | "_source": {
        |    "exclude": [
        |      "imported"
        |      $lang
        |    ]
        |  }$hiddenFilter
        | }
        |
      """.stripMargin

    val templateQuery = (hideValue: Boolean) =>
      s"""
        | ,"query": {
        |    "filtered": {
        |      "filter": {
        |        "term": {
        |          "hide": $hideValue
        |        }
        |      }
        |    }
        |  }
      """.stripMargin

    val qfilter = if (qr.hidden) "" else templateQuery(qr.hidden)
    val langToExclude = if (qr.lang == "_all") "" else "," + getAllExcludedLanguagesExceptAsString(qr.lang)

    val query = templateSource(langToExclude, qfilter)
    println(query)
    //TODO logger pour les query
    //TODO logger pour les reponses
    //TODO logger WARNING pour les requetes trop longues
    //TODO créer une PartialFunction qui s'occupe de la gestion d'erreur quand requetes ES KO
    val response: Future[HttpResponse] = pipeline(Post(route("/" + store + "/brand/_search"), query))

    response.flatMap {
      resp => {
        if (resp.status.isSuccess) {
          val json = parse(resp.entity.asString)
          val subset = json \ "hits" \ "hits" \ "_source"
          future(subset)
        } else {
          //TODO log l'erreur
          future(parse(resp.entity.asString))
          //throw new ElasticSearchClientException(resp.status.reason)
        }
      }
    }
  }


  /**
   *
   * @param store
   * @param qr
   * @return
   */
  def queryTags(store: String, qr: TagRequest): Future[HttpResponse] = {

    val template = (lang: String) =>
      s"""
        | {
        |  "_source": {
        |    "include": [
        |      "id",
        |      "$lang.*"
        |    ]
        |   }
        | }
        |
      """.stripMargin
    val plang = if (qr.lang == "_all") "*" else qr.lang
    val query = template(plang)
    println(query)
    val response: Future[HttpResponse] = pipeline(Post(route("/" + store + "/tag/_search"), query))
    response
  }

  def listAllLanguages(): List[String] = {
    "fr" :: "en" :: "es" :: "de" :: Nil
  }

  def getAllExcludedLanguagesExcept(langRequested: String): List[String] = {
    if (langRequested.isEmpty) listAllLanguages()
    else listAllLanguages().filter {
      lang => lang != langRequested
    }
  }

  def getAllExcludedLanguagesExceptAsList(lang: String): List[String] = {
    val langs = getAllExcludedLanguagesExcept(lang)
    val langsTokens = langs.flatMap {
      l => l :: "*." + l :: Nil
    } //map{ l => "*."+l}

    langsTokens
  }

  def getAllExcludedLanguagesExceptAsString(lang: String): String = {
    val langs = getAllExcludedLanguagesExcept(lang)
    val langsTokens = langs.flatMap {
      l => l :: "*." + l :: Nil
    } //map{ l => "*."+l}

    langsTokens.mkString("\"", "\",\"", "\"")
  }

  def getLangFieldsWithPrefix(preField: String, field: String): String = {
    val langs = listAllLanguages()
    val langsTokens = langs.flatMap {
      l => preField + l + "." + field :: Nil
    }
    langsTokens.mkString("\"", "\", \"", "\"")
  }

  def getIncludedFieldWithPrefix(preField:  String, field: String, lang: String) : String = {
    {
      if ("_all".equals(lang)) {
        getLangFieldsWithPrefix(preField, field)
      } else {
        "\"" + preField + lang + "." + field + "\""
      }
    }
  }

  def getIncludedFields(field: String, lang: String) : String = {
    getIncludedFieldWithPrefix("",field,lang)
  }

  def getHighlightedFields(field: String): String = {
    val langs = listAllLanguages()
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
      val include = List()
      val exclude = createExcludeLang(qr.lang) :+ "imported"

      val brandIdFilter = createTermFilterWithStr("brand.id", qr.brandId)
      val hideFilter = if (!qr.hidden) createTermFilterWithStr("category.hide", Option("false")) else ""
      val parentIdFilter = createTermFilterWithStr("category.parentId", qr.parentId)
      val categoryPathFilter = createExpRegFilter("category.path", qr.categoryPath)
      val andFilters = createAndFilter(List(brandIdFilter, hideFilter, parentIdFilter, categoryPathFilter))

      val query = createQuery(include, exclude, createFilter(andFilters))
      println(query)
      val response: Future[HttpResponse] = pipeline(Post(route("/" + store + "/product/_search"), query))
      response
    }
    else {
      var include = List()
      val exclude = createExcludeLang(qr.lang) :+ "imported"

      val hideFilter = if (!qr.hidden) createTermFilterWithStr("hide", Option("false")) else ""
      val parentIdFilter = if (qr.parentId.isDefined) createTermFilterWithStr("parentId", qr.parentId)
                            else if (!qr.categoryPath.isDefined) createMissingFilter("parentId", true, true)
                            else ""
      val categoryPathFilter = createExpRegFilter("path", qr.categoryPath)
      val andFilters = createAndFilter(List(hideFilter, parentIdFilter, categoryPathFilter))

      val query = createQuery(include, exclude, createFilter(andFilters))
      println(query)
      val response: Future[HttpResponse] = pipeline(Post(route("/" + store + "/category/_search"), query))
      response
    }
  }


  def queryProductsByCriteria(store: String, req: ProductRequest): Future[JValue] = {
    //println("queryProductsByCriteria")
    val fieldsToExclude = (getAllExcludedLanguagesExceptAsList(req.lang) ::: fieldsToRemoveForProductSearchRendering).mkString("\"", "\",\"", "\"")
    //TODO propose a way to request include / exclude fields         "include":["id","price","taxRate"],

    //    val langsToExclude = getAllExcludedLanguagesExceptAsString(req.lang)bt
    val source = s"""
        |  "_source": {
        |    "exclude": [$fieldsToExclude]
        |   }
      """.stripMargin
    val nameCriteria = if (req.name.isEmpty) {
      ""
    } else {
      val name = req.name.get
      s"""
      "query": {
                "match": {
                    "name": {
                        "query": "$name",
                        "operator": "and"
                    }
                }
            }
      """.stripMargin
    }

    val queryCriteria = s"""
      "query": {
        "filtered": {
          $nameCriteria
        }
      }
      """.stripMargin


    val codeFilter = createTermFilterWithStr("code", req.code)
    val categoryFilter = createTermFilterWithNum("category.id", req.categoryId)
    val xtypeFilter = createTermFilterWithStr("xtype", req.xtype)
    val pathFilter = createTermFilterWithStr("path", req.path)
    val brandFilter = createTermFilterWithNum("brand.id", req.brandId)
    val tagsFilter = createTermFilterWithStr("tags.name", req.tagName)
    val priceFilter = createRangeFilter("price", req.priceMin, req.priceMax)
    val featuredFilter = createFeaturedFilter(req.featured.getOrElse(false))

    val filters = (codeFilter :: categoryFilter :: xtypeFilter :: pathFilter :: brandFilter :: tagsFilter :: priceFilter :: featuredFilter :: Nil).filter {
      s => !s.isEmpty
    }.mkString(",")
    println(filters)


    val filterCriteria = if (filters.isEmpty) ""
    else s"""
      "filter": {
        "bool": {
          "must": [$filters]
        }
      }
    """.stripMargin

    val pagingAndSortingCriteria = (from: Int, size: Int, sortField: String, sortOrder: String) =>
      s"""
        |"sort": {"$sortField": "$sortOrder"},
        |"from": $from,
        |"size": $size
      """.stripMargin

    val pageAndSort = pagingAndSortingCriteria(req.pageOffset.getOrElse(0) * req.maxItemPerPage.getOrElse(100), req.maxItemPerPage.getOrElse(100), req.orderBy.getOrElse("name"), req.orderDirection.getOrElse("asc"))

    val query = List(source, queryCriteria, filterCriteria, pageAndSort).filter {
      str => !str.isEmpty
    }.mkString("{", ",", "}")

    println(query)
    val fresponse: Future[HttpResponse] = pipeline(Post(route("/" + store + "/product/_search"), query))
    fresponse.flatMap {
      response => {
        if (response.status.isSuccess) {

          val json = parse(response.entity.asString)
          val subset = json \ "hits" \ "hits" \ "_source"
          val currencies = Await.result(getCurrencies(store, req.lang), 1 second) //TODO parrallele for loop
          val currency = currencies.filter {
              cur => cur.code == req.currencyCode
            }.headOption getOrElse (defaultCurrency)

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

          future(res)
        } else {
          //TODO log l'erreur
          future(parse(response.entity.asString))
          //throw new ElasticSearchClientException(resp.status.reason)
        }
      }
    }
  }

  private val defaultCurrency = new Currency(2, 1, "EUR", "euro") //FIXME trouvez autre chose

  private val fieldsToRemoveForProductSearchRendering = List("skus", "features", "resources", "datePeriods", "intraDayPeriods")

  def renderProduct(product: JsonAST.JValue, country: Option[String], currency: Option[String], lang: String, cur: Currency, fieldsToRemove: List[String]): JsonAST.JValue = {
    if (country.isEmpty || currency.isEmpty) {
      product
    } else {
      implicit def json4sFormats: Formats = DefaultFormats
      //    println(product)
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
      val formatedPrice = format(price, currency.get, locale, cur.rate)
      val formatedEndPrice = format(endPrice, currency.get, locale, cur.rate)
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

  private def format(amount: Double, currencyCode: String, locale: Locale = Locale.getDefault, rate: Double = 0): String = {
    val numberFormat = NumberFormat.getCurrencyInstance(locale);
    numberFormat.setCurrency(java.util.Currency.getInstance(currencyCode));
    return numberFormat.format(amount * rate);
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

          val currencies = Await.result(getCurrencies(store, req.lang), 1 second)
          val currency = currencies.filter {
            cur => cur.code == req.currency
          }.headOption getOrElse (new Currency(2, 1, "EUR", "euro"))

          val product = renderProduct(subset, req.country, req.currency, req.lang, currency, List())

          future(product)
        } else {
          //TODO log l'erreur
          future(parse(response.entity.asString))
          //throw new ElasticSearchClientException(resp.status.reason)
        }
      }
    }
  }


  /**
   * Get multiple products features
   * @param store
   * @param params
   * @return a list of products
   */
  def getProductsFeatures(store: String, params: CompareProductParameters): Future[JValue] = {

    val idList = params.ids.split(",").toList

    def getFetchConfig(id: String, lang: String): String = {
      val nameFields = getIncludedFields("name", lang)
      val featuresNameField = getIncludedFieldWithPrefix("features.", "name", lang)
      val featuresValueField = getIncludedFieldWithPrefix("features.", "value", lang)
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

    val fetchConfigsList = idList.map(id => getFetchConfig(id, params._lang))

    val fetchConfigs = fetchConfigsList.filter {
      str => !str.isEmpty
    }.mkString("[", ",", "]")

    val multipleGetQueryTemplate = s"""
      {
        "docs": $fetchConfigs
      }
      """.stripMargin

    println(multipleGetQueryTemplate)

    /**
     * This function zip tow map with default value
     * @param map1
     * @param map2
     * @return map with zipped value
     */
    def zipper(map1: immutable.Map[String, String], map2: immutable.Map[String, String]) = {

      val elementsNumberSet: Set[Int] = map1.values.map(v => v.split(",").size).toSet
      val elementsNumber = if (elementsNumberSet.isEmpty) 0 else elementsNumberSet.max

      for (key <- map1.keys ++ map2.keys) yield key -> {
        val map1Size: Int = map1.getOrElse(key, "-").split(",").size
        val map1Value = if (map1Size < elementsNumber) {
          {
            for (i <- 1 to elementsNumber - map1Size)
            yield map1.getOrElse(key, "-")
          }.mkString(",")
        } else {
          map1.getOrElse(key, "-")
        }
        (map1Value + "," + map2.getOrElse(key, "-"))
      }
    }

      // TODO A VALIDER !
    /**
     * This function translates properties returned by the store to the specified language
     * @param lang - the lang to use for translation
     * @param jv - the JValue object to be translated
     * @return the properties translated
     */
    def translate(lang: String, jv: JValue): JValue = {
      implicit val formats = DefaultFormats
      val map = collection.mutable.Map(jv.extract[Map[String, JValue]].toSeq: _*)
      if (!lang.equals("_all")) {
        val _jvLang: JValue = map.getOrElse(lang, JNothing)
        if (_jvLang != JNothing) {
          val _langMap = _jvLang.extract[Map[String, JValue]]
          _langMap foreach {
            case (k, v) => map(k) = v
          }
          map.remove(lang)
        }
        map foreach {
          case (k: String, v: JObject) => map(k) = translate(lang, v)
          case (k: String, v: JArray) => map(k) = v.children.toList map {
            jv => translate(lang, jv)
          }
          case (k: String, _) =>
        }
      } else {
        listAllLanguages() foreach {
          _lng => {
            val _jvLang: JValue = map.getOrElse(_lng, JNothing)
            //println(_jvLang)
            if (_jvLang != JNothing) {
              val _langMap = _jvLang.extract[Map[String, JValue]]
              //println(_langMap)
              _langMap foreach {
                case (k, v) => map.put(_lng,v)
              }
              //println(map)
            }
            map foreach {
              case (k: String, v: JObject) => map(k) = translate(lang, v)
              case (k: String, v: JArray) => map(k) = v.children.toList map {
                jv => translate(lang, jv)
              }
              case (k: String, _) =>
            }
          }
        }
      }
      //println(compact(render(JsonDSL.map2jvalue(map.toMap))))
      JsonDSL.map2jvalue(map.toMap)
    }

    def httpResponseToFuture(response: HttpResponse, params: CompareProductParameters): Future[JValue] = {
      implicit def formats = DefaultFormats

      if (response.status.isSuccess) {
        val lang : String = params._lang
        val json = parse(response.entity.asString)
        val docsArray = (json \ "docs")

        var result = immutable.Map.empty[String, String]
        var _langValueMap = collection.mutable.Map.empty[String,Map[String,String]]
        var _langNameMap = collection.mutable.Map.empty[String,Map[String,String]]

        val rawIds: List[BigInt] = for {
          JObject(result) <- (docsArray \ "_source")
          JField("id", JInt(id)) <- result
        } yield (id)

        val rawFeatures: List[(BigInt, List[JValue])] = {
          for {
            JObject(result) <- (docsArray \ "_source")
            JField("id", JInt(id)) <- result
            JField("features", JArray(features)) <- result
          } yield (id -> features)
        }

        for (id <- rawIds) {

          val _featuresForId = rawFeatures.toMap.getOrElse(id, List.empty)

          val _translatedFeaturesForId = _featuresForId.map(f => translate(lang, f))
          //println(compact(render(_translatedFeaturesForId)))

          val _resultForId : List[(String,String)] = for {
            _jfeature <- _translatedFeaturesForId
            JString(name) <- _jfeature \ "name"
            JString(value) <- _jfeature \ "value"
          } yield (name , value)

          if (lang.equals("_all")) {
            listAllLanguages() foreach {
              _lang => {
                //val list : List[Map[String,Map[String,String]]] =
                for {
                  _jfeature <- _translatedFeaturesForId
                  JString(value) <- _jfeature \ _lang
                  JString(name) <- _jfeature \ "name"
                } yield {
                   _langValueMap.getOrElse(name,{
                    _langValueMap.put(name, Map(_lang -> value))
                     _langValueMap(name)
                  })
                  _langValueMap(name).getOrElse(_lang,_langValueMap(name) ++ Map(_lang -> value))
                }
                for {
                  _jfeature <- _featuresForId
                  JString(value) <- _jfeature \ _lang
                  JString(name) <- _jfeature \ "name"
                } yield {
                  _langNameMap.getOrElse(name,{
                    _langNameMap.put(name, Map(_lang -> value))
                    _langNameMap(name)
                  })
                  _langNameMap(name).getOrElse(_lang,_langNameMap(name) ++ Map(_lang -> value))
                }
              }
            }
          }
          result = zipper(result, _resultForId.toMap).toMap
          //println("without langs = " + result)
          //println("langs = " + langValues)
        }
        //println(_langValueMap)
        //println(_langNameMap)
        val resultWithDiff: List[Map[String, Object]] = {
          result.map {
            case (k, v) => {
              val _list = v.split(",").toList
              val list = if (_list.size > rawIds.size) _list.tail else _list
              val diff = if (list.toSet.size == 1) "0" else "1"
              var _resultWithDiff = Map(
                "indicator" -> diff,
                "label" -> k,
                "values" -> list.map(v => {
                  var _map = Map("value" -> v)
                  if(!v.equals("-") && lang.equals("_all")){
                    listAllLanguages() foreach {
                      _lang => {
                        val s :String = _langValueMap.getOrElse(k, Map(_lang -> "")).getOrElse(_lang, "")
                        if (s.nonEmpty) {
                          _map = _map + (_lang -> s)
                        }
                      }
                    }
                  }
                  _map
                })
              )
              listAllLanguages() foreach {
                _lang => {
                  val s :String = _langNameMap.getOrElse(k, Map(_lang -> "")).getOrElse(_lang, "")
                  if (s.nonEmpty) {
                    _resultWithDiff = _resultWithDiff + (_lang -> s)
                  }
                }
              }
              _resultWithDiff
            }
          }
        }.toList
        //case class MyResult (ids: Map[String, List[String]], result: Map[String,Map[String, List[String]]])

        //val resultWithDiffAndIds = ComparisonResult(rawIds.map(id => String.valueOf(id)),resultWithDiff)
        val resultWithDiffAndIds = Map(
          "ids" -> rawIds.map(id => String.valueOf(id)),
          "result" -> resultWithDiff)

        println(compact(render(Extraction.decompose(resultWithDiffAndIds))))

        future(Extraction.decompose(resultWithDiffAndIds))

      } else {
        //TODO log l'erreur
        future(parse(response.entity.asString))
        //throw new ElasticSearchClientException(resp.status.reason)
      }
    }

    val fresponse: Future[HttpResponse] = pipeline(Post(route("/" + store + "/_mget"), multipleGetQueryTemplate))
    fresponse.flatMap (response => httpResponseToFuture(response,params))

  }
   
   /**
   * Fulltext products search
   * @param store
   * @param params
   * @return a list of products
   */
  def queryProductsByFulltextCriteria(store: String, params: FulltextSearchProductParameters): Future[JValue] = {

    val includedFields = if (params.highlight) {
      ""
    }
    else {
      ", \"name\", " + getIncludedFields("name",params._lang)
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
      val included = getIncludedFields("name",params._lang)
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
      getHighlightedFields("name")
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
        println(compact(render(result)))
        future(result)

      } else {
        //TODO log l'erreur
        future(parse(response.entity.asString))
        //throw new ElasticSearchClientException(resp.status.reason)
      }
    }

    println(query)
    val fresponse: Future[HttpResponse] = pipeline(Post(route("/" + store + "/product,category,brand/_search"), query))
    fresponse.flatMap (response => httpResponseToFuture(response,params.highlight))

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

    //println(query)
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
          println(pretty(render(intraDayPeriods)))
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
                println(date)
                checkDate(currentDate,endCalendar,date::acc)
              }else{
                checkDate(currentDate,endCalendar,acc)
              }
            }
          }
          val currentDate = getCalendar(startCalendar.getTime())
          val dates = checkDate(currentDate,endCalendar,List())

          future(dates.reverse)
        } else {
          //TODO log l'erreur
          future(parse(response.entity.asString))
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

    //println(query)

    val fresponse: Future[HttpResponse] = pipeline(Post(route("/" + store + "/product/_search"), query))
    fresponse.flatMap {
      response => {
        if (response.status.isSuccess) {

          val json = parse(response.entity.asString)
          val subset = json \ "hits" \ "hits" \ "_source"

//          val datePeriods = subset \ "datePeriods"
//          val outPeriods = datePeriods.extract[List[EndPeriod]]

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

          future(startingHours)
        } else {
          //TODO log l'erreur
          future(parse(response.entity.asString))
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
          future(true)
        } else {
          //TODO log l'erreur
          println(response.entity.asString)
          future(false)
        }
      }
    }
  }

  /**
   * Query for products in paralle according to a list of product ids
   *
   * @param store
   * @param ids
   * @param req
   * @return
   */
  def getProducts(store: String, ids: List[Long], req: ProductDetailsRequest): Future[List[JValue]] = {
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

        future(validResponses)
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
          future(productIds.extract[List[Long]])
        } else {
          //no sessionId available, EmptyList products returned
          future(List())
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

            future(Comment(Some(id),c.userId,c.surname,c.notation,c.subject,c.comment,c.created,productId))
          } else {
            //TODO log l'erreur
            println("new ElasticSearchClientException createComment error")
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
        println(response.entity.asString)
        if (response.status.isSuccess) {
          future(useful)
        } else {
          Future.failed(CommentException(CommentException.UPDATE_ERROR))
        }
      }
    }
  }

  def getComments(store:String, req:CommentGetRequest) : Future[Paging[Comment]] = {
    implicit def json4sFormats: Formats = DefaultFormats

    val size = req.maxItemPerPage.getOrElse(100)
    val from = req.pageOffset.getOrElse(0) * size

    val query = s"""{
      "sort": {"created": "desc"},"from": $from,"size": $size
    }"""

    val fresponse: Future[HttpResponse] = pipeline(Post(route("/" + commentIndex(store) + "/comment/_search"),query))
    fresponse.flatMap {
      response => {
        println(response.entity.asString)
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
          future(pagedResults)
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
   * Create a Query with "_source" (complete using "include" and "exclude" parameters)
   * and with "query" (complete using "filtered" parameter)
   * @param include
   * @param exclude
   * @param filtered
   * @return
   */
  private def createQuery(include: List[String], exclude: List[String], filtered: String): String = {
    val incl = if (!include.isEmpty) s""" "include": [${include.collect{case s:String => s"""\"$s\""""}.mkString(",")}] """.stripMargin else ""
    val excl = if (!exclude.isEmpty) s""" "exclude": [${exclude.collect{case s:String => s"""\"$s\""""}.mkString(",")}] """.stripMargin else ""
    s"""
        | {
        |  "_source": {
        |    ${List(incl, excl).filter(s => !s.isEmpty).mkString(",")}
        |  },
        |  "query":{
        |   "filtered":$filtered
        |  }
        | }
      """.stripMargin
  }

  /**
   * il lang == "_all" returns a empty list
   * else returns the list of all languages except the given language
   * @param lang
   * @return
   */
  private def createExcludeLang(lang: String): List[String] = {
    if (lang == "_all") List()
    else listAllLanguages().filter{case l: String => l != lang}.collect { case l:String =>
      "*." + l
    }
  }

  /**
   * Create a Filter with the given "filter"<br/>
   * The result can bu use with the method "createQuery"
   * @param filter
   * @return
   */
  private def createFilter(filter: String): String = {
    if (filter.isEmpty) ""
    else s"""{"filter": $filter}"""
  }

  /**
   * Extract not empty filters of the list and return :<br/>
   * - a empty string if the list is empty<br/>
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
   * Create a "term" filter
   * @param field
   * @param value
   * @return
   */
  private def createTermFilterWithNum(field: String, value: Option[Int]): String = {
    if (value.isEmpty) ""
    else s"""{"term": {"$field": "${value.get}"}}"""
  }

}
