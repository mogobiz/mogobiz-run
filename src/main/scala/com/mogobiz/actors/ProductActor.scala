package com.mogobiz.actors

import akka.actor.Actor
import com.mogobiz.actors.ProductActor.{QueryCompareProductRequest, QueryFindProductRequest, QueryProductDetailsRequest, QueryProductRequest, _}
import com.mogobiz.config.HandlersConfig._
import com.mogobiz.model._

object ProductActor {

  case class QueryProductRequest(storeCode: String, params: ProductRequest)

  case class QueryFindProductRequest(storeCode: String, params: FullTextSearchProductParameters)

  case class QueryCompareProductRequest(storeCode: String, params: CompareProductParameters)

  case class QueryProductDetailsRequest(storeCode: String, params: ProductDetailsRequest, productId: Long, uuid: String)

  case class QueryProductDatesRequest(storeCode: String, params: ProductDatesRequest, productId: Long, uuid: String)

  case class QueryProductTimesRequest(storeCode: String, params: ProductTimesRequest, productId: Long, uuid: String)

  case class QueryUpdateCommentRequest(storeCode: String, productId: Long, commentId: String, useful: Boolean)

  case class QueryGetCommentRequest(storeCode: String, productId: Long, req: CommentGetRequest)

  case class QueryCreateCommentRequest(storeCode: String, productId: Long, req: CommentRequest)

  case class QueryVisitedProductRequest(storeCode: String, req: VisitorHistoryRequest,  uuid: String)

  case class QueryNotationProductRequest(storeCode: String, lang: String)
}

class ProductActor extends Actor {
  def receive = {
    case q: QueryProductRequest =>
      sender ! productHandler.queryProductsByCriteria(q.storeCode, q.params)

    case q: QueryFindProductRequest =>
      sender ! productHandler.queryProductsByFulltextCriteria(q.storeCode, q.params)

    case q: QueryCompareProductRequest =>
      sender ! productHandler.getProductsFeatures(q.storeCode, q.params)

    case q: QueryProductDetailsRequest =>
      sender ! productHandler.getProductDetails(q.storeCode, q.params, q.productId, q.uuid)

    case q: QueryProductDatesRequest =>
      sender ! productHandler.getProductDates(q.storeCode, q.params, q.productId, q.uuid)

    case q: QueryProductTimesRequest =>
      sender ! productHandler.getProductTimes(q.storeCode, q.params, q.productId, q.uuid)

    case q: QueryCreateCommentRequest =>
      sender ! productHandler.createComment(q.storeCode, q.productId, q.req)

    case q: QueryGetCommentRequest =>
      sender ! productHandler.getComment(q.storeCode, q.productId, q.req)

    case q: QueryUpdateCommentRequest =>
      sender ! productHandler.updateComment(q.storeCode, q.productId, q.commentId, q.useful)

    case q: QueryVisitedProductRequest =>
      sender ! productHandler.getProductHistory(q.storeCode, q.req, q.uuid)

    case q: QueryNotationProductRequest =>
      sender ! productHandler.getProductsByNotation(q.storeCode, q.lang)
  }
}
