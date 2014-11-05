package com.mogobiz

import akka.actor.Props
import akka.io.IO
import com.mogobiz.actors.{ActorSystemLocator, MogobizActors}
import com.mogobiz.config.Settings
import com.mogobiz.jobs.CleanCartJob
import com.mogobiz.mail.EmailService
import com.mogobiz.pay.config.{MogopayActors, MogopayRoutes}
import com.mogobiz.services.MogobizRoutes
import com.mogobiz.system.BootedMogobizSystem
import spray.can.Http
import com.mogobiz.system.RoutedHttpService

object RestAll extends App with BootedMogobizSystem with MogobizActors with MogobizRoutes with MogopayActors with MogopayRoutes {

  ActorSystemLocator(system)

  //init the email service with the system Actor
  EmailService(system, "emailService")

  //init jobs
  CleanCartJob.start(system)


  override val routes = super[MogobizRoutes].routes ~ super[MogopayRoutes].routes

  override val routesServices = system.actorOf(Props(new RoutedHttpService(routes)))

  IO(Http)(system) ! Http.Bind(routesServices, interface = Settings.Interface, port = Settings.Port)
}