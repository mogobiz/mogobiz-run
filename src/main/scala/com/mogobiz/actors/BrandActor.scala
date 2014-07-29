package com.mogobiz.actors

import akka.actor.Actor
import com.mogobiz.actors.BrandActor.QueryBrandRequest
import com.mogobiz.config.HandlersConfig._

object BrandActor {

  case class QueryBrandRequest(storeCode: String, hidden: Boolean, categoryPath: Option[String], lang: String)

}

class BrandActor extends Actor {
  def receive = {
    case q: QueryBrandRequest => {
      sender ! brandHandler.queryBrand(q.storeCode, q.hidden, q.categoryPath, q.lang)
    }
  }
}
