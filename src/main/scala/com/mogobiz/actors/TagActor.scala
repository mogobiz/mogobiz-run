package com.mogobiz.actors

import akka.actor.Actor
import com.mogobiz.actors.TagActor.QueryTagRequest
import com.mogobiz.config.HandlersConfig._

object TagActor {

  case class QueryTagRequest(storeCode: String, hidden: Boolean, inactive: Boolean, lang: String)

}

class TagActor extends Actor {
  def receive = {
    case q: QueryTagRequest =>
      sender ! tagHandler.queryTags(q.storeCode, q.hidden, q.inactive, q.lang)
  }
}
