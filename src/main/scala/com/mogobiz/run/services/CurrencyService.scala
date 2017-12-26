/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives
import com.mogobiz.run.config.DefaultComplete
import com.mogobiz.run.config.MogobizHandlers.handlers._
import org.json4s._
import de.heikoseeberger.akkahttpjson4s.Json4sSupport._
import com.mogobiz.run.implicits.Json4sProtocol
import com.mogobiz.json.JacksonConverter._

class CurrencyService extends Directives with DefaultComplete {

  val route = {
    pathPrefix(Segment / "currencies") { storeCode =>
      pathEnd {
        get {
          parameters('lang ? "_all") { lang =>
            handleCall(currencyHandler.queryCurrency(storeCode, lang),
                       (json: JValue) => complete(StatusCodes.OK, json))
          }
        }
      } ~
        pathPrefix(Segment) { currencyCde =>
          get {
            handleCall(currencyHandler.queryCurrencyByCode(storeCode,
                                                           currencyCde),
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
