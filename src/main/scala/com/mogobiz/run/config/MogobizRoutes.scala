package com.mogobiz.run.config

import akka.actor.Props
import com.mogobiz.run.exceptions.MogobizException
import com.mogobiz.run.services._
import com.mogobiz.system.{MogobizSystem, RoutedHttpService}
import spray.http.StatusCodes
import spray.routing.{Directives, _}

import scala.util.{Failure, Success, Try}


trait MogobizRoutes extends Directives {
  this: MogobizSystem =>

  private implicit val _ = system.dispatcher

  lazy val apiRoutes = new TagService().route ~
    new BrandService().route ~
    new LangService().route ~
    new CountryService().route ~
    new CurrencyService().route ~
    new CategoryService().route ~
    new PromotionService().route ~
    new WishlistService().route ~
    new FacetService().route ~
    new ResourceService().route ~
    new BackofficeService().route ~
    new LearningService().route ~
    new ValidatorService().route ~
    new ProductService().route ~
    new PreferenceService().route ~
    new CartService().route

  def routes =
    logRequestResponse(showRequest _) {
    pathPrefix(("api" / "store") | "store") {
      pathEnd {
        complete("this is the root of all things")
      } ~ apiRoutes
    }
  }

  def routesServices = system.actorOf(Props(new RoutedHttpService(routes)))
}


trait DefaultComplete {
  this : Directives =>
  def handleCall[T](call: => T, handler: T => Route): Route = {
    import com.mogobiz.run.implicits.JsonSupport._
    Try(call) match {
      case Failure(t: MogobizException) => t.printStackTrace(); complete(t.code -> Map('type -> t.getClass.getSimpleName, 'error -> t.toString))
      case Failure(t:Throwable) => t.printStackTrace();  complete(StatusCodes.InternalServerError -> Map('type -> t.getClass.getSimpleName, 'error -> t.toString))
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

trait MogolearnRoutes extends Directives {
  this: MogobizSystem =>

  private implicit val _ = system.dispatcher

  lazy val learningRoute = new LearningService().route

  def routes =
    logRequestResponse(showRequest _) {
      pathPrefix(("api" / "store") | "store"){
        pathEnd{complete("this is the root of all knowledge")}
      } ~ learningRoute
    }

  def routesServices = system.actorOf(Props(new RoutedHttpService(routes)))
}
