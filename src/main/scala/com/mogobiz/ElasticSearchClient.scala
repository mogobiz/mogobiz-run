package com.mogobiz

import akka.actor.{Actor, ActorSystem}
import akka.io.IO
import akka.pattern.ask
import scala.util.{Success, Failure}
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
import scala.util.Success
import spray.http.HttpRequest

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
    println(query)
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
        |       "imported",
        |      "hide"
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
    listAllLanguages().filter {
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

  def queryCategories(store: String, qr: CategoryRequest): Future[HttpResponse] = {

    //"name","description","keywords",
    val template = (lang: String, query: String) =>
      s"""
        | {
        |  "_source": {
        |    "include": [
        |      "id","uuid","name","path","parentId",
        |      "$lang.*"
        |    ]
        |   }$query
        | }
        |
      """.stripMargin

    val queryWrapper = (filter: String) =>
      s"""
       |,"query":{"filtered":{"filter":$filter}}
      """.stripMargin

    val andFilter = (filter1: String, filter2: String) =>
      s"""
        | {"and":[$filter1,$filter2]}
      """.stripMargin

    val hiddenFilter = """{"term":{"hide":false}}"""
    val parentFilter = (parent: String) => s"""{"term":{"parentId":$parent}}"""

    val filters = if (!qr.hidden && !qr.parent.isEmpty)
      queryWrapper(andFilter(hiddenFilter, parentFilter(qr.parent.get)))
    else if (!qr.hidden)
      queryWrapper(hiddenFilter)
    else if (!qr.parent.isEmpty)
      queryWrapper(parentFilter(qr.parent.get))
    else ""

    val plang = if (qr.lang == "_all") "*" else qr.lang
    val query = template(plang, filters)
    println(query)
    val response: Future[HttpResponse] = pipeline(Post(route("/" + store + "/category/_search"), query))
    response
  }


  def queryProductsByCriteria(store: String, req: ProductRequest): Future[JValue] = {
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
    val featuredFilter = createFeaturedFilter(req.featured)

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

          //code temporaire pour ne avoir à gérer l'asyncro tout de suite
          //val json = parse(Await.result(response, 1 second).entity.asString)
          val json = parse(response.entity.asString)
          val subset = json \ "hits" \ "hits" \ "_source"
          /*
          println("----------------------------")
          println(subset.getClass.getName)
          println(subset)
          println("----------------------------")
          */
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

          /* + tard
          response onSuccess {
            case response => {

            }
          }*/
          //          println("+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++")
          //          println(pretty(render(products)))
          future(products)
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

  def renderProduct(product: JsonAST.JValue, countryCode: String, currencyCode: String, lang: String, cur: Currency, fieldsToRemove: List[String]): JsonAST.JValue = {
    implicit def json4sFormats: Formats = DefaultFormats
    println(product)
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
      if (x contains JField("countryCode", JString(countryCode.toUpperCase()))) //WARNING toUpperCase ??
      JField("rate", value) <- x} yield value

    val taxRate = lrt.headOption match {
      case Some(JDouble(x)) => x
      case Some(JInt(x)) => x.toDouble
      //      case Some(JNothing) => 0.toDouble
      case _ => 0.toDouble
    }

    val locale = new Locale(lang);
    val endPrice = price + (price * taxRate / 100d)
    val formatedPrice = format(price, currencyCode, locale, cur.rate)
    val formatedEndPrice = format(endPrice, currencyCode, locale, cur.rate)
    val additionalFields = (
      ("localTaxRate" -> taxRate) ~
        ("endPrice" -> endPrice) ~
        ("formatedPrice" -> formatedPrice) ~
        ("formatedEndPrice" -> formatedEndPrice)
      )

    val renderedProduct = product merge additionalFields
    //removeFields(renderedProduct,fieldsToRemove)
    renderedProduct

    /* calcul prix Groovy
    def localTaxRates = product['taxRate'] ? product['taxRate']['localTaxRates'] as List<Map> : []
    def localTaxRate = localTaxRates?.find { ltr ->
      ltr['countryCode'] == country && (!state || ltr['stateCode'] == state)
    }
    def taxRate = localTaxRate ? localTaxRate['rate'] : 0f
    def price = product['price'] ?: 0l
    def endPrice = (price as long) + ((price * taxRate / 100f) as long)
    product << ['localTaxRate': taxRate,
    formatedPrice: format(price as long, currencyCode, locale, rate as double),
    formatedEndPrice: format(endPrice, currencyCode, locale, rate as double)
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

  private def createTermFilterWithStr(field: String, value: Option[String]): String = {
    if (value.isEmpty) ""
    else {
      val valu = value.get
      s"""{
      "term": {"$field": "$valu"}
     }""".stripMargin
    }
  }

  private def createTermFilterWithNum(field: String, value: Option[Int]): String = {
    if (value.isEmpty) ""
    else {
      val valu = value.get
      s"""{
      "term": {"$field": $valu}
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

    if (req.historize) {
      //TODO call addToHistory
    }


    val fresponse: Future[HttpResponse] = pipeline(Get(route("/" + store + "/product/" + id)))
    fresponse.flatMap {
      response => {
        if (response.status.isSuccess) {

          val json = parse(response.entity.asString)
          val subset = json \ "_source"

          val currencies = Await.result(getCurrencies(store, req.lang), 1 second)
          val currency = currencies.filter {
            cur => cur.code == req.currencyCode
          }.headOption getOrElse (new Currency(2, 1, "EUR", "euro"))

          val product = renderProduct(subset, req.countryCode, req.currencyCode, req.lang, currency, List())

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
   * Fulltext products search
   * @param store
   * @param params
   * @return a list of products
   */
  def queryProductsByFulltextCriteria(store: String, params: FulltextSearchProductParameters): Future[JValue] = {

    //"skus", "features", "resources", "datePeriods", "intraDayPeriods","imported",
    var tplquery = (excludedFields: String, text: String) => s"""
      {
        "_source": {
          "exclude": [$excludedFields]
        },
        "query": {
          "query_string": {
            "query": "$text"
          }
        }
      }""".stripMargin


    val fields = (getAllExcludedLanguagesExceptAsList(params.lang) ::: fieldsToRemoveForProductSearchRendering).mkString("\"", "\",\"", "\"")
    val query = tplquery(fields, params.query)
    val fresponse: Future[HttpResponse] = pipeline(Post(route("/" + store + "/product/_search"), query))
    fresponse.flatMap {
      response => {
        if (response.status.isSuccess) {

          val json = parse(response.entity.asString)
          val subset = json \ "hits" \ "hits" \ "_source"

          val currencies = Await.result(getCurrencies(store, params.lang), 1 second)
          val currency = currencies.filter {
            cur => cur.code == params.currencyCode
          }.headOption getOrElse (defaultCurrency)

          val products = subset.children.map {
            p => renderProduct(p, params.countryCode, params.currencyCode, params.lang, currency, fieldsToRemoveForProductSearchRendering)
          }

          future(products)
        } else {
          //TODO log l'erreur
          future(parse(response.entity.asString))
          //throw new ElasticSearchClientException(resp.status.reason)
        }
      }
    }
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
          println(pretty(render(intraDayPeriods)))
          val inPeriods = intraDayPeriods.extract[List[IntraDayPeriod]]

          //date or today
          val now = Calendar.getInstance().getTime
          val today = getCalendar(sdf.parse(sdf.format(now)))
          val d = sdf.parse(req.date.getOrElse(sdf.format(now)))
          var selectedDate = getCalendar(d)

          if (selectedDate.compareTo(today) >= 0) {

            future(List())
          }else{
            //return empty list
            future(List())
          }
        } else {
          //TODO log l'erreur
          future(parse(response.entity.asString))
          //throw new ElasticSearchClientException(resp.status.reason)
        }
      }
    }

    /* code Groovy
    calendarType: DATE_TIME
    List<String> results = []

    if(productId && date){

      def today = IperUtil.today()

      Calendar startCalendar = IperUtil.parseCalendar(date, IperConstant.DATE_FORMAT_WITHOUT_HOUR)

      if (startCalendar.compareTo(today) >= 0) {
        def query = [:]
        def filters = []

        def included = ['intraDayPeriods']

        filters << [term:['id':productId]]
        filters << [term:[calendarType:ProductCalendar.DATE_TIME.name()]]

        query << [query:[filtered:[filter:[and:filters]]]]

        def hits  = search(store, 'product', query, included)?.hits

        results.addAll(hits.size() > 0 ? hits.get(0)['intraDayPeriods'].collect { intraDayPeriod ->
          def period = new DayPeriod(
            startDate: new SimpleDateFormat('yyyy-MM-dd\'T\'HH:mm:ss\'Z\'').parse(intraDayPeriod['startDate'] as String),
          endDate: new SimpleDateFormat('yyyy-MM-dd\'T\'HH:mm:ss\'Z\'').parse(intraDayPeriod['endDate'] as String),
          weekday1: intraDayPeriod['weekday1'] as boolean,
          weekday2: intraDayPeriod['weekday2'] as boolean,
          weekday3: intraDayPeriod['weekday3'] as boolean,
          weekday4: intraDayPeriod['weekday4'] as boolean,
          weekday5: intraDayPeriod['weekday5'] as boolean,
          weekday6: intraDayPeriod['weekday6'] as boolean,
          weekday7: intraDayPeriod['weekday7'] as boolean
          )
          def c = Calendar.getInstance()
          c.setTime(period.startDate)
          isDateIncluded([period], startCalendar) ? RenderUtil.asMapForJSON(c, IperConstant.TIME_FORMAT) : null
        }.flatten() as List<String> : [])
      }
    }*/

  }

  private def getCalendar(d: Date): Calendar = {
    val cal = Calendar.getInstance();
    cal.setTime(d);
    cal;
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
    /*
    //req de creation
    val query = s"""{"productIds":[$productId]}"""
    val fresponse: Future[HttpResponse] = pipeline(Put(route("/" + store + "/history/"+sessionId), query))
    */

    //TODO check and refuse if productId already exists
    val query = s"""{"script":"ctx._source.productIds += pid","params":{"pid":$productId},"upsert":{"productIds":[$productId]}}"""

    val fresponse: Future[HttpResponse] = pipeline(Post(route("/" + store + "/history/"+sessionId+"/_update"), query))

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
  def getProducts(store:String, ids:List[Long],req:ProductDetailsRequest) : Future[List[JValue]] = {
    implicit def json4sFormats: Formats = DefaultFormats

    val fproducts:List[Future[JValue]] = for{
      id <- ids
    } yield queryProductById(store,id,req)

    //TODO to replace by RxScala Iterable
    val f = Future.sequence(fproducts.toList)

    //TODO try with a for-compr
    f.flatMap {
      list => {
        val validResponses = list.filter{
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

    val fresponse: Future[HttpResponse] = pipeline(Get(route("/" + store + "/history/"+sessionId)))
    fresponse.flatMap {
      response => {
        if (response.status.isSuccess) {
          val json = parse(response.entity.asString)
          val subset = json \ "_source"
          val productIds = subset \ "productIds"
          future(productIds.extract[List[Long]])
        } else {
          //TODO log l'erreur
          //future(parse(response.entity.asString))
          throw new ElasticSearchClientException(response.entity.asString)
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
}