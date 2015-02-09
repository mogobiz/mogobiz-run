package com.mogobiz.run.services

import com.mogobiz.run.config.HandlersConfig._
import com.mogobiz.run.implicits.Json4sProtocol._
import com.mogobiz.run.model.RequestParameters.PromotionRequest
import org.json4s._
import spray.http.StatusCodes
import spray.routing.Directives

import scala.concurrent.ExecutionContext

class PromotionService(storeCode: String) extends Directives with DefaultComplete {

  val route = {
    pathPrefix("promotions") {
      promotions
    }
  }

  lazy val promotions = pathEnd {
    get {
      parameters(
        'maxItemPerPage.?
        , 'pageOffset.?
        , 'orderBy.?
        , 'orderDirection.?
        , 'categoryPath.?
        , 'lang ? "_all").as(PromotionRequest) { params =>
        handleCall(promotionHandler.getPromotions(storeCode, params), (json: JValue) => complete(StatusCodes.OK, json))
      }
    }
  }
}
