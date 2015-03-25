package com.mogobiz.run.services

import com.mogobiz.run.config.HandlersConfig._
import com.mogobiz.run.implicits.Json4sProtocol
import com.mogobiz.run.model.RequestParameters.{UpdateBOReturnedItemRequest, CreateBOReturnedItemRequest, BOListCustomersRequest, BOListOrdersRequest}
import com.mogobiz.run.utils.{PagingParams, Paging}
import com.mogobiz.session.Session
import com.mogobiz.session.SessionESDirectives._
import org.json4s.JsonAST.JValue
import spray.http.StatusCodes
import spray.routing.Directives
import com.mogobiz.pay.implicits.Implicits.MogopaySession
import Json4sProtocol._

class BackofficeService(storeCode: String) extends Directives with DefaultComplete {

  val route = {
    optionalSession { optSession =>
      val accountUuid = optSession.flatMap { session: Session => session.sessionData.accountId}
      pathPrefix("backoffice") {
        path("listOrders") {
          get {
            listOrders(accountUuid)
          }
        } ~
        path("listCustomers") {
          get {
            listCustomers(accountUuid)
          }
        } ~
        pathPrefix("cartDetails" / Segment) { transactionUuid =>
          pathEnd {
            get {
              cartDetails(accountUuid, transactionUuid)
            }
          } ~
          pathPrefix(Segment) { boCartItemUuid =>
            pathEnd {
              post {
                createBoReturnedItem(accountUuid, transactionUuid, boCartItemUuid)
              }
            } ~
            path(Segment) { boReturnedUuid =>
              put {
                updateBoReturnedItem(accountUuid, transactionUuid, boCartItemUuid, boReturnedUuid)
              }
            }
          }
        }
      }
    }
  }

  def listOrders(accountUuid: Option[String]) = parameters(
    'maxItemPerPage ?,
    'pageOffset ?,
    'lastName.?,
    'email.?,
    'startDate.?,
    'endDate.?,
    'price.?,
    'transactionStatus.?,
    'deliveryStatus.?).as(BOListOrdersRequest) { req =>
      handleCall(backofficeHandler.listOrders(storeCode, accountUuid, req),
        (res : Paging[JValue]) => complete(StatusCodes.OK, res))
    }

  def listCustomers(accountUuid: Option[String]) = parameters(
    'maxItemPerPage ?,
    'pageOffset ?,
    'lastName.?,
    'email.?).as(BOListCustomersRequest) { req =>
      handleCall(backofficeHandler.listCustomers(storeCode, accountUuid, req),
        (res : Paging[JValue]) => complete(StatusCodes.OK, res))
    }

  def cartDetails(accountUuid: Option[String], transactionUuid: String) =
      handleCall(backofficeHandler.cartDetails(storeCode, accountUuid, transactionUuid),
        (res: JValue) => complete(StatusCodes.OK, res))

  def createBoReturnedItem(accountUuid: Option[String], transactionUuid: String, boCartItemUuid: String) =
    entity(as[CreateBOReturnedItemRequest]) { req =>
      handleCall(backofficeHandler.createBOReturnedItem(storeCode, accountUuid, transactionUuid, boCartItemUuid, req),
        (res: Unit) => complete(StatusCodes.OK))
    }

  def updateBoReturnedItem(accountUuid: Option[String], transactionUuid: String, boCartItemUuid: String, boReturnedUuid: String) =
    entity(as[UpdateBOReturnedItemRequest]) { req =>
      handleCall(backofficeHandler.updateBOReturnedItem(storeCode, accountUuid, transactionUuid, boCartItemUuid, boReturnedUuid, req),
        (res: Unit) => complete(StatusCodes.OK))
    }
}