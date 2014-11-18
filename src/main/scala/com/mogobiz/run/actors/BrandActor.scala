package com.mogobiz.run.actors

import akka.actor.Actor
import com.mogobiz.run.actors.BrandActor.QueryBrandRequest
import com.mogobiz.run.config.HandlersConfig
import HandlersConfig._

import scala.util.Try

object BrandActor {

  case class QueryBrandRequest(storeCode: String, hidden: Boolean, categoryPath: Option[String], lang: String, promotionId:Option[String], size: Option[Int])

}

class BrandActor extends Actor {
  def receive = {
    case q: QueryBrandRequest =>
      sender ! Try(brandHandler.queryBrands(q.storeCode, q.hidden, q.categoryPath, q.lang, q.promotionId, q.size))
  }
}
