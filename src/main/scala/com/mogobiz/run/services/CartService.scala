package com.mogobiz.run.services

import com.mogobiz.pay.implicits.Implicits
import com.mogobiz.pay.implicits.Implicits.MogopaySession
import com.mogobiz.run.implicits.Json4sProtocol
import Json4sProtocol._
import com.mogobiz.run.model.RequestParameters._
import com.mogobiz.session.Session
import spray.http.StatusCodes
import spray.routing.Directives
import com.mogobiz.session.SessionESDirectives._
import com.mogobiz.run.config.HandlersConfig._

class CartService(storeCode: String, uuid: String) extends Directives with DefaultComplete {

  val route = {
    pathPrefix("cart") {
      cart ~
        coupon ~
        payment
    }
  }

  lazy val cart = {
    cartInit ~
      cartClear ~
      cartAdd ~
      cartUpdateRemove ~
      cartValidate
  }

  lazy val cartInit = pathEnd {
    get {
      parameters('currency.?, 'country.?, 'state.?, 'lang ? "_all").as(CartParameters) {
        params => {
          optionalSession { optSession =>
            val accountId = optSession.flatMap { session: Session => session.sessionData.accountId}
            handleCall(cartHandler.queryCartInit(storeCode, uuid, params, accountId), (res: Map[String, Any]) => complete(StatusCodes.OK, res))
          }
        }
      }
    }
  }

  lazy val cartClear = pathEnd {
    delete {
      parameters('currency.?, 'country.?, 'state.?, 'lang ? "_all").as(CartParameters) {
        params => {
          optionalSession { optSession =>
            val accountId = optSession.flatMap { session: Session => session.sessionData.accountId}
            handleCall(cartHandler.queryCartClear(storeCode, uuid, params, accountId), (res: Map[String, Any]) => complete(StatusCodes.OK, res))
          }
        }
      }
    }
  }

  lazy val cartAdd = path("items") {
    post {
      parameters('currency.?, 'country.?, 'state.?, 'lang ? "_all").as(CartParameters) {
        params =>
          entity(as[AddCartItemRequest]) {
            cmd => {
              optionalSession {
                optSession =>
                  val accountId = optSession.flatMap { session: Session => session.sessionData.accountId}
                  handleCall(cartHandler.queryCartItemAdd(storeCode, uuid, params, cmd, accountId), (res: Map[String, Any]) => complete(StatusCodes.OK, res))
              }
            }
          }
      }
    }
  }

  lazy val cartUpdateRemove = path("item" / Segment) {
    cartItemId => pathEnd {
      cartUpdate(cartItemId) ~
        cartRemove(cartItemId)
    }
  }

  def cartUpdate(cartItemId: String) = put {
    parameters('currency.?, 'country.?, 'state.?, 'lang ? "_all").as(CartParameters) {
      params => {
        entity(as[UpdateCartItemRequest]) {
          cmd => {
            optionalSession { optSession =>
              val accountId = optSession.flatMap { session: Session => session.sessionData.accountId}
              handleCall(cartHandler.queryCartItemUpdate(storeCode, uuid, cartItemId, params, cmd, accountId), (res: Map[String, Any]) => complete(StatusCodes.OK, res))
            }
          }
        }
      }
    }
  }

  def cartRemove(cartItemId: String) = delete {
    parameters('currency.?, 'country.?, 'state.?, 'lang ? "_all").as(CartParameters) {
      params => {
        optionalSession { optSession =>
          val accountId = optSession.flatMap { session: Session => session.sessionData.accountId}
          handleCall(cartHandler.queryCartItemRemove(storeCode, uuid, cartItemId, params, accountId), (res: Map[String, Any]) => complete(StatusCodes.OK, res))
        }
      }
    }
  }

  lazy val cartValidate = path("validate") {
    post {
      parameters('currency.?, 'country.?, 'state.?, 'lang ? "_all").as(CartParameters) {
        params => {
          optionalSession { optSession =>
            val accountId = optSession.flatMap { session: Session => session.sessionData.accountId}
            handleCall(cartHandler.queryCartValidate(storeCode, uuid, params, accountId), (res: Map[String, Any]) => complete(StatusCodes.OK, res))
          }
        }
      }
    }
  }

  lazy val coupon = path("coupons" / Segment) {
    couponCode => {
      pathEnd {
        couponAdd(couponCode) ~
          couponRemove(couponCode)
      }
    }
  }

  def couponAdd(couponCode: String) = post {
    parameters('currency.?, 'country.?, 'state.?, 'lang ? "_all").as(CouponParameters) {
      params => {
        optionalSession { optSession =>
          val accountId = optSession.flatMap { session: Session => session.sessionData.accountId}
          handleCall(cartHandler.queryCartCouponAdd(storeCode, uuid, couponCode, params, accountId), (res: Map[String, Any]) => complete(StatusCodes.OK, res))
        }
      }
    }
  }

  def couponRemove(couponCode: String) = delete {
    parameters('currency.?, 'country.?, 'state.?, 'lang ? "_all").as(CouponParameters) {
      params => {
        optionalSession { optSession =>
          val accountId = optSession.flatMap { session: Session => session.sessionData.accountId}
          handleCall(cartHandler.queryCartCouponDelete(storeCode, uuid, couponCode, params, accountId), (res: Map[String, Any]) => complete(StatusCodes.OK, res))
        }
      }
    }
  }

  lazy val payment = pathPrefix("payment") {
    post {
      paymentPrepare ~
        paymentCommit ~
        paymentCancel
    }
  }

  lazy val paymentPrepare = path("prepare") {
    entity(as[PrepareTransactionParameters]) { params =>
      optionalSession { optSession =>
        val accountId = optSession.flatMap { session: Session => session.sessionData.accountId}
        handleCall(cartHandler.queryCartPaymentPrepare(storeCode, uuid, params, accountId), (res: Map[String, Any]) => complete(StatusCodes.OK, res))
      }
    }
  }

  lazy val paymentCommit = path("commit") {
    entity(as[CommitTransactionParameters]) { params =>
      optionalSession { optSession =>
        val accountId = optSession.flatMap { session: Session => session.sessionData.accountId}
        handleCall(cartHandler.queryCartPaymentCommit(storeCode, uuid, params, accountId), (res: Unit) => complete(StatusCodes.OK))
      }
    }
  }

  lazy val paymentCancel = path("cancel") {
    entity(as[CancelTransactionParameters]) {  params =>
        optionalSession { optSession =>
          val accountId = optSession.flatMap { session: Session => session.sessionData.accountId}
          handleCall(cartHandler.queryCartPaymentCancel(storeCode, uuid, params, accountId), (res: Map[String, Any]) => complete(StatusCodes.OK, res))
        }
      }
    }
  }
}
