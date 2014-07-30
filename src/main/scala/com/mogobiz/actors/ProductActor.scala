package com.mogobiz.actors

import akka.actor.Actor
import com.mogobiz.actors.ProductActor.{QueryFindProductRequest, QueryProductRequest}
import com.mogobiz.config.HandlersConfig._
import com.mogobiz.{FullTextSearchProductParameters, ProductRequest}

object ProductActor {

  case class QueryProductRequest(storeCode: String, params: ProductRequest)

  case class QueryFindProductRequest(storeCode: String, params: FullTextSearchProductParameters)

}

class ProductActor extends Actor {
  def receive = {
    case q: QueryProductRequest => {
      sender ! productHandler.queryProductsByCriteria(q.storeCode, q.params)
    }
    case q: QueryFindProductRequest => {
      sender ! productHandler.queryProductsByFulltextCriteria(q.storeCode, q.params)
    }
  }
}
