package com.mogobiz.run.actors

import akka.actor.Actor
import com.mogobiz.run.actors.TagActor.QueryTagRequest
import com.mogobiz.run.config.HandlersConfig
import HandlersConfig._

object TagActor {

  case class QueryTagRequest(storeCode: String, hidden: Boolean, inactive: Boolean, lang: String)

}

class TagActor extends Actor {
  def receive = {
    case q: QueryTagRequest =>
      sender ! tagHandler.queryTags(q.storeCode, q.hidden, q.inactive, q.lang)
  }
}
