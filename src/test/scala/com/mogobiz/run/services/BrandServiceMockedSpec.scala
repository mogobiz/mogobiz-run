/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import com.mogobiz.{DefaultCompleteMocked, MogobizRouteMocked}

/**
 */
class BrandServiceMockedSpec extends MogobizRouteMocked  {

  override lazy val apiRoutes = (new BrandService() with DefaultCompleteMocked).route

  " brand route " should {
    " respond and be successful " in {
      Get("/store/" + STORE + "/brands") ~> sealRoute(routes) ~> check {
        status.isSuccess must beTrue
      }
    }
  }
}
