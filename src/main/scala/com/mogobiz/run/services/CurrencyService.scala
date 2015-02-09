package com.mogobiz.run.services

import com.mogobiz.run.config.HandlersConfig._
import com.mogobiz.run.implicits.Json4sProtocol
import Json4sProtocol._
import org.json4s._
import spray.http.StatusCodes
import spray.routing.Directives

import scala.concurrent.ExecutionContext

class CurrencyService(storeCode: String)(implicit executionContext: ExecutionContext) extends Directives with DefaultComplete {

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
