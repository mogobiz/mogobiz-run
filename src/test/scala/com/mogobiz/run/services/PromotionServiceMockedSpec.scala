/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import com.mogobiz.{DefaultCompleteMocked, MogobizRouteMocked}

/**
 */
class PromotionServiceMockedSpec extends MogobizRouteMocked  {

  override lazy val apiRoutes = (new PromotionService() with DefaultCompleteMocked).route

  " promotion route " should {
    " respond and be successful with default parameters" in {
      Get("/store/" + STORE + "/promotions") ~> sealRoute(routes) ~> check {
        status.isSuccess must beTrue
      }
    }
  }
}
