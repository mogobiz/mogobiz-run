package com.mogobiz.run.services

import com.mogobiz.{DefaultCompleteMocked, MogobizRouteMocked}

/**
 * Created by Christophe on 29/06/2015.
 */
class FacetServiceMockedSpec extends MogobizRouteMocked  {

  override lazy val apiRoutes = (new FacetService() with DefaultCompleteMocked).route

  " facet route " should {

    " respond with default parameters " in {
      val path = "/store/" + STORE + "/facets?priceInterval="+1000
      Get(path) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

    //TODO test with optionals parameters
  }
}
