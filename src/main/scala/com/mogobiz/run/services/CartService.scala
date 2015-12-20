/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import java.util.UUID

import com.mogobiz.pay.common.Cart
import com.mogobiz.pay.handlers.shipping.ShippingPrice
import com.mogobiz.pay.implicits.Implicits
import com.mogobiz.pay.implicits.Implicits.MogopaySession
import com.mogobiz.pay.model.ParamRequest.ListShippingPriceParam
import com.mogobiz.run.config.DefaultComplete
import com.mogobiz.run.config.Settings._
import com.mogobiz.run.implicits.Json4sProtocol
import Json4sProtocol._
import com.mogobiz.run.model.RequestParameters._
import com.mogobiz.session.Session
import spray.http.{ HttpCookie, StatusCodes }
import spray.routing.Directives
import com.mogobiz.session.SessionESDirectives._
import com.mogobiz.run.config.MogobizHandlers.handlers._

class CartService extends Directives with DefaultComplete {

  val route = {
    pathPrefix(Segment / "cart") { implicit storeCode =>
      optionalCookie(CookieTracking) {
        case Some(mogoCookie) =>
          transacRoutes(mogoCookie.content)
        case None =>
          val id = UUID.randomUUID.toString
          setCookie(HttpCookie(CookieTracking, content = id, path = Some("/api/store/" + storeCode))) {
            transacRoutes(id)
          }
      }

    }
  }

  def transacRoutes(uuid: String)(implicit storeCode: String) =
    cart(storeCode, uuid) ~
      coupon(storeCode, uuid) ~
      payment(storeCode, uuid) ~
      shippingPrices(storeCode, uuid)

  def cart(storeCode: String, uuid: String) = {
    cartInit(storeCode, uuid) ~
      cartClear(storeCode, uuid) ~
      cartAdd(storeCode, uuid) ~
      cartUpdateRemove(storeCode, uuid)
  }

  def cartInit(storeCode: String, uuid: String) = pathEnd {
    get {
      parameters('currency.?, 'country.?, 'state.?, 'lang ? "_all").as(CartParameters) {
        params =>
          {
            optionalSession { optSession =>
              val accountId = optSession.flatMap { session: Session => session.sessionData.accountId }
              handleCall(cartHandler.queryCartInit(storeCode, uuid, params, accountId), (res: Map[String, Any]) => complete(StatusCodes.OK, res))
            }
          }
      }
    }
  }

  def cartClear(storeCode: String, uuid: String) = pathEnd {
    delete {
      parameters('currency.?, 'country.?, 'state.?, 'lang ? "_all").as(CartParameters) {
        params =>
          {
            optionalSession { optSession =>
              val accountId = optSession.flatMap { session: Session => session.sessionData.accountId }
              handleCall(cartHandler.queryCartClear(storeCode, uuid, params, accountId), (res: Map[String, Any]) => complete(StatusCodes.OK, res))
            }
          }
      }
    }
  }

  def cartAdd(storeCode: String, uuid: String) = path("items") {
    post {
      parameters('currency.?, 'country.?, 'state.?, 'lang ? "_all").as(CartParameters) {
        params =>
          entity(as[AddCartItemRequest]) {
            cmd =>
              {
                optionalSession {
                  optSession =>
                    val accountId = optSession.flatMap { session: Session => session.sessionData.accountId }
                    handleCall(cartHandler.queryCartItemAdd(storeCode, uuid, params, cmd, accountId), (res: Map[String, Any]) => complete(StatusCodes.OK, res))
                }
              }
          }
      }
    }
  }

  def cartUpdateRemove(storeCode: String, uuid: String) = path("item" / Segment) {
    cartItemId =>
      pathEnd {
        cartUpdate(storeCode, uuid, cartItemId) ~
          cartRemove(storeCode, uuid, cartItemId)
      }
  }

  def cartUpdate(storeCode: String, uuid: String, cartItemId: String) = put {
    parameters('currency.?, 'country.?, 'state.?, 'lang ? "_all").as(CartParameters) {
      params =>
        {
          entity(as[UpdateCartItemRequest]) {
            cmd =>
              {
                optionalSession { optSession =>
                  val accountId = optSession.flatMap { session: Session => session.sessionData.accountId }
                  handleCall(cartHandler.queryCartItemUpdate(storeCode, uuid, cartItemId, params, cmd, accountId), (res: Map[String, Any]) => complete(StatusCodes.OK, res))
                }
              }
          }
        }
    }
  }

  def cartRemove(storeCode: String, uuid: String, cartItemId: String) = delete {
    parameters('currency.?, 'country.?, 'state.?, 'lang ? "_all").as(CartParameters) {
      params =>
        {
          optionalSession { optSession =>
            val accountId = optSession.flatMap { session: Session => session.sessionData.accountId }
            handleCall(cartHandler.queryCartItemRemove(storeCode, uuid, cartItemId, params, accountId), (res: Map[String, Any]) => complete(StatusCodes.OK, res))
          }
        }
    }
  }

  def coupon(storeCode: String, uuid: String) = path("coupons" / Segment) {
    couponCode =>
      {
        pathEnd {
          couponAdd(storeCode, uuid, couponCode) ~
            couponRemove(storeCode, uuid, couponCode)
        }
      }
  }

  def couponAdd(storeCode: String, uuid: String, couponCode: String) = post {
    parameters('currency.?, 'country.?, 'state.?, 'lang ? "_all").as(CouponParameters) {
      params =>
        {
          optionalSession { optSession =>
            val accountId = optSession.flatMap { session: Session => session.sessionData.accountId }
            handleCall(cartHandler.queryCartCouponAdd(storeCode, uuid, couponCode, params, accountId), (res: Map[String, Any]) => complete(StatusCodes.OK, res))
          }
        }
    }
  }

  def couponRemove(storeCode: String, uuid: String, couponCode: String) = delete {
    parameters('currency.?, 'country.?, 'state.?, 'lang ? "_all").as(CouponParameters) {
      params =>
        {
          optionalSession { optSession =>
            val accountId = optSession.flatMap { session: Session => session.sessionData.accountId }
            handleCall(cartHandler.queryCartCouponDelete(storeCode, uuid, couponCode, params, accountId), (res: Map[String, Any]) => complete(StatusCodes.OK, res))
          }
        }
    }
  }

  def payment(storeCode: String, uuid: String) = pathPrefix("payment") {
    post {
      paymentPrepare(storeCode, uuid) ~
        paymentCommit(storeCode, uuid) ~
        paymentCancel(storeCode, uuid)
    }
  }

  def paymentPrepare(storeCode: String, uuid: String) = path("prepare") {
    entity(as[PrepareTransactionParameters]) { params =>
      optionalSession { optSession =>
        val accountId = optSession.flatMap { session: Session => session.sessionData.accountId }
        handleCall(cartHandler.queryCartPaymentPrepare(storeCode, uuid, params, accountId), (res: Map[String, Any]) => complete(StatusCodes.OK, res))
      }
    }
  }

  def paymentCommit(storeCode: String, uuid: String) = path("commit") {
    entity(as[CommitTransactionParameters]) { params =>
      optionalSession { optSession =>
        val accountId = optSession.flatMap { session: Session => session.sessionData.accountId }
        handleCall(cartHandler.queryCartPaymentCommit(storeCode, uuid, params, accountId), (res: Unit) => complete(StatusCodes.OK))
      }
    }
  }

  def paymentCancel(storeCode: String, uuid: String) = path("cancel") {
    entity(as[CancelTransactionParameters]) { params =>
      optionalSession { optSession =>
        val accountId = optSession.flatMap { session: Session => session.sessionData.accountId }
        handleCall(cartHandler.queryCartPaymentCancel(storeCode, uuid, params, accountId), (res: Map[String, Any]) => complete(StatusCodes.OK, res))
      }
    }
  }

  def shippingPrices(storeCode: String, uuid: String) = path("list-shipping-prices") {
    get {
      parameters('cartProvider, 'cartKeys).as(ListShippingPriceParam) {
        params =>
          session {
            session =>
              import Implicits._
              session.sessionData.accountId.map(_.toString) match {
                case None => complete {
                  StatusCodes.Forbidden -> Map('error -> "Not logged in")
                }
                case Some(id) => {
                  val keys = params.cartKeys.split("\\|\\|\\|")
                  handleCall({
                    cartHandler.getCartForPay(keys(0), keys(1), if (!keys(2).isEmpty) Some(keys(2)) else None)
                  }, (cart: Cart) => {
                    session.sessionData.cart = Some(cart)
                    import com.mogobiz.pay.config.MogopayHandlers.transactionHandler
                    handleCall(transactionHandler.shippingPrices(cart, id),
                      (shippinggPrices: Seq[ShippingPrice]) => {
                        session.sessionData.shippingPrices = Option(shippinggPrices.toList)
                        setSession(session) {
                          complete(StatusCodes.OK -> shippinggPrices)
                        }
                      }
                    )
                  }
                  )
                }
              }
          }
      }
    }
  }
}
