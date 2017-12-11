/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import com.mogobiz.{DefaultCompleteMocked, MogobizRouteTest}

/**
 */
class PromotionServiceMockedSpec extends MogobizRouteTest  {

  override lazy val apiRoutes = (new PromotionService() with DefaultCompleteMocked).route

  " promotion route " should " respond and be successful with default parameters" in {
      Get("/store/" + STORE + "/promotions") ~> sealRoute(routes) ~> check {
        status.isSuccess should be(true)
      }
    }
  }
