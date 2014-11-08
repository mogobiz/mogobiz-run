package com.mogobiz.run.services

import akka.actor.ActorRef
import com.mogobiz.run.actors.CartActor._
import com.mogobiz.run.cart.{AddToCartCommand, UpdateCartItemCommand}
import com.mogobiz.run.implicits.Json4sProtocol
import Json4sProtocol._
import com.mogobiz.run.model._
import spray.routing.Directives

import scala.concurrent.ExecutionContext


class CartService(storeCode: String, uuid: String, actor: ActorRef)(implicit executionContext: ExecutionContext) extends Directives {

  import akka.pattern.ask
  import akka.util.Timeout

import scala.concurrent.duration._

  implicit val timeout = Timeout(2.seconds)

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
      cartUpdateRemove
  }

  lazy val cartInit = pathEnd {
    get {
      parameters('currency.?, 'country.?, 'lang ? "_all").as(CartParameters) {
        params => {
          val request = QueryCartInitRequest(storeCode, uuid, params)
          complete {
            (actor ? request).mapTo[Map[String, Any]] map {
              response => response
            }
          }
        }
      }
    }
  }

  lazy val cartClear = pathEnd {
    delete {
      parameters('currency.?, 'country.?, 'lang ? "_all").as(CartParameters) {
        params => {
          val request = QueryCartClearRequest(storeCode, uuid, params)
          complete {
            (actor ? request).mapTo[Map[String, Any]] map {
              response => response
            }
          }
        }
      }
    }
  }

  lazy val cartAdd = path("items") {
    post {
      parameters('currency.?, 'country.?, 'lang ? "_all").as(CartParameters) {
        params =>
          entity(as[AddToCartCommand]) {
            cmd => {
              val request = QueryCartItemAddRequest(storeCode, uuid, params, cmd)
              complete {
                (actor ? request).mapTo[Map[String, Any]] map {
                  response => response
                }
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
    parameters('currency.?, 'country.?, 'lang ? "_all").as(CartParameters) {
      params => {
        entity(as[UpdateCartItemCommand]) {
          cmd => {
            val request = QueryCartItemUpdateRequest(storeCode, uuid, cartItemId, params, cmd)
            complete {
              (actor ? request).mapTo[Map[String, Any]] map {
                response => response
              }
            }
          }
        }
      }
    }
  }

  def cartRemove(cartItemId: String) = delete {
    parameters('currency.?, 'country.?, 'lang ? "_all").as(CartParameters) {
      params => {
        val request = QueryCartItemRemoveRequest(storeCode, uuid, cartItemId, params)
        complete {
          (actor ? request).mapTo[Map[String, Any]] map {
            response => response
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
    parameters('currency.?, 'country.?, 'lang ? "_all").as(CouponParameters) {
      params => {
        val request = QueryCartCouponAddRequest(storeCode, uuid, couponCode, params)
        complete {
          (actor ? request).mapTo[Map[String, Any]] map {
            response => response
          }
        }
      }
    }
  }

  def couponRemove(couponCode: String) = delete {
    parameters('currency.?, 'country.?, 'lang ? "_all").as(CouponParameters) {
      params => {
        val request = QueryCartCouponDeleteRequest(storeCode, uuid, couponCode, params)
        complete {
          (actor ? request).mapTo[Map[String, Any]] map {
            response => response
          }
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
    parameters('currency.?, 'country.?, 'state.?, 'lang ? "_all", 'buyer).as(PrepareTransactionParameters) {
      params => {
        val request = QueryCartPaymentPrepareRequest(storeCode, uuid, params)
        complete {
          (actor ? request).mapTo[Map[String, Any]] map {
            response => response
          }
        }
      }
    }
  }

  lazy val paymentCommit = path("commit") {
    parameters('transactionUuid).as(CommitTransactionParameters) {
      params => {
        val request = QueryCartPaymentCommitRequest(storeCode, uuid, params)
        complete {
          (actor ? request).mapTo[Map[String, Any]] map {
            response => response
          }
        }
      }
    }
  }

  lazy val paymentCancel = path("cancel") {
    parameters('currency.?, 'country.?, 'lang ? "_all").as(CancelTransactionParameters) {
      params => {
        val request = QueryCartPaymentCancelRequest(storeCode, uuid, params)
        complete {
          (actor ? request).mapTo[Map[String, Any]] map {
            response => response
          }
        }
      }
    }
  }

}
