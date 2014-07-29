package com.mogobiz.services

import akka.actor.ActorRef
import com.mogobiz.Json4sProtocol._
import com.mogobiz.actors.LangActor.QueryLangRequest
import spray.routing.Directives
import org.json4s._

import scala.concurrent.ExecutionContext

class LangService(storeCode: String, langActor: ActorRef)(implicit executionContext: ExecutionContext) extends Directives {

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
      val brandRequest = QueryLangRequest(storeCode)
      complete {
        (langActor ? brandRequest).mapTo[JValue]
      }
    }
  }
}
