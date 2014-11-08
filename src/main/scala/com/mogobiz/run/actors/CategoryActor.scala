package com.mogobiz.run.actors

import akka.actor.Actor
import com.mogobiz.run.actors.CategoryActor.QueryCategoryRequest
import com.mogobiz.run.config.HandlersConfig
import HandlersConfig._

object CategoryActor {

  case class QueryCategoryRequest(storeCode: String, hidden: Boolean, parentId: Option[String], brandId: Option[String], categoryPath: Option[String], lang: String, promotionId: Option[String])

}

class CategoryActor extends Actor {
  def receive = {
    case q: QueryCategoryRequest =>
      sender ! categoryHandler.queryCategories(q.storeCode, q.hidden, q.parentId, q.brandId, q.categoryPath, q.lang, q.promotionId)
  }
}
