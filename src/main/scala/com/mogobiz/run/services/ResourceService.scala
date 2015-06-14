package com.mogobiz.run.services

import akka.actor.ActorRefFactory
import com.mogobiz.run.config.HandlersConfig
import spray.routing.{RoutingSettings, Directives}
import HandlersConfig._
import spray.http.StatusCodes

import scala.concurrent.ExecutionContext

class ResourceService (implicit settings:RoutingSettings, refFactory:ActorRefFactory) extends Directives {

  val route = {
    pathPrefix(Segment / "resources" / Segment) { (storeCode, resourceId) =>
      resizeResource(storeCode, resourceId) ~
      resource(storeCode, resourceId)
    }
  }

  def resizeResource(storeCode:String, resourceId:String) = pathPrefix(Segment) { size =>
    get{
      resourceHandler.queryResource(storeCode, resourceId, Some(size)) match {
        case Some(path) => getFromFile(path)
        case None => complete(StatusCodes.NotFound)
      }
    }
  }

  def resource(storeCode:String, resourceId:String) =
    get{
      resourceHandler.queryResource(storeCode, resourceId, None) match {
        case Some(path) => getFromFile(path)
        case None => complete(StatusCodes.NotFound)
      }
    }
}
