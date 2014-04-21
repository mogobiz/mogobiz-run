package com.mogobiz

import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest

/**
 * Created by Christophe on 17/02/14.
 */
class StoreControllerSpec extends Specification with Specs2RouteTest with StoreService {

  def actorRefFactory = system

  val store = "mogobiz"

/*
  "return list of lang codes" in {
    Get("/store/"+store+"/langs") ~> storeRoutes ~> check {
      responseAs[String] must contain("fr") //["fr","en","de","it","es"]
    }
  }

  "return brands " in {
    Get("/store/"+store+"/brands?lang=fr") ~> storeRoutes ~> check {
      responseAs[String] must contain("nike")
    }
    Get("/store/"+store+"/brands?lang=fr") ~> storeRoutes ~> check {
      responseAs[String] must contain("http://www.samsung.com/fr")
    }
    Get("/store/"+store+"/brands?lang=en") ~> storeRoutes ~> check {
      responseAs[String] must contain("http://www.samsung.com")
    }
  }

  "return tags " in {
    Get("/store/"+store+"/tags?lang=fr") ~> storeRoutes ~> check {
      responseAs[String] must contain("CINEMA")
    }
  }

  "return products " in {
    Get("/store/"+store+"/products?lang=fr&store=companycode-6&currency=euro&country=fr") ~> storeRoutes ~> check {
      responseAs[String] must contain("Nike")
    }
  }

  "return find products criteria" in {
    Get("/store/"+store+"/find?lang=fr&store=companycode-2&query=imprimante") ~> storeRoutes ~> check {
      responseAs[String] must contain("imprimante")
    }
  }

  "return countries " in {
    Get("/store/"+store+"/countries?store=mogobiz&lang=fr") ~> storeRoutes ~> check {
      responseAs[String] must contain("France")
    }
  }

  "return currencies " in {
    Get("/store/"+store+"/currencies?store=mogobiz") ~> storeRoutes ~> check {
      responseAs[String] must contain("EUR")
    }
  }

  "return categories " in {

    Get("/store/"+store+"/categories?lang=fr") ~> storeRoutes ~> check {
      responseAs[String] must contain("Cinéma")
    }
    Get("/store/"+store+"/categories") ~> storeRoutes ~> check {
      responseAs[String] must contain("Televisions")
    }
    Get("/store/"+store+"/categories?hidden=true") ~> storeRoutes ~> check {
      responseAs[String] must contain("Cinéma")
    }
    Get("/store/"+store+"/categories?lang=fr&parentId=18") ~> storeRoutes ~> check {
      responseAs[String] must contain("Télévisions")
    }
    Get("/store/"+store+"/categories?parentId=18") ~> storeRoutes ~> check {
      responseAs[String] must contain("Televisions")
    }
    Get("/store/"+store+"/categories?parentId=18&hidden=true") ~> storeRoutes ~> check {
      responseAs[String] must contain("Televisions")
    }
    Get("/store/"+store+"/categories?parentId=18&hidden=true&lang=fr") ~> storeRoutes ~> check {
      responseAs[String] must contain("Télévisions")
    }
  }

  "return product details" in {
    Get("/store/"+store+"/productDetails?productId=1&visitorId=2&storeCode&currencyCode=4&countryCode=5&lang=FR") ~> storeRoutes ~> check {
      responseAs[String] must contain("Nike Air")
    }
  }
*/

}
