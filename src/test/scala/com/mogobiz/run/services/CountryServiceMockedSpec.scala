/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import com.mogobiz.{DefaultCompleteMocked, MogobizRouteTest}

/**
  */
class CountryServiceMockedSpec extends MogobizRouteTest {

  override lazy val apiRoutes = (new CountryService() with DefaultCompleteMocked).route

  " country route " should " respond and be successful " in {
    Get("/store/" + STORE + "/countries") ~> sealRoute(routes) ~> check {
      status.isSuccess should be(true)
    }
  }
}
