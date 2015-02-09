package com.mogobiz.run.services

import com.mogobiz.run.config.HandlersConfig._
import com.mogobiz.run.implicits.Json4sProtocol
import Json4sProtocol._
import org.json4s._
import spray.http.StatusCodes
import spray.routing.Directives


class CategoryService(storeCode: String) extends Directives with DefaultComplete {

  val route = {
    pathPrefix("categories") {
      categories
    }
  }

  lazy val categories = pathEnd {
    get {
      parameters('hidden ? false, 'parentId.?, 'brandId.?, 'categoryPath.?, 'lang ? "_all", 'promotionId ?, 'size.as[Option[Int]]) {
        (hidden, parentId, brandId, categoryPath, lang, promotionId, size) =>
          handleCall(categoryHandler.queryCategories(storeCode, hidden, parentId, brandId, categoryPath, lang, promotionId, size),
            (json: JValue) => complete(StatusCodes.OK, json))
      }
    }
  }
}
