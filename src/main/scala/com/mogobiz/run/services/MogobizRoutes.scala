package com.mogobiz.run.services

import java.util.UUID

import akka.actor.{Props}
import com.mogobiz.run.actors.{MogobizActors}
import com.mogobiz.run.config.Settings
import com.mogobiz.run.exceptions.MogobizException
import Settings._
import com.mogobiz.system.MogobizSystem

import spray.http.{StatusCodes, HttpCookie}
import spray.routing.{Directives, _}

import com.mogobiz.system.RoutedHttpService

import scala.util.{Success, Failure, Try}


trait MogobizRoutes extends Directives {
  this: MogobizActors with MogobizSystem =>

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
      new TagService(storeCode, tagActor).route ~
      new BrandService(storeCode, brandActor).route ~
      new LangService(storeCode, langActor).route ~
      new CountryService(storeCode, countriesActor).route ~
      new CurrencyService(storeCode, currencyActor).route ~
      new CategoryService(storeCode, categoryActor).route ~
      new ProductService(storeCode, uuid, productActor).route ~
      new PreferenceService(storeCode, uuid, preferenceActor).route ~
      new CartService(storeCode, uuid, cartActor).route ~
      new PromotionService(storeCode, promotionActor).route ~
      new WishlistService(storeCode, wishlistActor).route ~
      new FacetService(storeCode, facetActor).route ~
      new ResourceService(storeCode).route
  }


  def routesServices = system.actorOf(Props(new RoutedHttpService(routes)))
}


trait DefaultComplete {
  this : Directives =>
  def handleComplete[T](call: Try[Try[T]], handler: T => Route): Route = {
    import com.mogobiz.run.implicits.JsonSupport._
    call match {
      case Failure(t) => complete(StatusCodes.InternalServerError -> Map('error -> t.toString))
      case Success(res) =>
        res match {
          case Failure(t:MogobizException) => complete(t.code -> Map('error -> t.toString))
          case Success(id) => handler(id)
          case  Failure(t) => throw new Exception("Invalid Exception: " + t.getMessage,t)
        }
    }
  }
}