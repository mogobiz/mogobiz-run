package com.mogobiz.run.services

import com.mogobiz.{DefaultCompleteMocked, MogobizRouteMocked}

/**
 * Created by Christophe on 14/06/2015.
 */
class TagServiceMockedSpec extends MogobizRouteMocked  {

  override lazy val apiRoutes = (new TagService() with DefaultCompleteMocked).route

  " tag route " should {
    " respond and be successful " in {
      Get("/store/" + STORE + "/tags") ~> sealRoute(routes) ~> check {
        status.isSuccess must beTrue
      }
    }
  }
}
