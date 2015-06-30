package com.mogobiz.run.services

import java.util.UUID

import com.mogobiz.{DefaultCompleteMocked, MogobizRouteMocked}

/**
 * Created by Christophe on 29/06/2015.
 */
class ResourceServiceMockedSpec extends MogobizRouteMocked  {

  override lazy val apiRoutes = (new ResourceService() with DefaultCompleteMocked).route

  val resourceUuid = UUID.randomUUID().toString

  " resource route " should {
    skipped(" TODO cause mocking resourceHandler is required ")

    " respond when quering get resource without size " in {
      val path = "/store/" + STORE + "/resources/"+resourceUuid
      Get(path) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

    " respond when quering get resource with a specific size " in {
      val size = 1024
      val path = "/store/" + STORE + "/resources/"+resourceUuid+"/"+size
      Get(path) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }
  }
}
