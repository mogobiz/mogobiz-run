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

  "return tags " in {
    Get("/tags?category=10&lang=fr&store=companycode-5") ~> allRoutes ~> check {
      responseAs[String] must contain("chaussure")
    }
  }


  "return countries " in {
    Get("/countries?store=mogobiz&lang=fr") ~> allRoutes ~> check {
      responseAs[String] must contain("France")
    }
  }

  "return currencies " in {
    Get("/currencies?store=mogobiz") ~> allRoutes ~> check {
      responseAs[String] must contain("EUR")
    }
  }

  "return categories " in {
    Get("/categories?lang=fr&store=mogobiz") ~> allRoutes ~> check {
      responseAs[String] must contain("Cinéma")
    }
  }
}
