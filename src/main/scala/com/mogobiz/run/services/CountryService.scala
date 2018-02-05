/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import com.mogobiz.run.config.DefaultComplete
import com.mogobiz.run.config.MogobizHandlers.handlers._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives
import org.json4s._
import de.heikoseeberger.akkahttpjson4s.Json4sSupport._

class CountryService extends Directives with DefaultComplete {

  val route = {
    pathPrefix(Segment / "countries") { storeCode =>
      pathEnd {
        get {
          parameters('lang ? "_all") { lang =>
            handleCall(countryHandler.queryCountries(storeCode, lang),
                       (json: JValue) => complete(StatusCodes.OK, json))
          }
        }
      } ~
        pathPrefix(Segment) { countryCode =>
          get {
            handleCall(countryHandler.queryCountryByCode(storeCode,
                                                         countryCode),
                       (json: JValue) => complete(StatusCodes.OK, json))
          }
        }
    }
  }
  /*
  lazy val countries = pathEnd {
    get {
      parameters('lang ? "_all") {
        lang =>
          handleCall(countryHandler.queryCountries(storeCode, lang), (json: JValue) => complete(StatusCodes.OK, json))
      }
    }
  }*/
}
