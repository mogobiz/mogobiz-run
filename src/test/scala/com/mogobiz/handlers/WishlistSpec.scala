package com.mogobiz.handlers

import com.mogobiz.model._
import com.mogobiz.utils.GlobalUtil._
import com.mogobiz.json.JacksonConverter
import org.specs2.mutable.Specification

class WishlistSpec extends Specification {
  val service = new WishlistHandler()
  val Store = "mogobiz"


  // Ne pas utiliser. Plante pour des raisons inconnues.
  // Un main existe dans WishlistHandler poru faire la même chose
//  "Get user wishlist" in {
//    val wll = service.getWishlistList(Store, "hayssam@saleh.fr")
//    val wluuid = service.addWishlist(Store, wll.uuid, Wishlist(name = "Ma 1ere liste"), wll.owner.email)
//    service.addIdea(Store, wll.uuid, wluuid, WishIdea(name = "My first idea"), "hayssam@saleh.fr")
//    service.addIdea(Store, wll.uuid, wluuid, WishIdea(name = "My second idea"), "hayssam@saleh.fr")
//    service.addItem(Store, wll.uuid, wluuid, WishItem(name = "My first item", product = "product-uuid"), "hayssam@saleh.fr")
//    service.addItem(Store, wll.uuid, wluuid, WishItem(name = "My second item", product = "product-uuid"), "hayssam@saleh.fr")
//    service.setOwnerInfo(Store, wll.uuid, WishlistOwner("hayssam@saleh.fr", Some("Hayssam Saleh"), Some(15), Some(9), Some("Qui suis-je ?")))
//    service.addWishlist(Store, wll.uuid, Wishlist(name = "Ma deuxième liste"), wll.owner.email)
//    wll must not beNull
//  }
//  "remove idea and items" in {
//    val wll = service.getWishlistList(Store, "hayssam@saleh.fr")
////    service.removeIdea(Store, wll.uuid, wll.wishlists.head.uuid, wll.wishlists.head.ideas.head.uuid, "hayssam@saleh.fr")
////    service.removeItem(Store, wll.uuid, wll.wishlists.head.uuid, wll.wishlists.head.items.head.uuid, "hayssam@saleh.fr")
////    service.removeWishlist(Store, wll.uuid, wll.wishlists.head.uuid, "hayssam@saleh.fr")
//    wll must not beNull
//  }
}
