/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import com.mogobiz.{DefaultCompleteMocked, MogobizRouteTest}

/**
  */
class CategoryServiceMockedSpec extends MogobizRouteTest {

  override lazy val apiRoutes = (new CategoryService() with DefaultCompleteMocked).route

  " category route " should " respond and be successful with default parameters" in {
    Get("/store/" + STORE + "/categories") ~> sealRoute(routes) ~> check {
      status.isSuccess should be(true)
    }
  }
  it should " accept true for hidden parameter" in {
    Get("/store/" + STORE + "/categories?hidden=true") ~> sealRoute(routes) ~> check {
      status.isSuccess should be(true)
    }
  }
  it should " accept size parameter " in {
    Get("/store/" + STORE + "/categories?size=10") ~> sealRoute(routes) ~> check {
      status.isSuccess should be(true)
    }
    //parameters('hidden ? false, 'parentId.?, 'brandId.?, 'categoryPath.?, 'lang ? "_all", 'promotionId.?, 'size.as[Option[Int]]) {
  }
}
