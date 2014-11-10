package com.mogobiz.run.actors

import akka.actor.Actor
import com.mogobiz.run.actors.CartActor._
import com.mogobiz.run.cart.{AddToCartCommand, UpdateCartItemCommand}
import com.mogobiz.run.config.HandlersConfig
import HandlersConfig._
import com.mogobiz.run.model._

object CartActor {

  case class QueryCartInitRequest(storeCode: String, uuid: String, params: CartParameters)
  case class QueryCartClearRequest(storeCode: String, uuid: String, params: CartParameters)
  case class QueryCartItemAddRequest(storeCode: String, uuid: String, params: CartParameters, cmd: AddToCartCommand)
  case class QueryCartItemUpdateRequest(storeCode: String, uuid: String, cartItemId: String, params: CartParameters, cmd: UpdateCartItemCommand)
  case class QueryCartItemRemoveRequest(storeCode: String, uuid: String, cartItemId: String, params: CartParameters)
  case class QueryCartCouponAddRequest(storeCode: String, uuid: String, couponCode: String, params: CouponParameters)
  case class QueryCartCouponDeleteRequest(storeCode: String, uuid: String, couponCode: String, params: CouponParameters)
  case class QueryCartPaymentPrepareRequest(storeCode: String, uuid: String, params: PrepareTransactionParameters)
  case class QueryCartPaymentCommitRequest(storeCode: String, uuid: String, params: CommitTransactionParameters)
  case class QueryCartPaymentCancelRequest(storeCode: String, uuid: String, params: CancelTransactionParameters)

}

class CartActor extends Actor {
  def receive = {
    case q: QueryCartInitRequest =>
      sender ! cartHandler.queryCartInit(q.storeCode,q.uuid, q.params)

    case q: QueryCartClearRequest =>
      sender ! cartHandler.queryCartClear(q.storeCode,q.uuid, q.params)

    case q: QueryCartItemAddRequest =>
      sender ! cartHandler.queryCartItemAdd(q.storeCode,q.uuid, q.params, q.cmd)

    case q: QueryCartItemUpdateRequest =>
      sender ! cartHandler.queryCartItemUpdate(q.storeCode,q.uuid, q.cartItemId, q.params, q.cmd)

    case q: QueryCartItemRemoveRequest =>
      sender ! cartHandler.queryCartItemRemove(q.storeCode,q.uuid, q.cartItemId, q.params)

    case q: QueryCartCouponAddRequest =>
      sender ! cartHandler.queryCartCouponAdd(q.storeCode,q.uuid, q.couponCode, q.params)

    case q: QueryCartCouponDeleteRequest =>
      sender ! cartHandler.queryCartCouponDelete(q.storeCode,q.uuid, q.couponCode, q.params)

    case q: QueryCartPaymentPrepareRequest =>
      sender ! cartHandler.queryCartPaymentPrepare(q.storeCode,q.uuid, q.params)

    case q: QueryCartPaymentCommitRequest =>
      sender ! cartHandler.queryCartPaymentCommit(q.storeCode,q.uuid, q.params)

    case q: QueryCartPaymentCancelRequest =>
      sender ! cartHandler.queryCartPaymentCancel(q.storeCode,q.uuid, q.params)
  }
}
