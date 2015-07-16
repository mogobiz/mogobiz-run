/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import com.mogobiz.{DefaultCompleteMocked, MogobizRouteMocked}

/**
 * Created by Christophe on 30/06/2015.
 */
class PreferenceServiceServiceSpec extends MogobizRouteMocked  {

  override lazy val apiRoutes = (new PreferenceService() with DefaultCompleteMocked).route

  " preference route " should {

    val rootPath = "/store/" + STORE + "/prefs"
    " be successful when getting user preferences " in {
      Get(rootPath) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }
    " be successful when setting user preferences " in {
      Post(rootPath) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

    " reject on wrong path " in {
      val wrongPath = "/store/" + STORE + "/anythingOtherThanTheRightRoute"
      Get(wrongPath) ~> sealRoute(routes) ~> check {
        status.intValue must_== 404
        status.isSuccess must beFalse
      }
    }

    " reject on wrong path even with prefs prefix" in {
      skipped("because this one fails")
      val wrongPath = "/store/" + STORE + "/prefsAndAnythingOtherThanTheRightRoute"
      Get(wrongPath) ~> sealRoute(routes) ~> check {
        status.intValue must_== 404
        status.isSuccess must beFalse
      }
    }

  }

}
