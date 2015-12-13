/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import com.mogobiz.{DefaultCompleteMocked, MogobizRouteMocked}
import java.util.UUID
/**
 */
class WishlistServiceMockedSpec extends MogobizRouteMocked  {

  override lazy val apiRoutes = (new WishlistService() with DefaultCompleteMocked).route

  val wishlistListUuid = UUID.randomUUID().toString
  val wishlistUuid = UUID.randomUUID().toString

  " wishlist route " should {
    " respond and be successful with default parameters" in {
      Get("/store/" + STORE + "/wishlists?owner_email=somestrval") ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

    " answer for the wishlist token " in {
      val path = "/store/" + STORE + "/wishlists/" + wishlistListUuid + "/wishlist/" + wishlistUuid + "/token?owner_email=somestrval"
      Get(path) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

    " answer for the wishlist by token" in {
      val path = "/store/" + STORE + "/wishlists/wishlist/" + wishlistUuid
      Get(path) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

    " set default wishlist " in {
      val path = "/store/" + STORE + "/wishlists/" + wishlistListUuid + "/wishlist/" + wishlistUuid + "/default?owner_email=somestrval"
      Post(path) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

    " set owner info " in {
      val path = "/store/" + STORE + "/wishlists/" + wishlistListUuid + "/owner?owner_email=somestrval"
      Post(path) ~> sealRoute(routes) ~> check {
        status.intValue must_== 200
        status.isSuccess must beTrue
      }
    }

    /*
val wishlistListUuid = UUID.randomUUID().toString
val wishlistUuid = UUID.randomUUID().toString
Post("/store/" + STORE + "/wishlists/" + wishlistUuid + "/wishlist" + wishlistListUuid + "/item") ~> sealRoute(routes) ~> check {
  status.isSuccess must beTrue
}
*/


  }

}
