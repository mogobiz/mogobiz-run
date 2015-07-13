package com.mogobiz.run.services

import com.mogobiz.{DefaultCompleteMocked, MogobizRouteMocked}

class SkuServiceMockedSpec extends MogobizRouteMocked  {

  override lazy val apiRoutes = (new SkuService() with DefaultCompleteMocked).route

  " sku route " should {

    val rootPath = "/store/" + STORE + "/skus"
    " be successful when looking for skus " in {
      //val productUuid = UUID.randomUUID().toString
      //val productId = 1234
      val path = rootPath //+ "/" + productId
      Get(path) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }
  }
}
