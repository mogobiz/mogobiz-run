/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import akka.http.scaladsl.model.StatusCodes
import com.mogobiz.run.config.DefaultComplete
import com.mogobiz.run.config.MogobizHandlers.handlers._
import akka.http.scaladsl.server.Directives
import org.json4s._
import de.heikoseeberger.akkahttpjson4s.Json4sSupport._
import com.mogobiz.run.implicits.Json4sProtocol
import com.mogobiz.json.JacksonConverter._

class TagService extends Directives with DefaultComplete {

  val route = {
    pathPrefix(Segment / "tags") { storeCode =>
      pathEnd {
        get {
          parameters('hidden ? false,
                     'inactive ? false,
                     'lang ? "_all",
                     'size.as[Option[Int]]) { (hidden, inactive, lang, size) =>
            handleCall(tagHandler.queryTags(storeCode,
                                            hidden,
                                            inactive,
                                            lang,
                                            size),
                       (json: JValue) => complete(StatusCodes.OK, json))
          }
        }
      } ~
        pathPrefix("id" / Segment) { tagId =>
          get {
            handleCall(tagHandler.queryTagId(storeCode, tagId),
                       (json: JValue) => complete(StatusCodes.OK, json))
          }
        } ~
        pathPrefix("name" / Segment) { tagName =>
          get {
            handleCall(tagHandler.queryTagName(storeCode, tagName),
                       (json: JValue) => complete(StatusCodes.OK, json))
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
