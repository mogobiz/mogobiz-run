/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import java.util.UUID

import com.mogobiz.{DefaultCompleteMocked, MogobizRouteTest}

/**
 */
class ResourceServiceMockedSpec extends MogobizRouteTest  {

  override lazy val apiRoutes = (new ResourceService() with DefaultCompleteMocked).route

  val resourceUuid = UUID.randomUUID().toString

  " resource route " should " respond when quering get resource without size " in {
      val path = "/store/" + STORE + "/resources/"+resourceUuid
      Get(path) ~> sealRoute(routes) ~> check {
        status.intValue should be(200)
        status.isSuccess should be(true)
      }
    }

    it should " respond when quering get resource with a specific size " in {
      val size = 1024
      val path = "/store/" + STORE + "/resources/"+resourceUuid+"/"+size
      Get(path) ~> sealRoute(routes) ~> check {
        status.intValue should be(200)
        status.isSuccess should be(true)
      }
    }
  }
