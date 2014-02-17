package com.mogobiz

import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import akka.actor.ActorRefFactory

/**
 * Created by Christophe on 17/02/14.
 */
class StoreControllerSpec  extends Specification with Specs2RouteTest with  StoreController {
  def actorRefFactory = system

  "return list of lang codes" in {
    Get("/langs") ~> allRoutes ~> check {
      responseAs[String] must contain("fr")//["fr","en","de","it","es"]
    }
  }

  "return brands " in {
    Get("/brands") ~> allRoutes ~> check {
      responseAs[String] must contain("nike")
    }
  }
}
