package com.mogobiz.run.actors

import akka.actor.{Props, Actor}
import com.mogobiz.run.actors.EsUpdateActor.ProductNotationsUpdateRequest
import com.mogobiz.run.actors.ProductActor.{QueryCompareProductRequest, QueryFindProductRequest, QueryProductDetailsRequest, QueryProductRequest, _}
import com.mogobiz.run.config.HandlersConfig
import HandlersConfig._
import com.mogobiz.run.model._

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
      val promotionId = q.params.promotionId
      val promotionIds = q.params.hasPromotion.map(v => {
        if(v) {
          val ids = promotionHandler.getPromotionIds(q.storeCode)
          if(ids.isEmpty) None
          else Some(ids.mkString("|"))
        }else None
      }) match {
        case Some(s) => s
        case _ => promotionId
      }

      val params = q.params.copy(promotionId = promotionIds)
      sender ! productHandler.queryProductsByCriteria(q.storeCode, params)

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
      val comment = productHandler.createComment(q.storeCode, q.productId, q.req)

      val updateActor = context.actorOf(Props[EsUpdateActor])
      updateActor ! ProductNotationsUpdateRequest(q.storeCode, q.productId)

      sender ! comment

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
