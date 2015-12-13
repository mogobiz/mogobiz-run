/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import java.util.UUID

import com.mogobiz.{DefaultCompleteMocked, MogobizRouteMocked}
import com.mogobiz.run.model.RequestParameters.{UpdateBOReturnedItemRequest, CreateBOReturnedItemRequest}
import spray.http.HttpEntity

/**
 */
class BackofficeServiceMockedSpec extends MogobizRouteMocked  {

  override lazy val apiRoutes = (new BackofficeService() with DefaultCompleteMocked).route

  " backoffice route " should {

    " respond when get list orders" in {
      val path = "/store/" + STORE + "/backoffice/listOrders"
      Get(path) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

    " respond when get list customers" in {
      val path = "/store/" + STORE + "/backoffice/listCustomers"
      Get(path) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

    " respond when get cart details" in {
      val transactionUuid = UUID.randomUUID().toString
      val path = "/store/" + STORE + "/backoffice/cartDetails/" + transactionUuid
      Get(path) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

    " respond when create item" in {
      import com.mogobiz.run.implicits.Json4sProtocol
      import Json4sProtocol._

      val transactionUuid = UUID.randomUUID().toString
      val boCartItemUuid = UUID.randomUUID().toString
      val path = "/store/" + STORE + "/backoffice/cartDetails/" + transactionUuid + "/" + boCartItemUuid

      val obj = CreateBOReturnedItemRequest(1, "test creation")
      Post(path, obj)  ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

    " respond when update item" in {
      import com.mogobiz.run.implicits.Json4sProtocol
      import Json4sProtocol._

      val transactionUuid = UUID.randomUUID().toString
      val boCartItemUuid = UUID.randomUUID().toString
      val boReturnedUuid = UUID.randomUUID().toString

      val obj = UpdateBOReturnedItemRequest("refuned", 1, 10,"return status", "motivation")
      val path = "/store/" + STORE + "/backoffice/cartDetails/" + transactionUuid + "/" + boCartItemUuid + "/" + boReturnedUuid
      Put(path, obj) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

  }
}
