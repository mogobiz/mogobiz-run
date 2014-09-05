package com.mogobiz

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import com.mogobiz.config.Settings
import com.typesafe.config.ConfigFactory
import spray.can.Http
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._

object Boot extends App {
  private val config = ConfigFactory.load()

  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("on-spray-can")

  // create and start our service actor
  val service = system.actorOf(Props[ControllerActor],"mogobiz-services")


  implicit val timeout = Timeout(5.seconds)
  //start a new HTTP server on port 8082 with our service actor as the handler

  IO(Http) ? Http.Bind(service, interface = Settings.Interface, port = Settings.Port)
}


