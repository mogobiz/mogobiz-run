/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz

import org.specs2.matcher._
import org.json4s.native.JsonParser
import org.json4s.JsonAST._

/**
 *
 */
class ProductSpec extends MogobizRouteTest{

  "The product service" should {

    "return products, categories, brands and tags for suggestions" in {
      Get("/store/" + STORE + "/products/find?query=hab") ~> sealRoute(routes) ~> check {
        val suggestions: List[JValue] = checkJArray(JsonParser.parse(responseAs[String]))
        suggestions must not be null
      }
    }

  }

}
