/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import com.mogobiz.run.config.DefaultComplete
import com.mogobiz.run.config.MogobizHandlers.handlers._
import com.mogobiz.run.implicits.Json4sProtocol
import Json4sProtocol._
import org.json4s._
import spray.http.StatusCodes
import spray.routing.Directives

class CurrencyService extends Directives with DefaultComplete {

  val route = {
    pathPrefix(Segment / "currencies") { storeCode =>
      pathEnd {
        get {
          parameters('lang ? "_all") { lang =>
            handleCall(currencyHandler.queryCurrency(storeCode, lang), (json: JValue) => complete(StatusCodes.OK, json))
          }
        }
      } ~
        pathPrefix(Segment) { currencyCde =>
          get {
            handleCall(currencyHandler.queryCurrencyByCode(storeCode, currencyCde),
              (json: JValue) => complete(StatusCodes.OK, json))
          }
        }
    }
  }

  /*
  lazy val currencies = pathEnd {
    get {
      parameters('lang ? "_all") { lang =>
        handleCall(currencyHandler.queryCurrency(storeCode, lang), (json: JValue) => complete(StatusCodes.OK, json))
      }
    }
  }*/
}
