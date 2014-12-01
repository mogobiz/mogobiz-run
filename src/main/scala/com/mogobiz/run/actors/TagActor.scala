package com.mogobiz.run.actors

import akka.actor.Actor
import com.mogobiz.run.actors.TagActor.QueryTagRequest
import com.mogobiz.run.config.HandlersConfig
import HandlersConfig._

import scala.util.Try

object TagActor {

  case class QueryTagRequest(storeCode: String, hidden: Boolean, inactive: Boolean, lang: String, size: Option[Int])

}

class TagActor extends Actor {
  def receive = {
    case q: QueryTagRequest =>
      sender ! Try(tagHandler.queryTags(q.storeCode, q.hidden, q.inactive, q.lang, q.size))
  }
}
