package com.mogobiz

import akka.actor.{Actor, ActorSystem}
import akka.io.IO
import akka.pattern.ask
import scala.util.{Success, Failure}
import scala.concurrent._
import scala.concurrent.duration._
import spray.can.Http
import spray.httpx.SprayJsonSupport
import spray.client.pipelining._
import spray.util._
import spray.http._
import org.json4s.native.JsonMethods._
import org.json4s._
import org.json4s.JsonDSL._
import scala.Some

/**
 * Created by Christophe on 18/02/14.
 */

class ElasticSearchClient /*extends Actor*/ {

  implicit val system = ActorSystem("es-client")
  import system.dispatcher // execution context for futures

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

  private def route(url:String):String = ES_FULL_URL+url


  /**
   *
   * @param store
   * @param lang
   * @return
   */
  def queryCountries(store:String,lang:String):Future[HttpResponse] = {

    val template = (lang:String) =>
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

    val plang = if(lang=="_all") "*" else lang
    val query = template(plang)
    println(query)
    val response: Future[HttpResponse] = pipeline(Post(route("/"+store+"/country/_search"),query))
    response
  }


  /**
   *
   * @param store
   * @param lang
   * @return
   */
  def queryCurrencies(store:String,lang:String):Future[HttpResponse] = {

    val template = (lang:String) =>
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

    val plang = if(lang=="_all") "*" else lang
    val query = template(plang)
    println(query)
    val response: Future[HttpResponse] = pipeline(Post(route("/"+store+"/rate/_search"),query))
    response
  }

  /**
    * Effectue la recheche de brands dans ES
   * @param store code store
   * @param qr parameters
   * @return
   */
  def queryBrands(store:String,qr:BrandRequest): Future[HttpResponse] = {

    val templateSource = (lang:String,hiddenFilter: String) =>
    s"""
        | {
        | "_source": {
        |    "include": [
        |      "id",
        |      "$lang.*"
        |    ]
        |  }$hiddenFilter
        | }
        |
      """.stripMargin

    val templateQuery = (hideValue:Boolean) =>
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

    val qfilter = if(qr.hidden) "" else templateQuery(qr.hidden)
    val plang = if(qr.lang=="_all") "*" else qr.lang
    val query = templateSource(plang,qfilter)
    println(query)

    val response: Future[HttpResponse] = pipeline(Post(route("/"+store+"/brand/_search"),query))
    response
  }


  /**
   *
   * @param store
   * @param qr
   * @return
   */
  def queryTags(store:String, qr:TagRequest): Future[HttpResponse] = {

    val template = (lang:String) =>
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
    val plang = if(qr.lang=="_all") "*" else qr.lang
    val query = template(plang)
    println(query)
    val response: Future[HttpResponse] = pipeline(Post(route("/"+store+"/tag/_search"),query))
    response
  }

  def listAllLanguages() : List[String] = {
    "fr"::"en"::"es"::"de"::Nil
  }

  def getAllExcludedLanguagesExcept(langRequested:String) : List[String] = {
    listAllLanguages().filter{
      lang => lang!=langRequested
    }
  }

  def getAllExcludedLanguagesExceptAsString(lang:String) : String = {
    val langs = getAllExcludedLanguagesExcept(lang)
    val langsTokens = langs.flatMap{ l => l::"*."+l::Nil} //map{ l => "*."+l}

    langsTokens.mkString("\"","\",\"","\"")
  }



  def queryCategories(store:String, qr:CategoryRequest): Future[HttpResponse] = {

  //"name","description","keywords",
    val template = (lang:String,query:String) =>
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

    val queryWrapper = (filter:String) =>
      s"""
       |,"query":{"filtered":{"filter":$filter}}
      """.stripMargin

    val andFilter = (filter1:String, filter2:String ) =>
      s"""
        | {"and":[$filter1,$filter2]}
      """.stripMargin

    val hiddenFilter = """{"term":{"hide":false}}"""
    val parentFilter = (parent:String) => s"""{"regexp":{"path":"(?)*$parent*"}}"""

    val filters = if(!qr.hidden && !qr.parent.isEmpty)
      queryWrapper(andFilter(hiddenFilter,parentFilter(qr.parent.get)))
    else if(!qr.hidden)
      queryWrapper(hiddenFilter)
    else
      queryWrapper(parentFilter(qr.parent.get))

    val plang = if(qr.lang=="_all") "*" else qr.lang
    val query = template(plang, filters)
    println(query)
    val response: Future[HttpResponse] = pipeline(Post(route("/"+store+"/category/_search"),query))
    response
  }


  def queryProductsByCriteria(store: String, req: ProductRequest): Future[HttpResponse] = { //

    val langsToExclude = getAllExcludedLanguagesExceptAsString(req.lang)
    val source = s"""
        |  "_source": {
        |     "include":["id","price","taxRate"],
        |    "exclude": [
        |      $langsToExclude
        |    ]
        |   }
      """.stripMargin
    val nameCriteria = if(req.name.isEmpty){
      ""
    }else {
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


    val codeFilter = createTermFilterWithStr("code",req.code)
    val categoryFilter = createTermFilterWithNum("category.id",req.categoryId)
    val xtypeFilter = createTermFilterWithStr("xtype",req.xtype)
    val pathFilter = createTermFilterWithStr("path",req.path)
    val brandFilter = createTermFilterWithNum("brand.id",req.brandId)
    val tagsFilter = createTermFilterWithStr("tags.name",req.tagName)
    val priceFilter = createRangeFilter("price",req.priceMin,req.priceMax)

    val filters = (codeFilter::categoryFilter::xtypeFilter::pathFilter::brandFilter::tagsFilter::priceFilter::Nil).filter {s => !s.isEmpty}.mkString(",")
    println(filters)


    val filterCriteria = if(filters.isEmpty) "" else s"""
      "filter": {
        "bool": {
          "must": [$filters]
        }
      }
    """.stripMargin

    val pagingAndSortingCriteria = (from:Int,size:Int,sortField:String,sortOrder:String) =>
      s"""
        |"sort": {"$sortField": "$sortOrder"},
        |"from": $from,
        |"size": $size
      """.stripMargin

    val pageAndSort = pagingAndSortingCriteria(req.pageOffset.getOrElse(0) * req.maxItemPerPage.getOrElse(100), req.maxItemPerPage.getOrElse(100), req.orderBy.getOrElse("name"), req.orderDirection.getOrElse("asc"))

    val query = List(source,queryCriteria,filterCriteria,pageAndSort).filter { str => !str.isEmpty }.mkString("{",",","}")

    println(query)
    val response: Future[HttpResponse] = pipeline(Post(route("/"+store+"/product/_search"),query))

    //code temporaire pour ne avoir à gérer l'asyncro tout de suite
    val json = parse(Await.result(response, 1 second).entity.asString)
    val subset = json \ "hits" \ "hits" \ "_source"


    val products = subset.children.map{
      p => renderProduct(p,req.countryCode,req.currencyCode)
    }

    /* + tard
    response onSuccess {
      case response => {

      }
    }*/
    println("+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++")
    println(pretty(render(products)))
    response
    //Future{render(products)}
  }

  def renderProduct(product:JsonAST.JValue,countryCode:String, currencyCode:String):JsonAST.JValue = {
    implicit def json4sFormats: Formats = DefaultFormats
    val price = (product \ "price").extract[Int]
    println(price)

    val lrt = for {
      localTaxRate @ JObject(x) <- product \ "taxRate" \ "localTaxRates"
      if (x contains JField("countryCode", JString(countryCode)))
      JField("rate",value) <- x } yield value

    val taxRate = lrt.headOption match {
      case Some(JDouble(x)) => x
      case Some(JInt(x)) => x.toDouble
      case Some(JNothing) => 0.toDouble
      case _ => 0.toDouble
    }

    val endPrice = price + (price*taxRate/100d)

    val additionalFields = (
      ("localTaxRate"->taxRate) ~
        ("endPrice"->endPrice) ~
        ("formatedPrice"-> (price+" "+currencyCode)) ~
        ("formatedEndPrice"-> (endPrice+" "+currencyCode))
      )

    product merge additionalFields


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

  def createRangeFilter(field:String,gte:Option[Long],lte:Option[Long]): String ={

    if(gte.isEmpty && lte.isEmpty) ""
    else{
      val gteStr = if(gte.isEmpty) "" else {
        val gteVal = gte.get
        s""" "gte":$gteVal """.stripMargin
      }

      val lteStr = if(lte.isEmpty) "" else {
        val lteVal = lte.get
        s""" "lte":$lteVal """.stripMargin
      }

      val range = (gteStr :: lteStr :: Nil).filter { str => !str.isEmpty }
      if(range.isEmpty) ""
      val ranges = range.mkString(",")
      s"""{
        "range": {
          "$field": { $ranges }
        }
      }""".stripMargin
    }
  }

  def createTermFilterWithStr(field:String,value:Option[String]) : String = {
    if(value.isEmpty) ""
    else
     s"""{
      "term": {
        "$field": "$value"
      }
      """.stripMargin
  }

  def createTermFilterWithNum(field:String,value:Option[Int]) : String = {
    if(value.isEmpty) ""
    else s"""{
      "term": {
        "$field": "$value"
      }
      """.stripMargin
  }

  def queryProductById(store: String,id:Long,req: ProductDetailsRequest): Future[HttpResponse]= {

    if(req.historize){
      //TODO call addToHistory
    }

    val response: Future[HttpResponse] = pipeline(Get(route("/"+store+"/product/"+id)))
    response
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
}
