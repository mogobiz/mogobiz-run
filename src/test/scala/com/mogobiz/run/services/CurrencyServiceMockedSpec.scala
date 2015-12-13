/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import com.mogobiz.{DefaultCompleteMocked, MogobizRouteMocked}

/**
 */
class CurrencyServiceMockedSpec extends MogobizRouteMocked  {

  override lazy val apiRoutes = (new CurrencyService() with DefaultCompleteMocked).route

  " currency route " should {
    " respond and be successful " in {
      Get("/store/" + STORE + "/currencies") ~> sealRoute(routes) ~> check {
        status.isSuccess must beTrue
      }
    }
  }
}
