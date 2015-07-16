/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import com.mogobiz.{DefaultCompleteMocked, MogobizRouteMocked}

/**
 * Created by Christophe on 18/06/2015.
 */
class CategoryServiceMockedSpec extends MogobizRouteMocked  {

  override lazy val apiRoutes = (new CategoryService() with DefaultCompleteMocked).route

  " category route " should {
    " respond and be successful with default parameters" in {
      Get("/store/" + STORE + "/categories") ~> sealRoute(routes) ~> check {
        status.isSuccess must beTrue
      }
    }
    " accept true for hidden parameter" in {
      Get("/store/" + STORE + "/categories?hidden=true") ~> sealRoute(routes) ~> check {
        status.isSuccess must beTrue
      }
    }
    " accept size parameter " in {
      Get("/store/" + STORE + "/categories?size=10") ~> sealRoute(routes) ~> check {
        status.isSuccess must beTrue
      }
      //parameters('hidden ? false, 'parentId.?, 'brandId.?, 'categoryPath.?, 'lang ? "_all", 'promotionId.?, 'size.as[Option[Int]]) {
    }
  }
}
