package com.mogobiz.actors

import akka.actor.Actor
import com.mogobiz.actors.CountryActor.QueryCountryRequest
import com.mogobiz.config.HandlersConfig._

object CountryActor {

  case class QueryCountryRequest(storeCode: String, lang: String)

}

class CountryActor extends Actor {
  def receive = {
    case q: QueryCountryRequest => {
      sender ! countryHandler.queryCountries(q.storeCode, q.lang)
    }
  }
}
