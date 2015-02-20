package com.mogobiz.run.services

import com.mogobiz.run.config.HandlersConfig._
import com.mogobiz.run.implicits.Json4sProtocol
import com.mogobiz.run.model.RequestParameters.BOListOrderRequest
import com.mogobiz.run.utils.Paging
import com.mogobiz.session.Session
import com.mogobiz.session.SessionESDirectives._
import org.json4s.JsonAST.JValue
import spray.http.StatusCodes
import spray.routing.Directives
import com.mogobiz.pay.implicits.Implicits.MogopaySession
import Json4sProtocol._

/**
 * Created by yoannbaudy on 19/02/2015.
 */
class BackofficeService(storeCode: String) extends Directives with DefaultComplete {

  val route = {
    pathPrefix("backoffice") {
      listOrders
    }
  }

  lazy val listOrders = pathEnd {
    get {
      parameters(
        'maxItemPerPage ?,
        'pageOffset ?,
        'lastName.?,
        'email.?,
        'startDate.?,
        'endDate.?,
        'price.?,
        'transactionStatus.?,
        'deliveryStatus.?).as(BOListOrderRequest) { req =>
          optionalSession { optSession =>
            val accountUuid = optSession.flatMap { session: Session => session.sessionData.accountId}
            handleCall(backofficeHandler.listOrders(storeCode, accountUuid, req),
              (res : Paging[JValue]) => complete(StatusCodes.OK, res))
          }
      }
    }
  }
}