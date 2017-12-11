/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import com.mogobiz.{DefaultCompleteMocked, MogobizRouteTest}

/**
 */
class PreferenceServiceServiceSpec extends MogobizRouteTest {

  override lazy val apiRoutes = (new PreferenceService() with DefaultCompleteMocked).route

  val rootPath = "/store/" + STORE + "/prefs"
  " preference route " should " be successful when getting user preferences " in {
      Get(rootPath) ~> sealRoute(routes) ~> check {
        status.intValue should be(200)
        status.isSuccess should be(true)
      }
    }
    it should " be successful when setting user preferences " in {
      Post(rootPath) ~> sealRoute(routes) ~> check {
        status.intValue should be(200)
        status.isSuccess should be(true)
      }
    }

  it should " reject on wrong path " in {
      val wrongPath = "/store/" + STORE + "/anythingOtherThanTheRightRoute"
      Get(wrongPath) ~> sealRoute(routes) ~> check {
        status.intValue should be(404)
        status.isSuccess should be(false)
      }
    }

  it should " reject on wrong path even with prefs prefix" in {
      val wrongPath = "/store/" + STORE + "/prefsAndAnythingOtherThanTheRightRoute"
      Get(wrongPath) ~> sealRoute(routes) ~> check {
        status.intValue should be(404)
        status.isSuccess should be(false)
      }
    }
}
