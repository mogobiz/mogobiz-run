package com.mogobiz.actors

import akka.actor.{Props, Actor}
import com.mogobiz._
import com.mogobiz.actors.CartActor._
import com.mogobiz.cart.{UpdateCartItemCommand, AddToCartCommand}
import com.mogobiz.config.HandlersConfig._
import com.mogobiz.config.Settings
import com.mogobiz.mail.{EmailMessage, SmtpConfig, EmailService}
import com.mogobiz.utils.MailTemplateUtils
import org.joda.time.DateTime

import scala.concurrent.duration._

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

    case q: QueryCartInitRequest => {
      sender ! cartHandler.queryCartInit(q.storeCode,q.uuid, q.params)
    }
    case q: QueryCartClearRequest => {
      sender ! cartHandler.queryCartClear(q.storeCode,q.uuid, q.params)
    }
    case q: QueryCartItemAddRequest => {
      sender ! cartHandler.queryCartItemAdd(q.storeCode,q.uuid, q.params, q.cmd)
    }
    case q: QueryCartItemUpdateRequest => {
      sender ! cartHandler.queryCartItemUpdate(q.storeCode,q.uuid, q.cartItemId, q.params, q.cmd)
    }
    case q: QueryCartItemRemoveRequest => {
      sender ! cartHandler.queryCartItemRemove(q.storeCode,q.uuid, q.cartItemId, q.params)
    }
    case q: QueryCartCouponAddRequest => {
      sender ! cartHandler.queryCartCouponAdd(q.storeCode,q.uuid, q.couponCode, q.params)
    }
    case q: QueryCartCouponDeleteRequest => {
      sender ! cartHandler.queryCartCouponDelete(q.storeCode,q.uuid, q.couponCode, q.params)
    }
    case q: QueryCartPaymentPrepareRequest => {
      sender ! cartHandler.queryCartPaymentPrepare(q.storeCode,q.uuid, q.params)
    }
    case q: QueryCartPaymentCommitRequest => {
      val res: Map[String,Any] = cartHandler.queryCartPaymentCommit(q.storeCode,q.uuid, q.params)

      sender ! res

      val emailingData = res("data").asInstanceOf[List[Map[String,Any]]]

      val smtpConfig = SmtpConfig(
        ssl = Settings.Emailing.SMTP.IsSSLEnabled,
        port = Settings.Emailing.SMTP.Port,
        host = Settings.Emailing.SMTP.Hostname,
        user = Settings.Emailing.SMTP.Username,
        password = Settings.Emailing.SMTP.Password
      )
      val qrcodeBaseUrl : String = Settings.MogobizAdmin.QrCodeAccessUrl

      emailingData.foreach{ emailData => {

          val msg = EmailMessage(
            subject = "Your ticket : "+emailData("eventName"),
            recipient = emailData("email").asInstanceOf[Option[String]].getOrElse(""),
            from = Settings.Emailing.defaultFrom,

            html = Some(MailTemplateUtils.ticket(
              emailData("eventName").asInstanceOf[String],
              "",//emailData("startDate").asInstanceOf[Option[DateTime]].getOrElse(""),
              "",//emailData("stopDate").asInstanceOf[Option[DateTime]].getOrElse(""),
              emailData("location").asInstanceOf[String],
              emailData("price").asInstanceOf[Long].toString,
              emailData("type").asInstanceOf[Option[String]].getOrElse(""),
              qrcodeBaseUrl + emailData("qrcode").asInstanceOf[Option[String]].getOrElse("")
            )),
//            html = Some(MailTemplateUtils.ticket("","","","","","","")),

            smtpConfig = smtpConfig,
          retryOn = 5.minutes,
            deliveryAttempts=10
          )
          EmailService.send(msg)
        }
      }

    }
    case q: QueryCartPaymentCancelRequest => {
      sender ! cartHandler.queryCartPaymentCancel(q.storeCode,q.uuid, q.params)
    }
  }
}
