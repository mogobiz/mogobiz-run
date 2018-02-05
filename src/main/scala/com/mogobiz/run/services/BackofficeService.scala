/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives
import com.mogobiz.pay.implicits.Implicits.MogopaySession
import com.mogobiz.run.config.DefaultComplete
import com.mogobiz.run.config.MogobizHandlers.handlers._
import com.mogobiz.run.exceptions.SomeParameterIsMissingException
import de.heikoseeberger.akkahttpjson4s.Json4sSupport._

import com.mogobiz.run.model.RequestParameters.{
  BOListCustomersRequest,
  BOListOrdersRequest,
  CreateBOReturnedItemRequest,
  UpdateBOReturnedItemRequest
}

import com.mogobiz.run.utils.Paging
import com.mogobiz.session.SessionESDirectives._
import org.json4s.JValue

class BackofficeService extends Directives with DefaultComplete {

  val route = {
    optionalSession { optSession =>
      val accountUuid = optSession.flatMap(_.sessionData.accountId)
      val locale = optSession.flatMap(_.sessionData.locale)

      pathPrefix(Segment / "backoffice") { implicit storeCode =>
        path("shipping-webhook" / Segment) { webhookProvider =>
          post {
            entity(as[String]) { postData =>
              handleCall(backofficeHandler.shippingWebhook(storeCode,
                                                           webhookProvider,
                                                           postData),
                         (_: Unit) => {
                           complete(StatusCodes.OK)
                         })
            }
          }
        } ~
          path("listOrders") {
            get {
              listOrders(storeCode, accountUuid)
            }
          } ~
          path("listCustomers") {
            get {
              listCustomers(storeCode, accountUuid)
            }
          } ~
          pathPrefix("cartDetails" / Segment) { transactionUuid =>
            pathEnd {
              get {
                cartDetails(storeCode, accountUuid, transactionUuid)
              }
            } ~
              pathPrefix(Segment) { boCartItemUuid =>
                pathEnd {
                  post {
                    createBoReturnedItem(storeCode,
                                         accountUuid,
                                         transactionUuid,
                                         boCartItemUuid,
                                         locale)
                  }
                } ~
                  path(Segment) { boReturnedUuid =>
                    put {
                      updateBoReturnedItem(storeCode,
                                           accountUuid,
                                           transactionUuid,
                                           boCartItemUuid,
                                           boReturnedUuid,
                                           locale)
                    }
                  }
              }
          }
      }
    }
  }

  def listOrders(storeCode: String, accountUuid: Option[String]) =
    parameters('maxItemPerPage.as[Int].?,
               'pageOffset.as[Int].?,
               'lastName.?,
               'email.?,
               'startDate.?,
               'endDate.?,
               'price.?,
               'transactionStatus.?,
               'deliveryStatus.?).as(BOListOrdersRequest) { req =>
      handleCall(backofficeHandler.listOrders(storeCode, accountUuid, req),
                 (res: Paging[JValue]) => complete(StatusCodes.OK, res))
    }

  def listCustomers(storeCode: String, accountUuid: Option[String]) =
    parameters('maxItemPerPage.as[Int].?,
               'pageOffset.as[Int].?,
               'lastName.?,
               'email.?)
      .as(BOListCustomersRequest) { req =>
        handleCall(backofficeHandler.listCustomers(storeCode, accountUuid, req),
                   (res: Paging[JValue]) => complete(StatusCodes.OK, res))
      }

  def cartDetails(storeCode: String,
                  accountUuid: Option[String],
                  transactionUuid: String) =
    handleCall(
      backofficeHandler.cartDetails(storeCode, accountUuid, transactionUuid),
      (res: JValue) => complete(StatusCodes.OK, res))

  def createBoReturnedItem(storeCode: String,
                           accountUuid: Option[String],
                           transactionUuid: String,
                           boCartItemUuid: String,
                           locale: Option[String]) =
    entity(as[CreateBOReturnedItemRequest]) { req =>
      handleCall(
        backofficeHandler.createBOReturnedItem(
          storeCode,
          accountUuid.getOrElse(
            throw new SomeParameterIsMissingException("accountUuid not found")),
          transactionUuid,
          boCartItemUuid,
          req,
          locale),
        (_: Unit) => complete(StatusCodes.OK)
      )
    }

  def updateBoReturnedItem(storeCode: String,
                           accountUuid: Option[String],
                           transactionUuid: String,
                           boCartItemUuid: String,
                           boReturnedUuid: String,
                           locale: Option[String]) =
    entity(as[UpdateBOReturnedItemRequest]) { req =>
      handleCall(
        backofficeHandler.updateBOReturnedItem(
          storeCode,
          accountUuid.getOrElse(
            throw new SomeParameterIsMissingException("accountUuid not found")),
          transactionUuid,
          boCartItemUuid,
          boReturnedUuid,
          req,
          locale
        ),
        (_: Unit) => complete(StatusCodes.OK)
      )
    }
}
