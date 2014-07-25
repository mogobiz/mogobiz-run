package com.mogobiz.actors

import akka.actor.Actor
import com.mogobiz.actors.TagActor.QueryTagRequest
import com.mogobiz.config.HandlersConfig._

object TagActor {

  case class QueryTagRequest(storeCode: String, hidden: Boolean, inactive: Boolean, lang: String)

}

class TagActor extends Actor {
  def receive = {
    case t: QueryTagRequest => {
      sender ! tagHandler.queryTags(t.storeCode, t.hidden, t.inactive, t.lang)
    }
  }
}
