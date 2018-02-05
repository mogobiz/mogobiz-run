/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import java.util.UUID

import com.mogobiz.{DefaultCompleteMocked, MogobizRouteTest}
import com.mogobiz.run.model.RequestParameters.{
  CreateBOReturnedItemRequest,
  UpdateBOReturnedItemRequest
}
import spray.http.HttpEntity

/**
  */
class BackofficeServiceMockedSpec extends MogobizRouteTest {

  override lazy val apiRoutes =
    (new BackofficeService() with DefaultCompleteMocked).route

  " backoffice route " should " respond when get list orders" in {
    val path = "/store/" + STORE + "/backoffice/listOrders"
    Get(path) ~> sealRoute(routes) ~> check {
      status.intValue should be(200)
      status.isSuccess should be(true)
    }
  }

  it should "respond when get list customers" in {
    val path = "/store/" + STORE + "/backoffice/listCustomers"
    Get(path) ~> sealRoute(routes) ~> check {
      status.intValue should be(200)
      status.isSuccess should be(true)
    }
  }

  it should "respond when get cart details" in {
    val transactionUuid = UUID.randomUUID().toString
    val path = "/store/" + STORE + "/backoffice/cartDetails/" + transactionUuid
    Get(path) ~> sealRoute(routes) ~> check {
      status.intValue should be(200)
      status.isSuccess should be(true)
    }
  }

  it should "respond when create item" in {
    import JacksonSupport._

    val transactionUuid = UUID.randomUUID().toString
    val boCartItemUuid = UUID.randomUUID().toString
    val path = "/store/" + STORE + "/backoffice/cartDetails/" + transactionUuid + "/" + boCartItemUuid

    val obj = CreateBOReturnedItemRequest(1, "test creation")
    Post(path, obj) ~> sealRoute(routes) ~> check {
      status.intValue should be(200)
      status.isSuccess should be(true)
    }
  }

  it should "respond when update item" in {

    val transactionUuid = UUID.randomUUID().toString
    val boCartItemUuid = UUID.randomUUID().toString
    val boReturnedUuid = UUID.randomUUID().toString

    val obj = UpdateBOReturnedItemRequest("refuned",
                                          1,
                                          10,
                                          "return status",
                                          "motivation")
    val path = "/store/" + STORE + "/backoffice/cartDetails/" + transactionUuid + "/" + boCartItemUuid + "/" + boReturnedUuid
    Put(path, obj) ~> sealRoute(routes) ~> check {
      status.intValue should be(200)
      status.isSuccess should be(true)
    }
  }
}
