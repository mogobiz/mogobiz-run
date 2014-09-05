package com.mogobiz

import com.mogobiz.actors.{MogobizActors, BootedMogobizSystem}
import akka.io.IO
import com.mogobiz.config.Settings
import com.mogobiz.services.MogobizRoutes
import spray.can.Http

object Rest extends App with BootedMogobizSystem with MogobizActors with MogobizRoutes {
  override def main (args: Array[String]) {
    IO(Http)(system) ! Http.Bind(routesServices, interface = Settings.Interface, port = Settings.Port)
  }
}