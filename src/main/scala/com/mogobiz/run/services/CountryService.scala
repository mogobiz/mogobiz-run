package com.mogobiz.run.services

import com.mogobiz.run.config.DefaultComplete
import com.mogobiz.run.config.MogobizHandlers._
import com.mogobiz.run.implicits.Json4sProtocol
import Json4sProtocol._
import org.json4s._
import spray.http.StatusCodes
import spray.routing.Directives


class CountryService extends Directives with DefaultComplete {

  val route = {
    pathPrefix(Segment / "countries") { storeCode =>
      pathEnd {
        get {
          parameters('lang ? "_all") {
            lang =>
              handleCall(countryHandler.queryCountries(storeCode, lang), (json: JValue) => complete(StatusCodes.OK, json))
          }
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
