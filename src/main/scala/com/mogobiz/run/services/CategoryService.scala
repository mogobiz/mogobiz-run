/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import java.net.URLDecoder

import com.mogobiz.run.config.DefaultComplete
import com.mogobiz.run.config.MogobizHandlers.handlers._
import com.mogobiz.run.implicits.Json4sProtocol
import Json4sProtocol._
import org.json4s._
import spray.http.StatusCodes
import spray.routing.Directives

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
                     'size.as[Option[Int]]) { (hidden, parentId, brandId, categoryPath, lang, promotionId, size) =>
            handleCall(categoryHandler
                         .queryCategories(storeCode, hidden, parentId, brandId, categoryPath, lang, promotionId, size),
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
