package com.mogobiz.actors

import akka.actor.Actor
import com.mogobiz.actors.CategoryActor.QueryCategoryRequest
import com.mogobiz.config.HandlersConfig._

object CategoryActor {

  case class QueryCategoryRequest(storeCode: String, hidden: Boolean, parentId: Option[String], brandId: Option[String], categoryPath: Option[String], lang: String)

}

class CategoryActor extends Actor {
  def receive = {
    case q: QueryCategoryRequest =>
      sender ! categoryHandler.queryCategories(q.storeCode, q.hidden, q.parentId, q.brandId, q.categoryPath, q.lang)
  }
}
