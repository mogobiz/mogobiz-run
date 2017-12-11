/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import com.mogobiz.{DefaultCompleteMocked, MogobizRouteTest}

class SkuServiceMockedSpec extends MogobizRouteTest  {

  override lazy val apiRoutes = (new SkuService() with DefaultCompleteMocked).route
  val rootPath = "/store/" + STORE + "/skus"

  " sku route " should " be successful when looking for skus " in {
      //val productUuid = UUID.randomUUID().toString
      //val productId = 1234
      val path = rootPath //+ "/" + productId
      Get(path) ~> sealRoute(routes) ~> check {
        status.intValue should be(200)
        status.isSuccess should be(true)
    }
  }
}
