/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.services

import com.mogobiz.MogobizRouteTest
import org.json4s.JsonAST._
import org.json4s.native.JsonParser

class LangServiceSpec extends MogobizRouteTest {
  "The Lang service" should "return langs" in {
      Get("/store/" + STORE + "/langs") ~> sealRoute(routes) ~> check {
        val langs: List[JValue] = checkJArray(JsonParser.parse(responseAs[String]))
        langs should have size 4

        langs(0) should be(JString("en"))
        langs(1) should be(JString("fr"))
        langs(2) should be(JString("es"))
        langs(3) should be(JString("de"))
      }
    }
  }
