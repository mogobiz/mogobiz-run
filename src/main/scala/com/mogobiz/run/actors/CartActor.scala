package com.mogobiz.run.actors

import akka.actor.Actor
import com.mogobiz.pay.model.Mogopay
import com.mogobiz.run.actors.CartActor._
import com.mogobiz.run.cart.{AddToCartCommand, UpdateCartItemCommand}
import com.mogobiz.run.config.HandlersConfig
import HandlersConfig._
import com.mogobiz.run.model.RequestParameters._
import com.mogobiz.run.model._

object CartActor {

  case class QueryCartInitRequest(storeCode: String, uuid: String, params: CartParameters, accountId:Option[Mogopay.Document])
  case class QueryCartClearRequest(storeCode: String, uuid: String, params: CartParameters, accountId:Option[Mogopay.Document])
  case class QueryCartItemAddRequest(storeCode: String, uuid: String, params: CartParameters, cmd: AddToCartCommand, accountId:Option[Mogopay.Document])
  case class QueryCartItemUpdateRequest(storeCode: String, uuid: String, cartItemId: String, params: CartParameters, cmd: UpdateCartItemCommand, accountId:Option[Mogopay.Document])
  case class QueryCartItemRemoveRequest(storeCode: String, uuid: String, cartItemId: String, params: CartParameters, accountId:Option[Mogopay.Document])
  case class QueryCartCouponAddRequest(storeCode: String, uuid: String, couponCode: String, params: CouponParameters, accountId:Option[Mogopay.Document])
  case class QueryCartCouponDeleteRequest(storeCode: String, uuid: String, couponCode: String, params: CouponParameters, accountId:Option[Mogopay.Document])
  case class QueryCartPaymentPrepareRequest(storeCode: String, uuid: String, params: PrepareTransactionParameters, accountId:Option[Mogopay.Document])
  case class QueryCartPaymentCommitRequest(storeCode: String, uuid: String, params: CommitTransactionParameters, accountId:Option[Mogopay.Document])
  case class QueryCartPaymentCancelRequest(storeCode: String, uuid: String, params: CancelTransactionParameters, accountId:Option[Mogopay.Document])
  case class QueryCartValidateRequest(storeCode: String, uuid: String, params: CartParameters, accountId:Option[Mogopay.Document])

}

class CartActor extends Actor {
  def receive = {
    case q: QueryCartInitRequest =>
      sender ! cartHandler.queryCartInit(q.storeCode,q.uuid, q.params, q.accountId)

    case q: QueryCartClearRequest =>
      sender ! cartHandler.queryCartClear(q.storeCode,q.uuid, q.params, q.accountId)

    case q: QueryCartItemAddRequest =>
      sender ! cartHandler.queryCartItemAdd(q.storeCode,q.uuid, q.params, q.cmd, q.accountId)

    case q: QueryCartItemUpdateRequest =>
      sender ! cartHandler.queryCartItemUpdate(q.storeCode,q.uuid, q.cartItemId, q.params, q.cmd, q.accountId)

    case q: QueryCartItemRemoveRequest =>
      sender ! cartHandler.queryCartItemRemove(q.storeCode,q.uuid, q.cartItemId, q.params, q.accountId)

    case q: QueryCartCouponAddRequest =>
      sender ! cartHandler.queryCartCouponAdd(q.storeCode,q.uuid, q.couponCode, q.params, q.accountId)

    case q: QueryCartCouponDeleteRequest =>
      sender ! cartHandler.queryCartCouponDelete(q.storeCode,q.uuid, q.couponCode, q.params, q.accountId)

    case q: QueryCartPaymentPrepareRequest =>
      sender ! cartHandler.queryCartPaymentPrepare(q.storeCode,q.uuid, q.params, q.accountId)

    case q: QueryCartPaymentCommitRequest =>
      sender ! cartHandler.queryCartPaymentCommit(q.storeCode,q.uuid, q.params, q.accountId)

    case q: QueryCartPaymentCancelRequest =>
      sender ! cartHandler.queryCartPaymentCancel(q.storeCode,q.uuid, q.params, q.accountId)

    case q: QueryCartValidateRequest =>
      sender ! cartHandler.queryCartValidate(q.storeCode,q.uuid, q.params, q.accountId)
  }
}
