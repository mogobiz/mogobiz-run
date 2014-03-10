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


  def queryCategories(store:String, qr:CategoryRequest): Future[HttpResponse] = {

  //"name","description","keywords",
    val template = (lang:String,query:String) =>
      s"""
        | {
        |  "_source": {
        |    "include": [
        |      "id","uuid","path",
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
