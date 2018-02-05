/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import java.util.UUID

import com.mogobiz.run.config.DefaultComplete
import com.mogobiz.run.config.MogobizHandlers.handlers._
import com.mogobiz.run.config.Settings._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.HttpCookie
import akka.http.scaladsl.server.Directives
import com.mogobiz.run.model.Prefs
import de.heikoseeberger.akkahttpjson4s.Json4sSupport._

import scala.util.{Failure, Success, Try}

class PreferenceService extends Directives with DefaultComplete {

  val route = {
    pathPrefix(Segment / "prefs") { implicit storeCode =>
      optionalCookie(CookieTracking) {
        case Some(mogoCookie) =>
          preferencesRoutes(mogoCookie.value)
        case None =>
          val id = UUID.randomUUID.toString
          setCookie(
            HttpCookie(CookieTracking,
                       value = id,
                       path = Some("/api/store/" + storeCode))) {
            preferencesRoutes(id)
          }
      }
    }
  }

  def preferencesRoutes(uuid: String)(implicit storeCode: String) =
    getPrefs(uuid) ~ savePrefs(uuid)

  def getPrefs(uuid: String)(implicit storeCode: String) = get {
    handleCall(preferenceHandler.getPreferences(storeCode, uuid),
               (prefs: Prefs) => complete(StatusCodes.OK, prefs))
  }

  def savePrefs(uuid: String)(implicit storeCode: String) = post {
    parameters('productsNumber ? 10) { params =>
      Try(preferenceHandler.savePreference(storeCode, uuid, Prefs(params))) match {
        case Success(result) => complete(StatusCodes.OK -> Map("code" -> true))
        case Failure(result) => complete(StatusCodes.OK -> Map("code" -> false))
      }
    }
  }
}
