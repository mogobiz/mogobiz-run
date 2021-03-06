/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import com.mogobiz.{DefaultCompleteMocked, MogobizRouteTest}

/**
 */
class TagServiceMockedSpec extends MogobizRouteTest  {

  override lazy val apiRoutes = (new TagService() with DefaultCompleteMocked).route

  " tag route " should " respond and be successful " in {
      Get("/store/" + STORE + "/tags") ~> sealRoute(routes) ~> check {
        status.isSuccess should be(true)
      }
    }
  }
