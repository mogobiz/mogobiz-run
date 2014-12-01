package com.mogobiz.run.actors

import akka.actor.Actor
import com.mogobiz.run.actors.LangActor.QueryLangRequest
import com.mogobiz.run.config.HandlersConfig
import HandlersConfig._

import scala.util.Try

object LangActor {
  case class QueryLangRequest(storeCode: String)
}

class LangActor extends Actor {
  def receive = {
    case q: QueryLangRequest =>
      sender ! Try(langHandler.queryLang(q.storeCode))
  }
}
