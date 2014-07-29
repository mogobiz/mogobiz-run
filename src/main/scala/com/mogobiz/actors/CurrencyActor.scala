package com.mogobiz.actors

import akka.actor.Actor
import com.mogobiz.config.HandlersConfig._
import com.mogobiz.actors.CurrencyActor.QueryCurrencyRequest

object CurrencyActor {
  case class QueryCurrencyRequest(storeCode: String, lang: String)
}

class CurrencyActor extends Actor {
  def receive = {
    case q: QueryCurrencyRequest => {
      sender ! currencyHandler.queryCurrency(q.storeCode, q.lang)
    }
  }
}
