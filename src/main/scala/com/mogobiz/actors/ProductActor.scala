package com.mogobiz.actors

import akka.actor.Actor
import com.mogobiz.actors.ProductActor._
import com.mogobiz.config.HandlersConfig._
import com.mogobiz._
import com.mogobiz.actors.ProductActor.QueryFindProductRequest
import com.mogobiz.actors.ProductActor.QueryProductRequest
import com.mogobiz.actors.ProductActor.QueryCompareProductRequest
import com.mogobiz.actors.ProductActor.QueryProductDetailsRequest
import com.mogobiz.ProductDetailsRequest
import com.mogobiz.actors.ProductActor.QueryFindProductRequest
import com.mogobiz.actors.ProductActor.QueryProductRequest
import com.mogobiz.ProductRequest
import com.mogobiz.actors.ProductActor.QueryCompareProductRequest
import com.mogobiz.CompareProductParameters
import com.mogobiz.actors.ProductActor.QueryProductDetailsRequest
import com.mogobiz.FullTextSearchProductParameters

object ProductActor {

  case class QueryProductRequest(storeCode: String, params: ProductRequest)

  case class QueryFindProductRequest(storeCode: String, params: FullTextSearchProductParameters)

  case class QueryCompareProductRequest(storeCode: String, params: CompareProductParameters)

  case class QueryProductDetailsRequest(storeCode: String, params: ProductDetailsRequest, productId: Long, uuid: String)

  case class QueryProductDatesRequest(storeCode: String, params: ProductDatesRequest, productId: Long, uuid: String)

  case class QueryProductTimesRequest(storeCode: String, params: ProductTimesRequest, productId: Long, uuid: String)
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

    case q: QueryProductDetailsRequest => {
      sender ! productHandler.getProductDetails(q.storeCode, q.params, q.productId, q.uuid)
    }

    case q: QueryProductDatesRequest => {
      sender ! productHandler.getProductDates(q.storeCode, q.params, q.productId, q.uuid)
    }

    case q: QueryProductTimesRequest => {
      sender ! productHandler.getProductTimes(q.storeCode, q.params, q.productId, q.uuid)
    }

  }
}
