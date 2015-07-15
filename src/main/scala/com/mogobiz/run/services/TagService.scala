package com.mogobiz.run.services

import com.mogobiz.run.config.DefaultComplete
import com.mogobiz.run.config.MogobizHandlers._
import com.mogobiz.run.implicits.Json4sProtocol
import Json4sProtocol._
import org.json4s._
import spray.http.StatusCodes
import spray.routing.Directives


class TagService extends Directives with DefaultComplete {

  val route = {
    pathPrefix(Segment / "tags") { storeCode =>
      pathEnd {
        get {
          parameters('hidden ? false, 'inactive ? false, 'lang ? "_all", 'size.as[Option[Int]]) {
            (hidden, inactive, lang, size) =>
              handleCall(tagHandler.queryTags(storeCode, hidden, inactive, lang, size), (json: JValue) => complete(StatusCodes.OK, json))
          }
        }
      }
    }
  }
  /*
  lazy val tags = pathEnd {
    get {
      parameters('hidden ? false, 'inactive ? false, 'lang ? "_all", 'size.as[Option[Int]]) {
        (hidden, inactive, lang, size) =>
          handleCall(tagHandler.queryTags(storeCode, hidden, inactive, lang, size), (json: JValue) => complete(StatusCodes.OK, json))
      }
    }
  }*/
}
