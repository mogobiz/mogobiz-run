package com.mogobiz.services

import java.util.UUID

import com.mogobiz.actors.{MogobizActors, MogobizSystem}
import spray.routing._
import akka.actor.{ActorLogging, Actor, Props}
import scala.util.control.NonFatal
import spray.http.StatusCodes._
import spray.http.{HttpCookie, HttpEntity, StatusCode}
import spray.util.LoggingContext
import spray.routing.Directives

trait MogobizRoutes extends Directives {
  this: MogobizActors with MogobizSystem =>

  private implicit val _ = system.dispatcher

  val routes = {
    pathPrefix("store" / Segment) {
      storeCode => {
        optionalCookie("mogobiz_uuid") {
          case Some(mogoCookie) => {
            println(s"mogoCookie=${mogoCookie.content}")
            storeRoutes(storeCode, mogoCookie.content)
          }
          case None => {
            val id = UUID.randomUUID.toString
            println(s"new uuid=${id}")
            setCookie(HttpCookie("mogobiz_uuid", content = id, path = Some("/store/" + storeCode))) {
              storeRoutes(storeCode, id)
            }
          }
        }
      }
    }
  }

  def storeRoutes(storeCode: String, uuid: String) = {
    pathEnd {
      complete("the store code is " + storeCode)
    } ~
      new TagService(storeCode, tagActor).route
  }


  val routesServices = system.actorOf(Props(new RoutedHttpService(routes)))
}

/**
 *
 * @param responseStatus
 * @param response
 */
case class ErrorResponseException(responseStatus: StatusCode, response: Option[HttpEntity]) extends Exception

/**
 * Allows you to construct Spray ``HttpService`` from a concatenation of routes; and wires in the error handler.
 * It also logs all internal server errors using ``SprayActorLogging``.
 *
 * @param route the (concatenated) route
 */
class RoutedHttpService(route: Route) extends Actor with HttpService with ActorLogging {

  implicit def actorRefFactory = context

  implicit val handler = ExceptionHandler {
    case NonFatal(ErrorResponseException(statusCode, entity)) => ctx =>
      ctx.complete(statusCode, entity)

    case NonFatal(e) => ctx => {
      log.error(e, InternalServerError.defaultMessage)
      ctx.complete(InternalServerError)
    }
  }

  def receive: Receive =
    runRoute(route)(handler, RejectionHandler.Default, context, RoutingSettings.default, LoggingContext.fromActorRefFactory)
}
