package com.mogobiz.run.services

import java.util.UUID

import com.mogobiz.{DefaultCompleteMocked, MogobizRouteMocked}

/**
 * Created by Christophe on 30/06/2015.
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
  }
}
