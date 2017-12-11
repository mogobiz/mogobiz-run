/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import com.mogobiz.{DefaultCompleteMocked, MogobizRouteTest}

/**
  */
class FacetServiceMockedSpec extends MogobizRouteTest {

  override lazy val apiRoutes = (new FacetService() with DefaultCompleteMocked).route

  " facet route " should " respond with default parameters " in {
    val path = "/store/" + STORE + "/facets?priceInterval=" + 1000
    Get(path) ~> sealRoute(routes) ~> check {
      status.intValue should be(200)
      status.isSuccess should be(true)
    }
  }
  //TODO test with optionals parameters
}
