package com.mogobiz.run.services

import akka.actor.ActorRefFactory
import com.mogobiz.run.config.HandlersConfig
import spray.http.StatusCodes
import spray.routing.{RoutingSettings, Directives}
import HandlersConfig._

import scala.concurrent.ExecutionContext

class ResourceService(storeCode: String)(implicit executionContext: ExecutionContext, settings:RoutingSettings, refFactory:ActorRefFactory) extends Directives {

  import akka.util.Timeout
  
  import scala.concurrent.duration._

  implicit val timeout = Timeout(2.seconds)

  val route = {
    pathPrefix("resources" / Segment) {resourceId =>
      resizeResource(resourceId) ~
      resource(resourceId)
    }
  }

  def resizeResource(resourceId:String) = pathPrefix(Segment) { size =>
    get{
      resourceHandler.queryResource(storeCode, resourceId, Some(size)) match {
        case Some(path) => getFromFile(path)
        case None => complete(StatusCodes.NotFound)
      }
    }
  }

  def resource(resourceId:String) =
    get{
      resourceHandler.queryResource(storeCode, resourceId, None) match {
        case Some(path) => getFromFile(path)
        case None => complete(StatusCodes.NotFound)
      }
    }
}
