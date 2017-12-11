/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz

import com.mogobiz.json.JacksonConverter
import org.json4s.JValue

/**
  *
  */
class ProductSpec extends MogobizRouteTest {
  "The product service" should "return products, categories, brands and tags for suggestions" in {
    Get("/store/" + STORE + "/products/find?query=hab") ~> sealRoute(routes) ~> check {
      val suggestions: List[JValue] = checkJArray(JacksonConverter.parse(responseAs[String]))
      suggestions should not be null
    }
  }
}