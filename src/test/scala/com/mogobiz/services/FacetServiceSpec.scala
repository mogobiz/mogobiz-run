package com.mogobiz.services

import com.mogobiz.MogobizRouteTest
import org.specs2.matcher._
import org.json4s.native.JsonParser
import org.json4s.JsonAST._

class FacetServiceSpec extends MogobizRouteTest {

  "The Facet service" should {
    "return all facets" in {
      Get("/store/" + STORE + "/facets") ~> sealRoute(routes) ~> check {
        val response = JsonParser.parse(responseAs[String])
        println(response)
        response must be_==(true)
      }
    }
  }
}
