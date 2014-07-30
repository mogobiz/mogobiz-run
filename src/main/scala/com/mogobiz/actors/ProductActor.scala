package com.mogobiz.actors

import akka.actor.Actor
import com.mogobiz.actors.ProductActor.{QueryCompareProductRequest, QueryFindProductRequest, QueryProductRequest}
import com.mogobiz.config.HandlersConfig._
import com.mogobiz.{CompareProductParameters, FullTextSearchProductParameters, ProductRequest}

object ProductActor {

  case class QueryProductRequest(storeCode: String, params: ProductRequest)

  case class QueryFindProductRequest(storeCode: String, params: FullTextSearchProductParameters)

  case class QueryCompareProductRequest(storeCode: String, params: CompareProductParameters)

}

class ProductActor extends Actor {
  def receive = {
    case q: QueryProductRequest => {
      sender ! productHandler.queryProductsByCriteria(q.storeCode, q.params)
    }
    case q: QueryFindProductRequest => {
      sender ! productHandler.queryProductsByFulltextCriteria(q.storeCode, q.params)
    }
    case q: QueryCompareProductRequest => {
      sender ! productHandler.getProductsFeatures(q.storeCode, q.params)
    }
  }
}
