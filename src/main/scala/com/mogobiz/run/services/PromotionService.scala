/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives
import com.mogobiz.run.config.DefaultComplete
import com.mogobiz.run.config.MogobizHandlers.handlers._
import com.mogobiz.run.model.RequestParameters.PromotionRequest
import de.heikoseeberger.akkahttpjson4s.Json4sSupport._
import com.mogobiz.run.implicits.Json4sProtocol
import com.mogobiz.json.JacksonConverter._
import org.json4s.JValue

class PromotionService extends Directives with DefaultComplete {

  val route = {
    pathPrefix(Segment / "promotions") { storeCode =>
      pathEnd {
        get {
          parameters('maxItemPerPage.as[Int].?,
                     'pageOffset.as[Int].?,
                     'orderBy.?,
                     'orderDirection.?,
                     'categoryPath.?,
                     'lang ? "_all")
            .as(PromotionRequest) { params =>
              handleCall(promotionHandler.getPromotions(storeCode, params),
                         (json: JValue) => complete(StatusCodes.OK, json))
            }
        }
      } ~
        pathPrefix(Segment) { promotionId =>
          get {
            handleCall(promotionHandler.getPromotionById(storeCode,
                                                         promotionId),
                       (json: JValue) => complete(StatusCodes.OK, json))
          }
        }
    }
  }

  /*
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
  }*/
}
