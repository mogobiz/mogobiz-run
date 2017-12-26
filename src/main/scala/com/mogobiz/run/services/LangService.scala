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
import com.mogobiz.run.implicits.Json4sProtocol
import com.mogobiz.json.JacksonConverter._

class LangService extends Directives with DefaultComplete {

  val route = {
    pathPrefix(Segment / "langs") { storeCode =>
      pathEnd {
        get {
          handleCall(langHandler.queryLang(storeCode),
                     (json: JValue) => complete(StatusCodes.OK, json))
        }
      }
    }
  }
  /*
  lazy val langs = pathEnd {
    get {
      handleCall(langHandler.queryLang(storeCode), (json: JValue) => complete(StatusCodes.OK, json))
    }
  }
 */
}
