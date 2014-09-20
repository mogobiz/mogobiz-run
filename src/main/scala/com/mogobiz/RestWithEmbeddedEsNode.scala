package com.mogobiz

import akka.io.IO
import com.mogobiz.actors.{BootedMogobizSystem, MogobizActors}
import com.mogobiz.config.Settings
import com.mogobiz.es.EmbeddedElasticSearchNode
import com.mogobiz.services.MogobizRoutes
import spray.can.Http

object RestWithEmbeddedEsNode extends App with BootedMogobizSystem with MogobizActors with MogobizRoutes with EmbeddedElasticSearchNode {
  start()
  IO(Http)(system) ! Http.Bind(routesServices, interface = Settings.Interface, port = Settings.Port)
}