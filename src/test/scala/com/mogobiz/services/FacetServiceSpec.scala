package com.mogobiz.services

import com.mogobiz.MogobizRouteTest
import org.specs2.matcher._
import org.json4s.native.JsonParser
import org.json4s.JsonAST._

class FacetServiceSpec extends MogobizRouteTest {


  "The Facet service" should {
    skipped("TODO update the ES Database coz mapping is deprecated")
    "return all facets" in {
      Get("/store/" + STORE + "/facets?priceInterval=5000") ~> sealRoute(routes) ~> check {
        //val res = JsonParser.parse(responseAs[String])
        val res = response
        println(res)
        res must be_==(true)
      }
    }
  }
}
