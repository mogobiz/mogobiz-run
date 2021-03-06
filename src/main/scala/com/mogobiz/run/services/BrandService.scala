/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import java.net.URLDecoder

import com.mogobiz.run.config.DefaultComplete
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives
import org.json4s._
import com.mogobiz.run.config.MogobizHandlers.handlers._
import de.heikoseeberger.akkahttpjson4s.Json4sSupport._

class BrandService extends Directives with DefaultComplete {

  import akka.util.Timeout

  import scala.concurrent.duration._

  implicit val timeout = Timeout(2.seconds)

  val route = {
    pathPrefix(Segment / "brands") { storeCode =>
      pathEnd {
        get {
          parameters('hidden ? false,
                     'categoryPath.?,
                     'lang ? "_all",
                     'promotionId.?,
                     'size.as[Option[Int]]) {
            (hidden, categoryPath, lang, promotionId, size) =>
              handleCall(brandHandler.queryBrands(storeCode,
                                                  hidden,
                                                  categoryPath,
                                                  lang,
                                                  promotionId,
                                                  size),
                         (json: JValue) => complete(StatusCodes.OK, json))
          }
        }
      } ~
        pathPrefix("id" / Segment) { brandId =>
          get {
            handleCall(brandHandler.queryBrandId(storeCode, brandId),
                       (json: JValue) => complete(StatusCodes.OK, json))
          }
        } ~
        pathPrefix("name" / Segment) { brandName =>
          get {
            handleCall(brandHandler.queryBrandName(storeCode,
                                                   URLDecoder.decode(brandName,
                                                                     "UTF-8")),
                       (json: JValue) => complete(StatusCodes.OK, json))
          }
        }
    }
  }
  /*
  lazy val brands = pathEnd {
    get {
      parameters('hidden ? false, 'categoryPath.?, 'lang ? "_all", 'promotionId.?, 'size.as[Option[Int]]) {
        (hidden, categoryPath, lang, promotionId, size) =>
          handleCall(brandHandler.queryBrands(storeCode, hidden, categoryPath, lang, promotionId, size),
            (json: JValue) => complete(StatusCodes.OK, json))
      }
    }
  }*/
}
