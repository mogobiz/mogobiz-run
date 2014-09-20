package com.mogobiz.model

import java.util.Date

import com.mogobiz.utils.GlobalUtil._


object WishlistVisibility extends Enumeration {

  type WishlistVisibility = Value

  val PUBLIC = Value("Public")
  val PRIVATE = Value("Private")
  val SHARED = Value("Shared")
}

import com.mogobiz.model.WishlistVisibility._

case class WishIdea(uuid: String, idea: String)

case class WishBrand(uuid: String, brand: String)

case class WishCategory(uuid: String, category: String)

case class WishItem(uuid: String, name: String, product: String)

case class WishlistOwner(email: String, name: Option[String] = None, dayOfBirth: Option[Int] = None, monthOfBirth: Option[Int] = None, description: Option[String] = None)

case class Wishlist(name: String,
                    uuid: String = newUUID,
                    visibility: WishlistVisibility = WishlistVisibility.PRIVATE,
                    default: Boolean = false,
                    token: String = null,
                    ideas: List[WishIdea] = List(),
                    items: List[WishItem] = List(),
                    brands: List[WishBrand] = List(), // Not yet available
                    categories: List[WishCategory] = List(), // Not yet available
                    alert: Boolean = false, // Ignored
                    var dateCreated: Date = null,
                    var lastUpdated: Date = null)


case class WishlistList(uuid: String = newUUID,
                        wishlists: List[Wishlist] = List(),
                        owner: WishlistOwner,
                        var dateCreated: Date = null,
                        var lastUpdated: Date = null)