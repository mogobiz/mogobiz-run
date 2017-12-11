/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import com.mogobiz.MogobizRouteTest
import com.mogobiz.run.model._

class WishlistSpec extends MogobizRouteTest {
  val service = new WishlistHandler()
  val Store = "mogobiz"


  // Ne pas utiliser. Plante pour des raisons inconnues.
  // Un main existe dans WishlistHandler poru faire la même chose
  "Get user wishlist" should "succeed"  in {
    val wll = service.getWishlistList(Store, "hayssam@saleh.fr")
    val wluuid = service.addWishlist(Store, wll.uuid, Wishlist(name = "Ma 1ere liste"), wll.owner.email)
    service.addIdea(Store, wll.uuid, wluuid, WishIdea(name = "My first idea"), "hayssam@saleh.fr")
    service.addIdea(Store, wll.uuid, wluuid, WishIdea(name = "My second idea"), "hayssam@saleh.fr")
    service.addItem(Store, wll.uuid, wluuid, WishItem(name = "My first item", product = "product-uuid"), "hayssam@saleh.fr")
    service.addItem(Store, wll.uuid, wluuid, WishItem(name = "My second item", product = "product-uuid"), "hayssam@saleh.fr")
    service.setOwnerInfo(Store, wll.uuid, WishlistOwner("hayssam@saleh.fr", Some("Hayssam Saleh"), Some(15), Some(9), Some("Qui suis-je ?")))
    service.addWishlist(Store, wll.uuid, Wishlist(name = "Ma deuxième liste"), wll.owner.email)
    wll should not be(null)
  }
  "remove idea and items" should "succeed" in {
    val wll = service.getWishlistList(Store, "hayssam@saleh.fr")
    service.removeIdea(Store, wll.uuid, wll.wishlists.head.uuid, wll.wishlists.head.ideas.head.uuid, "hayssam@saleh.fr")
    service.removeItem(Store, wll.uuid, wll.wishlists.head.uuid, wll.wishlists.head.items.head.uuid, "hayssam@saleh.fr")
    service.removeWishlist(Store, wll.uuid, wll.wishlists.head.uuid, "hayssam@saleh.fr")
    wll should not be(null)
  }
}
