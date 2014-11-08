package com.mogobiz.run.actors

import akka.actor.Actor
import com.mogobiz.run.actors.CountryActor.QueryCountryRequest
import com.mogobiz.run.config.HandlersConfig
import HandlersConfig._

object CountryActor {

  case class QueryCountryRequest(storeCode: String, lang: String)

}

class CountryActor extends Actor {
  def receive = {
    case q: QueryCountryRequest =>
      sender ! countryHandler.queryCountries(q.storeCode, q.lang)
  }
}
