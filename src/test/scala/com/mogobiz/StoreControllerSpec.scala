package com.mogobiz

import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest

/**
 * Created by Christophe on 17/02/14.
 */
class StoreControllerSpec extends Specification with Specs2RouteTest with StoreService {

  def actorRefFactory = system

  "return list of lang codes" in {
    Get("/langs") ~> storeRoutes ~> check {
      responseAs[String] must contain("fr") //["fr","en","de","it","es"]
    }
  }

  "return brands " in {
    Get("/brands") ~> storeRoutes ~> check {
      responseAs[String] must contain("nike")
    }
  }

  "return tags " in {
    Get("/tags?category=10&lang=fr") ~> storeRoutes ~> check {
      responseAs[String] must contain("chaussure")
    }
  }

  "return products " in {
    Get("/products?lang=fr&store=companycode-6&currency=euro&country=fr") ~> storeRoutes ~> check {
      responseAs[String] must contain("Nike")
    }
  }

  "return products " in {
    Get("/find?lang=fr&store=companycode-2&query=imprimante") ~> storeRoutes ~> check {
      responseAs[String] must contain("imprimante")
    }
  }

  "return countries " in {
    Get("/countries?store=mogobiz&lang=fr") ~> storeRoutes ~> check {
      responseAs[String] must contain("France")
    }
  }

  "return currencies " in {
    Get("/currencies?store=mogobiz") ~> storeRoutes ~> check {
      responseAs[String] must contain("EUR")
    }
  }

  "return categories " in {
    Get("/categories?lang=fr&store=mogobiz") ~> storeRoutes ~> check {
      responseAs[String] must contain("Cinéma")
    }
  }

  "return product details" in {
    Get("/productDetails?productId=1&visitorId=2&storeCode&currencyCode=4&countryCode=5&lang=FR") ~> storeRoutes ~> check {
      responseAs[String] must contain("Nike Air")
    }
  }
}
