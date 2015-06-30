package com.mogobiz

import com.mogobiz.run.actors.ActorSystemLocator
import com.mogobiz.run.config.MogobizRoutes
import com.mogobiz.system.MogobizSystem
import org.specs2.mutable.Specification
import spray.routing._
import spray.testkit.Specs2RouteTest

/**
 * Created by Christophe on 15/06/2015.
 */
abstract class MogobizRouteMocked extends Specification with Specs2RouteTest with HttpService with MogobizRoutes with MogobizSystem {

  val STORE = "mogobiz"

  def actorRefFactory = system // connect the DSL to the test ActorSystem
  ActorSystemLocator(system)

}
