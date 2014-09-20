package com.mogobiz.services

import java.util.UUID

import akka.actor.{Actor, ActorLogging, Props}
import com.mogobiz.actors.{MogobizActors, MogobizSystem}
import spray.http.StatusCodes._
import spray.http.{HttpCookie, HttpEntity, StatusCode}
import spray.routing.{Directives, _}
import spray.util.LoggingContext

import scala.util.control.NonFatal

trait MogobizRoutes extends Directives {
  this: MogobizActors with MogobizSystem =>

  private implicit val _ = system.dispatcher

  val routes = {
    pathPrefix("store" / Segment) {
      storeCode => {
        optionalCookie("mogobiz_uuid") {
          case Some(mogoCookie) =>
            println(s"mogoCookie=${mogoCookie.content}")
            storeRoutes(storeCode, mogoCookie.content)
          case None =>
            val id = UUID.randomUUID.toString
            println(s"new uuid=$id")
            setCookie(HttpCookie("mogobiz_uuid", content = id, path = Some("/store/" + storeCode))) {
              storeRoutes(storeCode, id)
            }
        }
      }
    }
  }

  def storeRoutes(storeCode: String, uuid: String) = {
    pathEnd {
      complete("the store code is " + storeCode)
    } ~
      new TagService(storeCode, tagActor).route ~
      new BrandService(storeCode, brandActor).route ~
      new LangService(storeCode, langActor).route ~
      new CountryService(storeCode, countryActor).route ~
      new CurrencyService(storeCode, currencyActor).route ~
      new CategoryService(storeCode, categoryActor).route ~
      new ProductService(storeCode, uuid, productActor).route ~
      new PreferenceService(storeCode, uuid, preferenceActor).route ~
      new CartService(storeCode, uuid, cartActor).route ~
      new PromotionService(storeCode, promotionActor).route
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
