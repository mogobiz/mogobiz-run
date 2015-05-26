package com.mogobiz.run.services

import com.mogobiz.run.config.DefaultComplete
import com.mogobiz.run.config.HandlersConfig._
import com.mogobiz.run.implicits.Json4sProtocol
import Json4sProtocol._
import org.json4s._
import spray.http.StatusCodes
import spray.routing.Directives


class CountryService(storeCode: String) extends Directives with DefaultComplete {


  val route = {
    pathPrefix("countries") {
      countries
    }
  }

  lazy val countries = pathEnd {
    get {
      parameters('lang ? "_all") {
        lang =>
          handleCall(countryHandler.queryCountries(storeCode, lang), (json: JValue) => complete(StatusCodes.OK, json))
      }
    }
  }
}
