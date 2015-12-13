/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import java.util.UUID

import com.mogobiz.{DefaultCompleteMocked, MogobizRouteMocked}

/**
 */
class LearningServiceMockedSpec extends MogobizRouteMocked  {

  override lazy val apiRoutes = (new LearningService() with DefaultCompleteMocked).route

  " learning route " should {

    val rootPath = "/store/" + STORE + "/learning"
    " respond when ask for similarity " in {
      val uuid =  UUID.randomUUID().toString
      val path = rootPath + "/" + uuid + "/history?action=purchase&history_count=1&count=2&match_count=3"
      Get(path) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

    " respond when ask for cooccurrence " in {
      val uuid =  UUID.randomUUID().toString
      val path = rootPath + "/" + uuid + "/cooccurrence?action=purchase"
      Get(path) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

    " respond when ask for frequency " in {
      val uuid =  UUID.randomUUID().toString
      val path = rootPath + "/" + uuid + "/fis?frequency="+100d
      Get(path) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }


    " respond when ask for last " in {
      val uuid =  UUID.randomUUID().toString
      val path = rootPath + "/" + uuid + "/last?count="+10
      Get(path) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

    " respond when ask for popular " in {
      val uuid =  UUID.randomUUID().toString
      val path = rootPath + "/popular?action=view&since=20150101&count="+10
      Get(path) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

  }
}
