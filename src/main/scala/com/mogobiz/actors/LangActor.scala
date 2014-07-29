package com.mogobiz.actors

import akka.actor.Actor
import com.mogobiz.config.HandlersConfig._
import com.mogobiz.actors.LangActor.QueryLangRequest

object LangActor {
  case class QueryLangRequest(storeCode: String)
}

class LangActor extends Actor {
  def receive = {
    case q: QueryLangRequest => {
      sender ! langHandler.queryLang(q.storeCode)
    }
  }
}
