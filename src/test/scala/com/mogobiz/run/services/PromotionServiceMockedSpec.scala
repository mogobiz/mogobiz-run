package com.mogobiz.run.services

import com.mogobiz.{DefaultCompleteMocked, MogobizRouteMocked}

/**
 * Created by Christophe on 18/06/2015.
 */
class PromotionServiceMockedSpec extends MogobizRouteMocked  {

  override lazy val apiRoutes = (new PromotionService() with DefaultCompleteMocked).route

  " promotion route " should {
    " respond and be successful with default parameters" in {
      Get("/store/" + STORE + "/promotions") ~> sealRoute(routes) ~> check {
        status.isSuccess must beTrue
      }
    }
  }
}
