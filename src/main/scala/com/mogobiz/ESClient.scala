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
class ESClient  /*extends Actor*/ {

//  override def receive: Actor.Receive = execute

  private val ES_URL = "http://localhost"
  private val ES_HTTP_PORT = 9200

  implicit val system = ActorSystem("es-client")
  import system.dispatcher // execution context for futures

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


  def queryBrands(store:String): Future[HttpResponse] = {
    val response: Future[HttpResponse] = pipeline(Post(route("/"+store+"/_search"),"{\n  \"query\": {\n    \"term\": {\n      \"_type\": \"brand\"\n    }\n  }\n}"))

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
