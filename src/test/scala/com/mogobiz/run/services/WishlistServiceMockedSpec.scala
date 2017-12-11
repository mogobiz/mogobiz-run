/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import com.mogobiz.{DefaultCompleteMocked, MogobizRouteTest}
import java.util.UUID
/**
 */
class WishlistServiceMockedSpec extends MogobizRouteTest {

  override lazy val apiRoutes = (new WishlistService() with DefaultCompleteMocked).route

  val wishlistListUuid = UUID.randomUUID().toString
  val wishlistUuid = UUID.randomUUID().toString

  " wishlist route " should " respond and be successful with default parameters" in {
      Get("/store/" + STORE + "/wishlists?owner_email=somestrval") ~> sealRoute(routes) ~> check {
        status.intValue should be(200)
        status.isSuccess should be(true)
      }
    }

    it should " answer for the wishlist token " in {
      val path = "/store/" + STORE + "/wishlists/" + wishlistListUuid + "/wishlist/" + wishlistUuid + "/token?owner_email=somestrval"
      Get(path) ~> sealRoute(routes) ~> check {
        status.intValue should be(200)
        status.isSuccess should be(true)
      }
    }

    it should " answer for the wishlist by token" in {
      val path = "/store/" + STORE + "/wishlists/wishlist/" + wishlistUuid
      Get(path) ~> sealRoute(routes) ~> check {
        status.intValue should be(200)
        status.isSuccess should be(true)
      }
    }

    it should " set default wishlist " in {
      val path = "/store/" + STORE + "/wishlists/" + wishlistListUuid + "/wishlist/" + wishlistUuid + "/default?owner_email=somestrval"
      Post(path) ~> sealRoute(routes) ~> check {
        status.intValue should be(200)
        status.isSuccess should be(true)
      }
    }

    it should " set owner info " in {
      val path = "/store/" + STORE + "/wishlists/" + wishlistListUuid + "/owner?owner_email=somestrval"
      Post(path) ~> sealRoute(routes) ~> check {
        status.intValue should be(200)
        status.isSuccess should be(true)
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
