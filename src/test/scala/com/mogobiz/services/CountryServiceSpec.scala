/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.services

import com.mogobiz.MogobizRouteTest
import org.specs2.matcher._
import org.json4s.native.JsonParser
import org.json4s.JsonAST._

class CountryServiceSpec extends MogobizRouteTest {
  "The Country service" should {

    "return countries" in {
      Get("/store/" + STORE + "/countries") ~> sealRoute(routes) ~> check {
        val countries: List[JValue] = checkJArray(JsonParser.parse(responseAs[String]))
        countries must have size 0
      }
    }
  }
}
