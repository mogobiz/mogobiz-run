package com.mogobiz.run.services

import com.mogobiz.run.config.DefaultComplete
import com.mogobiz.run.config.MogobizHandlers._
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

class BackofficeService extends Directives with DefaultComplete {

  val route = {
    optionalSession { optSession =>
      val accountUuid = optSession.flatMap { session: Session => session.sessionData.accountId}
      pathPrefix(Segment / "backoffice") { implicit storeCode =>
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
                createBoReturnedItem(storeCode, accountUuid, transactionUuid, boCartItemUuid)
              }
            } ~
            path(Segment) { boReturnedUuid =>
              put {
                updateBoReturnedItem(storeCode, accountUuid, transactionUuid, boCartItemUuid, boReturnedUuid)
              }
            }
          }
        }
      }
    }
  }

  def listOrders(storeCode: String, accountUuid: Option[String])  = parameters(
    'maxItemPerPage.?,
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

  def listCustomers(storeCode: String, accountUuid: Option[String]) = parameters(
    'maxItemPerPage.?,
    'pageOffset.?,
    'lastName.?,
    'email.?).as(BOListCustomersRequest) { req =>
      handleCall(backofficeHandler.listCustomers(storeCode, accountUuid, req),
        (res : Paging[JValue]) => complete(StatusCodes.OK, res))
    }

  def cartDetails(storeCode: String, accountUuid: Option[String], transactionUuid: String) =
      handleCall(backofficeHandler.cartDetails(storeCode, accountUuid, transactionUuid),
        (res: JValue) => complete(StatusCodes.OK, res))

  def createBoReturnedItem(storeCode: String, accountUuid: Option[String], transactionUuid: String, boCartItemUuid: String) =
    entity(as[CreateBOReturnedItemRequest]) { req =>
      handleCall(backofficeHandler.createBOReturnedItem(storeCode, accountUuid, transactionUuid, boCartItemUuid, req),
        (res: Unit) => complete(StatusCodes.OK))
    }

  def updateBoReturnedItem(storeCode: String, accountUuid: Option[String], transactionUuid: String, boCartItemUuid: String, boReturnedUuid: String) =
    entity(as[UpdateBOReturnedItemRequest]) { req =>
      handleCall(backofficeHandler.updateBOReturnedItem(storeCode, accountUuid, transactionUuid, boCartItemUuid, boReturnedUuid, req),
        (res: Unit) => complete(StatusCodes.OK))
    }
}