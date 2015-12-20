/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import java.util.UUID

import com.mogobiz.run.config.DefaultComplete
import com.mogobiz.run.config.MogobizHandlers.handlers._
import com.mogobiz.run.config.Settings._
import com.mogobiz.run.implicits.JsonSupport
import JsonSupport._
import com.mogobiz.run.model.Prefs
import spray.http.{ HttpCookie, StatusCodes }
import spray.routing.Directives

import scala.util.{ Try, Failure, Success }

class PreferenceService extends Directives with DefaultComplete {

  val route = {
    pathPrefix(Segment / "prefs") { implicit storeCode =>
      optionalCookie(CookieTracking) {
        case Some(mogoCookie) =>
          preferencesRoutes(mogoCookie.content)
        case None =>
          val id = UUID.randomUUID.toString
          setCookie(HttpCookie(CookieTracking, content = id, path = Some("/api/store/" + storeCode))) {
            preferencesRoutes(id)
          }
      }
    }
  }

  def preferencesRoutes(uuid: String)(implicit storeCode: String) = getPrefs(uuid) ~ savePrefs(uuid)

  def getPrefs(uuid: String)(implicit storeCode: String) = get {
    handleCall(preferenceHandler.getPreferences(storeCode, uuid), (prefs: Prefs) => complete(StatusCodes.OK, prefs))
  }

  def savePrefs(uuid: String)(implicit storeCode: String) = post {
    parameters('productsNumber ? 10).as(Prefs) {
      params =>
        Try(preferenceHandler.savePreference(storeCode, uuid, params)) match {
          case Success(result) => complete(StatusCodes.OK -> Map("code" -> true))
          case Failure(result) => complete(StatusCodes.OK -> Map("code" -> false))
        }
    }
  }
}
