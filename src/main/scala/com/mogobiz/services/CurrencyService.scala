package com.mogobiz.services

import akka.actor.ActorRef
import com.mogobiz.json.Json4sProtocol
import Json4sProtocol._
import com.mogobiz.actors.CurrencyActor.QueryCurrencyRequest
import org.json4s._
import spray.routing.Directives

import scala.concurrent.ExecutionContext

class CurrencyService(storeCode: String, actor: ActorRef)(implicit executionContext: ExecutionContext) extends Directives {

  import akka.pattern.ask
  import akka.util.Timeout

import scala.concurrent.duration._

  implicit val timeout = Timeout(2.seconds)

  val route = {
    pathPrefix("currencies") {
      currencies
    }
  }

  lazy val currencies = pathEnd {
    get {
      parameters('lang ? "_all") {
        lang => {
          val request = QueryCurrencyRequest(storeCode, lang)
          complete {
            (actor ? request).mapTo[JValue]
          }
        }
      }
    }
  }

}
