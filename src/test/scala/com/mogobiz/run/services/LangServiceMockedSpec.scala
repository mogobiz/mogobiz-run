package com.mogobiz.run.services

import com.mogobiz.{DefaultCompleteMocked, MogobizRouteMocked}

/**
 * Created by Christophe on 17/06/2015.
 */
class LangServiceMockedSpec extends MogobizRouteMocked  {

  override lazy val apiRoutes = (new LangService() with DefaultCompleteMocked).route

  " lang route " should {
    " respond and be successful " in {
      Get("/store/" + STORE + "/langs") ~> sealRoute(routes) ~> check {
        status.isSuccess must beTrue
      }
    }
  }
}
