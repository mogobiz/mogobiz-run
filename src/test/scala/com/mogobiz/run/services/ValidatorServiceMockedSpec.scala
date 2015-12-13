/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import java.util.UUID

import com.mogobiz.{DefaultCompleteMocked, MogobizRouteMocked}

/**
 */
class ValidatorServiceMockedSpec extends MogobizRouteMocked  {

  override lazy val apiRoutes = (new ValidatorService() with DefaultCompleteMocked).route

  " validator route " should {

    val rootPath = "/store/" + STORE + "/download"
    " respond when validate download " in {
      val key = UUID.randomUUID().toString
      val path = rootPath + "/" + "key"
      Get(path) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }
  }
}
