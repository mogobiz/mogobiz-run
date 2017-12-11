/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import com.mogobiz.{DefaultCompleteMocked, MogobizRouteTest}

/**
  */
class BrandServiceMockedSpec extends MogobizRouteTest {

  override lazy val apiRoutes = (new BrandService() with DefaultCompleteMocked).route

  "brand route " should "respond and be successful" in {
    Get("/store/" + STORE + "/brands") ~> sealRoute(routes) ~> check { status.isSuccess should be(true) }
  }
}
