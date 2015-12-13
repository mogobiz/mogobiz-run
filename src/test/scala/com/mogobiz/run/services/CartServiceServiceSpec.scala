/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import java.util.UUID

import com.mogobiz.run.implicits.Json4sProtocol
import com.mogobiz.run.model.RequestParameters._
import com.mogobiz.{DefaultCompleteMocked, MogobizRouteMocked}

/**
 */
class CartServiceServiceSpec extends MogobizRouteMocked  {

  override lazy val apiRoutes = (new CartService() with DefaultCompleteMocked).route

  " cart route " should {

    val rootPath = "/store/" + STORE + "/cart"
    " be successful when getting cart " in {
      Get(rootPath) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

    " be successful when deleting cart " in {
      Delete(rootPath) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

    " be successful when validate cart " in {
      val path = rootPath + "/validate"
      Post(path) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

    " be successful when adding an item to the cart " in {
      import Json4sProtocol._

      val path = rootPath + "/items"
      val skuId = 1234
      val item = AddCartItemRequest(None,skuId,"productUrl",5,None, List())
      Post(path,item) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

    " be successful when updating an item in the cart " in {
      import Json4sProtocol._

      val itemId = UUID.randomUUID().toString
      val path = rootPath + "/item/"+itemId
      val item = UpdateCartItemRequest(5)
      Put(path,item) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

    " be successful when updating an item in the cart " in {
      val itemId = UUID.randomUUID().toString
      val path = rootPath + "/item/"+itemId
      Delete(path) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

    " be successful when creating coupon " in {
      val code = "ABC"
      val path = rootPath + "/coupons/"+code
      Post(path) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

    " be successful when deleting coupon " in {
      val code = "ABC"
      val path = rootPath + "/coupons/"+code
      Delete(path) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

    " be successful when prepare payment " in {
      import Json4sProtocol._

      val action = "prepare"
      val path = rootPath + "/payment/"+action
      val obj = PrepareTransactionParameters(Some("currency"), Some("country"), Some("state"), "buyer", "shippingAddress", "lang")

      Post(path, obj) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

    " be successful when commit payment " in {
      import Json4sProtocol._

      val action = "commit"
      val path = rootPath + "/payment/"+action

      val transactionUuid = UUID.randomUUID().toString

      val obj = CommitTransactionParameters(Some("country"), transactionUuid, "lang")

      Post(path, obj) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

    " be successful when cancel payment " in {
      import Json4sProtocol._

      val action = "cancel"
      val path = rootPath + "/payment/"+action
      val obj = CancelTransactionParameters(Some("currency"), Some("country"), Some("state"), "lang")

      Post(path, obj) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

    " reject on wrong path " in {
      val wrongPath = "/store/" + STORE + "/carton"
      Get(wrongPath) ~> sealRoute(routes) ~> check {
        status.intValue must_== 404
        status.isSuccess must beFalse
      }
    }
  }
}
