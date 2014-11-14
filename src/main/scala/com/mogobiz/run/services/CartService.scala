package com.mogobiz.run.services

import akka.actor.ActorRef
import com.mogobiz.pay.implicits.Implicits.MogopaySession
import com.mogobiz.run.actors.CartActor._
import com.mogobiz.run.cart.{AddToCartCommand, UpdateCartItemCommand}
import com.mogobiz.run.implicits.Json4sProtocol
import Json4sProtocol._
import com.mogobiz.run.model._
import com.mogobiz.session.Session
import spray.routing.Directives
import com.mogobiz.session.SessionESDirectives._
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
      cartUpdateRemove ~
      cartValidate
  }

  lazy val cartInit = pathEnd {
    get {
      parameters('currency.?, 'country.?, 'lang ? "_all").as(CartParameters) { params => {
        optionalSession { optSession =>
          val accountId = optSession.flatMap { session: Session => session.sessionData.accountId}
          val request = QueryCartInitRequest(storeCode, uuid, params, accountId)
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

  lazy val cartClear = pathEnd {
    delete {
      parameters('currency.?, 'country.?, 'lang ? "_all").as(CartParameters) {
        params => {
          optionalSession { optSession =>
            val accountId = optSession.flatMap { session: Session => session.sessionData.accountId}
            val request = QueryCartClearRequest(storeCode, uuid, params, accountId)
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

  lazy val cartAdd = path("items") {
    post {
      parameters('currency.?, 'country.?, 'lang ? "_all").as(CartParameters) {
        params =>
          entity(as[AddToCartCommand]) {
            cmd => {
              optionalSession { optSession =>
                val accountId = optSession.flatMap { session: Session => session.sessionData.accountId}
                val request = QueryCartItemAddRequest(storeCode, uuid, params, cmd, accountId)
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
            optionalSession { optSession =>
              val accountId = optSession.flatMap { session: Session => session.sessionData.accountId}
              val request = QueryCartItemUpdateRequest(storeCode, uuid, cartItemId, params, cmd, accountId)
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
  }

  def cartRemove(cartItemId: String) = delete {
    parameters('currency.?, 'country.?, 'lang ? "_all").as(CartParameters) {
      params => {
        optionalSession { optSession =>
          val accountId = optSession.flatMap { session: Session => session.sessionData.accountId}
          val request = QueryCartItemRemoveRequest(storeCode, uuid, cartItemId, params, accountId)
          complete {
            (actor ? request).mapTo[Map[String, Any]] map {
              response => response
            }
          }
        }
      }
    }
  }

  lazy val cartValidate = path("validate") {
    post {
      parameters('currency.?, 'country.?, 'lang ? "_all").as(CartParameters) {
        params => {
          optionalSession { optSession =>
            val accountId = optSession.flatMap { session: Session => session.sessionData.accountId}
            val request = QueryCartValidateRequest(storeCode, uuid, params, accountId)
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
        optionalSession { optSession =>
          val accountId = optSession.flatMap { session: Session => session.sessionData.accountId}
          val request = QueryCartCouponAddRequest(storeCode, uuid, couponCode, params, accountId)
          complete {
            (actor ? request).mapTo[Map[String, Any]] map {
              response => response
            }
          }
        }
      }
    }
  }

  def couponRemove(couponCode: String) = delete {
    parameters('currency.?, 'country.?, 'lang ? "_all").as(CouponParameters) {
      params => {
        optionalSession { optSession =>
          val accountId = optSession.flatMap { session: Session => session.sessionData.accountId}
          val request = QueryCartCouponDeleteRequest(storeCode, uuid, couponCode, params, accountId)
          complete {
            (actor ? request).mapTo[Map[String, Any]] map {
              response => response
            }
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
        optionalSession { optSession =>
          val accountId = optSession.flatMap { session: Session => session.sessionData.accountId}
          val request = QueryCartPaymentPrepareRequest(storeCode, uuid, params, accountId)
          complete {
            (actor ? request).mapTo[Map[String, Any]] map {
              response => response
            }
          }
        }
      }
    }
  }

  lazy val paymentCommit = path("commit") {
    parameters('transactionUuid).as(CommitTransactionParameters) {
      params => {
        optionalSession { optSession =>
          val accountId = optSession.flatMap { session: Session => session.sessionData.accountId}
          val request = QueryCartPaymentCommitRequest(storeCode, uuid, params, accountId)
          complete {
            (actor ? request).mapTo[Map[String, Any]] map {
              response => response
            }
          }
        }
      }
    }
  }

  lazy val paymentCancel = path("cancel") {
    parameters('currency.?, 'country.?, 'lang ? "_all").as(CancelTransactionParameters) {
      params => {
        optionalSession { optSession =>
          val accountId = optSession.flatMap { session: Session => session.sessionData.accountId}
          val request = QueryCartPaymentCancelRequest(storeCode, uuid, params, accountId)
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
