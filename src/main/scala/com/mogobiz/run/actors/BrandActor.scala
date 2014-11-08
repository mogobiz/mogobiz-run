package com.mogobiz.run.actors

import akka.actor.Actor
import com.mogobiz.run.actors.BrandActor.QueryBrandRequest
import com.mogobiz.run.config.HandlersConfig
import HandlersConfig._

object BrandActor {

  case class QueryBrandRequest(storeCode: String, hidden: Boolean, categoryPath: Option[String], lang: String, promotionId:Option[String])

}

class BrandActor extends Actor {
  def receive = {
    case q: QueryBrandRequest =>
      sender ! brandHandler.queryBrands(q.storeCode, q.hidden, q.categoryPath, q.lang, q.promotionId)
  }
}
