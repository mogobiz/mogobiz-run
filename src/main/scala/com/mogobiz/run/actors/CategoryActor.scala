package com.mogobiz.run.actors

import akka.actor.Actor
import com.mogobiz.run.actors.CategoryActor.QueryCategoryRequest
import com.mogobiz.run.config.HandlersConfig
import HandlersConfig._

import scala.util.Try

object CategoryActor {

  case class QueryCategoryRequest(storeCode: String, hidden: Boolean, parentId: Option[String], brandId: Option[String], categoryPath: Option[String], lang: String, promotionId: Option[String], size:Option[Int])

}

class CategoryActor extends Actor {
  def receive = {
    case q: QueryCategoryRequest =>
      sender ! Try(categoryHandler.queryCategories(q.storeCode, q.hidden, q.parentId, q.brandId, q.categoryPath, q.lang, q.promotionId, q.size))
  }
}
