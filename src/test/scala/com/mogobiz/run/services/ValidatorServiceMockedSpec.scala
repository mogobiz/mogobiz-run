package com.mogobiz.run.services

import java.util.UUID

import com.mogobiz.{DefaultCompleteMocked, MogobizRouteMocked}

/**
 * Created by Christophe on 30/06/2015.
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
