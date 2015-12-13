/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import com.mogobiz.{DefaultCompleteMocked, MogobizRouteMocked}

/**
 */
class LangServiceMockedSpec extends MogobizRouteMocked  {

  override lazy val apiRoutes = (new LangService() with DefaultCompleteMocked).route

  " lang route " should {
    " respond and be successful " in {
      Get("/store/" + STORE + "/langs") ~> sealRoute(routes) ~> check {
        status.isSuccess must beTrue
      }
    }
  }
}
