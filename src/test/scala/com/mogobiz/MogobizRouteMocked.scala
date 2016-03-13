/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz

import com.mogobiz.run.config.MogobizRoutes
import com.mogobiz.system.{ActorSystemLocator, MogobizSystem}
import org.specs2.mutable.Specification
import spray.routing._
import spray.testkit.Specs2RouteTest

/**
 */
abstract class MogobizRouteMocked extends Specification with Specs2RouteTest with HttpService with MogobizRoutes with MogobizSystem {

  val STORE = "mogobiz"

  def actorRefFactory = system // connect the DSL to the test ActorSystem
  ActorSystemLocator(system)

}
