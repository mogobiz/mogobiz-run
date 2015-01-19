package com.mogobiz.run.actors

import akka.actor.{Props, Actor}
import com.mogobiz.pay.model.Mogopay.Account
import com.mogobiz.run.actors.EsUpdateActor.ProductNotationsUpdateRequest
import com.mogobiz.run.actors.ProductActor.{QueryCompareProductRequest, QueryFindProductRequest, QueryProductDetailsRequest, QueryProductRequest, _}
import com.mogobiz.run.config.HandlersConfig
import HandlersConfig._
import com.mogobiz.run.model.RequestParameters._
import com.mogobiz.run.model._

import scala.util.Try

object ProductActor {

  case class QueryProductRequest(storeCode: String, params: ProductRequest)

  case class QueryFindProductRequest(storeCode: String, params: FullTextSearchProductParameters)

  case class QueryCompareProductRequest(storeCode: String, params: CompareProductParameters)

  case class QueryProductDetailsRequest(storeCode: String, params: ProductDetailsRequest, productId: Long, uuid: String)

  case class QueryProductDatesRequest(storeCode: String, date:Option[String], productId: Long, uuid: String)

  case class QueryProductTimesRequest(storeCode: String, date:Option[String], productId: Long, uuid: String)

  case class QueryUpdateCommentRequest(storeCode: String, productId: Long, commentId: String, useful: Boolean)

  case class QueryGetCommentRequest(storeCode: String, productId: Long, req: CommentGetRequest)

  case class QueryCreateCommentRequest(storeCode: String, productId: Long, req: CommentRequest, account: Option[Account])

  case class QueryVisitedProductRequest(storeCode: String, uuid: String, currency: Option[String], country: Option[String], lang: String)

  case class QueryNotationProductRequest(storeCode: String, lang: String)

  case class QuerySuggestionsRequest(storeCode: String, productId: Long, lang: String)

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
      sender ! Try(productHandler.queryProductsByCriteria(q.storeCode, params))

    case q: QueryFindProductRequest =>
      sender ! Try(productHandler.queryProductsByFulltextCriteria(q.storeCode, q.params))

    case q: QueryCompareProductRequest =>
      sender ! Try(productHandler.getProductsFeatures(q.storeCode, q.params))

    case q: QueryProductDetailsRequest =>
      sender ! Try(productHandler.getProductDetails(q.storeCode, q.params, q.productId, q.uuid))

    case q: QueryProductDatesRequest =>
      sender ! Try(productHandler.getProductDates(q.storeCode, q.date, q.productId, q.uuid))

    case q: QueryProductTimesRequest =>
      sender ! Try(productHandler.getProductTimes(q.storeCode, q.date, q.productId, q.uuid))

    case q: QueryCreateCommentRequest =>
      val comment = Try(productHandler.createComment(q.storeCode, q.productId, q.req, q.account))

      val updateActor = context.actorOf(Props[EsUpdateActor])
      updateActor ! ProductNotationsUpdateRequest(q.storeCode, q.productId)

      sender ! comment

    case q: QueryGetCommentRequest =>
      sender ! Try(productHandler.getComment(q.storeCode, q.productId, q.req))

    case q: QueryUpdateCommentRequest =>
      sender ! Try(productHandler.updateComment(q.storeCode, q.productId, q.commentId, q.useful))

    case q: QueryVisitedProductRequest =>
      sender ! Try(productHandler.getProductHistory(q.storeCode, q.uuid, q.currency, q.country,  q.lang))

    case q: QueryNotationProductRequest =>
      sender ! Try(productHandler.getProductsByNotation(q.storeCode, q.lang))

    case q: QuerySuggestionsRequest =>
      sender ! Try(productHandler.querySuggestions(q.storeCode, q.productId, q.lang))
  }

}
