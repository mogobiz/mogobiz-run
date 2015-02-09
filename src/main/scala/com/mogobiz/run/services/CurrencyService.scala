package com.mogobiz.run.services

import akka.actor.ActorRef
import com.mogobiz.run.config.HandlersConfig._
import com.mogobiz.run.implicits.Json4sProtocol
import Json4sProtocol._
import org.json4s._
import spray.http.StatusCodes
import spray.routing.Directives

import scala.concurrent.ExecutionContext
import scala.util.Try

class CurrencyService(storeCode: String)(implicit executionContext: ExecutionContext) extends Directives with DefaultComplete {

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
      parameters('lang ? "_all") { lang =>
        handleCall(currencyHandler.queryCurrency(storeCode, lang), (json: JValue) => complete(StatusCodes.OK, json))
      }
    }
  }
}
