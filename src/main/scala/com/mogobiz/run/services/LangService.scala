package com.mogobiz.run.services

import com.mogobiz.run.config.DefaultComplete
import com.mogobiz.run.config.HandlersConfig._
import com.mogobiz.run.implicits.Json4sProtocol
import Json4sProtocol._
import org.json4s._
import spray.http.StatusCodes
import spray.routing.Directives

class LangService extends Directives with DefaultComplete {

  val route = {
    pathPrefix(Segment / "langs") { storeCode =>
      pathEnd {
        get {
          handleCall(langHandler.queryLang(storeCode), (json: JValue) => complete(StatusCodes.OK, json))
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
