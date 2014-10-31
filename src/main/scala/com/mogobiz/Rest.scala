package com.mogobiz

import akka.io.IO
import com.mogobiz.actors.{ActorSystemLocator, BootedMogobizSystem, MogobizActors}
import com.mogobiz.config.Settings
import com.mogobiz.jobs.CleanCartJob
import com.mogobiz.mail.EmailService
import com.mogobiz.services.MogobizRoutes
import spray.can.Http

object Rest extends App with BootedMogobizSystem with MogobizActors with MogobizRoutes {

  ActorSystemLocator(system)

  //init the email service with the system Actor
  EmailService(system,"emailService")

  //init jobs
  CleanCartJob.start(system)

  IO(Http)(system) ! Http.Bind(routesServices, interface = Settings.Interface, port = Settings.Port)
}