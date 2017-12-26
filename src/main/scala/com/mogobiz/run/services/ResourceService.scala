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

class ResourceService(implicit settings: RoutingSettings,
                      refFactory: ActorRefFactory)
    extends Directives {

  val route = {
    pathPrefix(Segment / "resources" / Segment) { (storeCode, resourceId) =>
      resizeResource(storeCode, resourceId) ~
        resource(storeCode, resourceId)
    }
  }

  def resizeResource(storeCode: String, resourceId: String) =
    pathPrefix(Segment) { size =>
      get {
        resourceHandler.queryResource(storeCode, resourceId, Some(size)) match {
          case Some(path) => getFromFile(path)
          case None       => complete(StatusCodes.NotFound)
        }
      }
    }

  def resource(storeCode: String, resourceId: String) =
    get {
      resourceHandler.queryResource(storeCode, resourceId, None) match {
        case Some(path) => getFromFile(path)
        case None       => complete(StatusCodes.NotFound)
      }
    }
}
