/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import akka.actor.ActorRefFactory
import com.mogobiz.run.config.MogobizHandlers
import com.mogobiz.run.config.MogobizHandlers._
import spray.http.StatusCodes
import spray.routing.{Directives, RoutingSettings}

class LogoService (implicit settings:RoutingSettings, refFactory:ActorRefFactory) extends Directives {

  val route = {
    pathPrefix(Segment / "logos" / Segment) { (storeCode, brandId) =>
      resizeLogo(storeCode, brandId) ~
      logo(storeCode, brandId)
    }
  }

  def resizeLogo(storeCode:String, brandId:String) = pathPrefix(Segment) { size =>
    get{
      logoHandler.queryLogo(storeCode, brandId, Some(size)) match {
        case Some(path) => getFromFile(path)
        case None => complete(StatusCodes.NotFound)
      }
    }
  }

  def logo(storeCode:String, brandId:String) =
    get{
      logoHandler.queryLogo(storeCode, brandId, None) match {
        case Some(path) => getFromFile(path)
        case None => complete(StatusCodes.NotFound)
      }
    }
}
