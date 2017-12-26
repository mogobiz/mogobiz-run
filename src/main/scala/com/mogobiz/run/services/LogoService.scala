/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import akka.actor.ActorRefFactory
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.settings.RoutingSettings
import com.mogobiz.run.config.MogobizHandlers.handlers._
import de.heikoseeberger.akkahttpjson4s.Json4sSupport._
import com.mogobiz.run.implicits.Json4sProtocol
import com.mogobiz.json.JacksonConverter._

class LogoService(implicit settings: RoutingSettings,
                  refFactory: ActorRefFactory)
    extends Directives {

  val route = {
    pathPrefix(Segment / "logos" / Segment) { (storeCode, brandId) =>
      resizeLogo(storeCode, brandId) ~
        logo(storeCode, brandId)
    }
  }

  def resizeLogo(storeCode: String, brandId: String) = pathPrefix(Segment) {
    size =>
      get {
        logoHandler.queryLogo(storeCode, brandId, Some(size)) match {
          case Some(path) => getFromFile(path)
          case None       => complete(StatusCodes.NotFound)
        }
      }
  }

  def logo(storeCode: String, brandId: String) =
    get {
      logoHandler.queryLogo(storeCode, brandId, None) match {
        case Some(path) => getFromFile(path)
        case None       => complete(StatusCodes.NotFound)
      }
    }
}
