package com.mogobiz.run.services

import java.util.UUID

import akka.actor.{Props}
import com.mogobiz.run.config.Settings
import com.mogobiz.run.exceptions.MogobizException
import Settings._
import com.mogobiz.system.MogobizSystem

import spray.http.{StatusCodes, HttpCookie}
import spray.routing.{Directives, _}

import com.mogobiz.system.RoutedHttpService

import scala.util.{Success, Failure, Try}


trait MogobizRoutes extends Directives {
  this: MogobizSystem =>

  private implicit val _ = system.dispatcher

  def routes =
    logRequestResponse(showRequest _) {
    pathPrefix((("api" / "store") | "store") / Segment) {
      storeCode => {
        optionalCookie(CookieTracking) {
          case Some(mogoCookie) =>
            println(s"mogoCookie=${mogoCookie.content}")
            storeRoutes(storeCode, mogoCookie.content)
          case None =>
            val id = UUID.randomUUID.toString
            println(s"new uuid=$id")
            setCookie(HttpCookie(CookieTracking, content = id, path = Some("/store/" + storeCode))) {
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
      new TagService(storeCode).route ~
      new BrandService(storeCode).route ~
      new LangService(storeCode).route ~
      new CountryService(storeCode).route ~
      new CurrencyService(storeCode).route ~
      new CategoryService(storeCode).route ~
      new ProductService(storeCode, uuid).route ~
      new PreferenceService(storeCode, uuid).route ~
      new CartService(storeCode, uuid).route ~
      new PromotionService(storeCode).route ~
      new WishlistService(storeCode).route ~
      new FacetService(storeCode).route ~
      new ResourceService(storeCode).route ~
      new BackofficeService(storeCode).route ~
      new LearningService(storeCode).route
  }


  def routesServices = system.actorOf(Props(new RoutedHttpService(routes)))
}


trait DefaultComplete {
  this : Directives =>
  def handleCall[T](call: => T, handler: T => Route): Route = {
    import com.mogobiz.run.implicits.JsonSupport._
    Try(call) match {
      case Failure(t: MogobizException) => t.printStackTrace(); complete(t.code -> Map('type -> t.getClass.getSimpleName, 'error -> t.toString))
      case Failure(t) => t.printStackTrace(); complete(StatusCodes.InternalServerError -> Map('type -> t.getClass.getSimpleName, 'error -> t.toString))
      case Success(res) => handler(res)
    }
  }

  def handleComplete[T](call: Try[Try[T]], handler: T => Route): Route = {
    import com.mogobiz.run.implicits.JsonSupport._
    call match {
      case Failure(t) => t.printStackTrace(); complete(StatusCodes.InternalServerError -> Map('type -> t.getClass.getSimpleName, 'error -> t.toString))
      case Success(res) =>
        res match {
          case Success(id) => handler(id)
          case Failure(t:MogobizException) => t.printStackTrace(); complete(t.code -> Map('type -> t.getClass.getSimpleName, 'error -> t.toString))
          case Failure(t) => t.printStackTrace(); complete(StatusCodes.InternalServerError -> Map('type -> t.getClass.getSimpleName, 'error -> t.toString))
        }
    }
  }
}