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

  private def buildTemplateFromLang(fieldName: String, lang: String): String = {
    if (lang == "_all") {
      "\"" + fieldName + "*\""
    } else {
      "\"" + fieldName + "\",\"" + fieldName + "." + lang + "\""
    }
  }


//TODO  def queryCountries()

  /**
    * Effectue la recheche de brands dans ES
   * @param store code store
   * @param qr parameters
   * @return
   */
  def queryBrands(store:String,qr:BrandRequest): Future[HttpResponse] = {

    val name = buildTemplateFromLang("name",qr.lang)
    val website = buildTemplateFromLang("website",qr.lang)
    //TODO hide param
    val template = (name:String,website:String) =>
    s"""
        | {
        | "_source": {
        |    "include": [
        |      "id",
        |      $name,
        |      $website
        |    ]
        |  }
        |  }
        |
      """.stripMargin

    val query = template(name,website)

    //println(query)
    //OLD "{\n  \"query\": {\n    \"term\": {\n      \"_type\": \"brand\"\n    }\n  }\n}"
    val response: Future[HttpResponse] = pipeline(Post(route("/"+store+"/brand/_search"),query))
    response
  }



  def queryTags(store:String, qr:TagRequest): Future[HttpResponse] = {
    val name = buildTemplateFromLang("name",qr.lang)
    val template = (name:String) =>
      s"""
        | {
        | "_source": {
        |    "include": [
        |      "id",
        |      $name
        |    ]
        |  }
        |  }
        |
      """.stripMargin
    val query = template(name)
    //println(query)
    val response: Future[HttpResponse] = pipeline(Post(route("/"+store+"/tag/_search"),query))
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
