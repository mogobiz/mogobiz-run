/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.config

import akka.actor.Props
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import com.mogobiz.pay.exceptions.Exceptions.{
  MogopayException,
  MogopayMessagelessException
}
import com.mogobiz.run.exceptions.MogobizException
import com.mogobiz.run.jobs.CleanCartJob
import com.mogobiz.run.services._
import com.mogobiz.system.MogobizSystem
import com.mogobiz.utils.HttpComplete

import scala.util.{Failure, Success, Try}

trait MogobizRoutes extends Directives {
  this: MogobizSystem =>

  private implicit val _ = system.dispatcher

  def bootstrap(): Unit = {
    com.mogobiz.session.boot.DBInitializer()
    com.mogobiz.notify.boot.DBInitializer()
    CleanCartJob.start(system)
  }

  lazy val apiRoutes =
    new TagService().route ~
      new BrandService().route ~
      new LangService().route ~
      new CountryService().route ~
      new CurrencyService().route ~
      new CategoryService().route ~
      new PromotionService().route ~
      new WishlistService().route ~
      new FacetService().route ~
      new ResourceService().route ~
      new LogoService().route ~
      new BackofficeService().route ~
      new LearningService().route ~
      new ValidatorService().route ~
      new ProductService().route ~
      new SkuService().route ~
      new PreferenceService().route ~
      new CartService().route

  def routes =
    pathPrefix(("api" / "store") | "store") {
      pathEnd {
        complete("this is the root of all things")
      } ~ apiRoutes
    }
}

trait DefaultComplete extends HttpComplete {
  this: Directives =>
  override def completeException(t: Throwable): Route = {
    t match {
      case (ex: MogobizException) =>
        if (ex.printTrace) ex.printStackTrace()
        complete(
          ex.code -> Map('type -> ex.getClass.getSimpleName,
                         'error -> ex.getMessage))

      case (_) =>
        super.completeException(t)
    }
  }
}

trait MogolearnRoutes extends Directives {
  this: MogobizSystem =>

  private implicit val _ = system.dispatcher

  lazy val learningRoute = new LearningService().route

  def routes =
    logRequestResponse(showRequest _) {
      pathPrefix(("api" / "store") | "store") {
        pathEnd {
          complete("this is the root of all knowledge")
        }
      } ~ learningRoute
    }

  def routesServices = system.actorOf(Props(new RoutedHttpService(routes)))
}
