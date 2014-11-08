package com.mogobiz.run.services

import akka.actor.ActorRef
import com.mogobiz.run.implicits.Json4sProtocol
import Json4sProtocol._
import com.mogobiz.run.actors.LangActor.QueryLangRequest
import org.json4s._
import spray.routing.Directives

import scala.concurrent.ExecutionContext

class LangService(storeCode: String, actor: ActorRef)(implicit executionContext: ExecutionContext) extends Directives {

  import akka.pattern.ask
  import akka.util.Timeout

import scala.concurrent.duration._

  implicit val timeout = Timeout(2.seconds)

  val route = {
    pathPrefix("langs") {
      langs
    }
  }

  lazy val langs = pathEnd {
    get {
      val request = QueryLangRequest(storeCode)
      complete {
        (actor ? request).mapTo[JValue]
      }
    }
  }
}
