package com.mogobiz.run.es


import com.mogobiz.es.EsClient
import com.mogobiz.run.config.Settings
import com.sksamuel.elastic4s.ElasticDsl._
import spray.client.pipelining._
import spray.http.{StatusCodes, HttpResponse, HttpRequest}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._

object Mapping extends App {

  def clear = EsClient().execute(delete index Settings.cart.EsIndex).await

  def set(mappingNames: List[String]) {
    def route(url: String) = "http://" + com.mogobiz.es.Settings.ElasticSearch.FullUrl + url
    def mappingFor(name: String) = getClass().getResourceAsStream(s"/es/run/mappings/$name.json")

    implicit val system = akka.actor.ActorSystem("mogobiz-boot")
    val pipeline: HttpRequest => scala.concurrent.Future[HttpResponse] = sendReceive

    mappingNames foreach { name =>
      val url = s"/${Settings.cart.EsIndex}/$name/_mapping"
      val mapping = scala.io.Source.fromInputStream(mappingFor(name)).mkString
      val x: Future[Any] = pipeline(Post(route(url), mapping)) map { response: HttpResponse =>
        response.status match {
          case StatusCodes.OK => System.err.println(s"The mapping for `$name` was successfully set.")
          case _ => System.err.println(s"Error while setting the mapping for `$name`: ${response.entity.asString}")
        }
      }
      Await.result(x, 10 seconds)
    }

    system.shutdown
  }
}
