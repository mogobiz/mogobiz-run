package com.mogobiz.run.actors

import akka.actor.Actor
import com.mogobiz.run.actors.CurrencyActor.QueryCurrencyRequest
import com.mogobiz.run.config.HandlersConfig
import HandlersConfig._

import scala.util.Try

object CurrencyActor {
  case class QueryCurrencyRequest(storeCode: String, lang: String)
}

class CurrencyActor extends Actor {
  def receive = {
    case q: QueryCurrencyRequest =>
      sender ! Try(currencyHandler.queryCurrency(q.storeCode, q.lang))
  }
}
