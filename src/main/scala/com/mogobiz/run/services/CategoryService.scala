/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import com.mogobiz.run.config.DefaultComplete
import com.mogobiz.run.config.MogobizHandlers.handlers._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives
import org.json4s._
import de.heikoseeberger.akkahttpjson4s.Json4sSupport._

class CategoryService extends Directives with DefaultComplete {

  val route = {
    pathPrefix(Segment / "categories") { storeCode =>
      pathEnd {
        get {
          parameters('hidden ? false,
                     'parentId.?,
                     'brandId.?,
                     'categoryPath.?,
                     'lang ? "_all",
                     'promotionId.?,
                     'size.as[Option[Int]]) {
            (hidden,
             parentId,
             brandId,
             categoryPath,
             lang,
             promotionId,
             size) =>
              handleCall(categoryHandler
                           .queryCategories(storeCode,
                                            hidden,
                                            parentId,
                                            brandId,
                                            categoryPath,
                                            lang,
                                            promotionId,
                                            size),
                         (json: JValue) => complete(StatusCodes.OK, json))
          }
        }
      } ~
        pathPrefix(Segment) { categoryId =>
          get {
            handleCall(categoryHandler.queryCategory(storeCode, categoryId),
                       (json: JValue) => complete(StatusCodes.OK, json))
          }
        }
    }
  }

  /*
  lazy val categories = pathEnd {
    get {
      parameters('hidden ? false, 'parentId.?, 'brandId.?, 'categoryPath.?, 'lang ? "_all", 'promotionId ?, 'size.as[Option[Int]]) {
        (hidden, parentId, brandId, categoryPath, lang, promotionId, size) =>
          handleCall(categoryHandler.queryCategories(storeCode, hidden, parentId, brandId, categoryPath, lang, promotionId, size),
            (json: JValue) => complete(StatusCodes.OK, json))
      }
    }
  }*/
}
