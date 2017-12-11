/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import java.util.UUID

import com.mogobiz.{DefaultCompleteMocked, MogobizRouteTest}

/**
 */
class ValidatorServiceMockedSpec extends MogobizRouteTest  {

  override lazy val apiRoutes = (new ValidatorService() with DefaultCompleteMocked).route

  val rootPath = "/store/" + STORE + "/download"
  " validator route " should " respond when validate download " in {
      val key = UUID.randomUUID().toString
      val path = rootPath + "/" + "key"
      Get(path) ~> sealRoute(routes) ~> check {
        status.intValue should be(200)
        status.isSuccess should be(true)
      }
    }
  }

