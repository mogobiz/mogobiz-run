package com.mogobiz.model

import java.util.{Calendar, Date}

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

case class WishItem(uuid: String, name: String, product: String)

case class WishlistOwner(email: String, name: Option[String] = None, dayOfBirth: Option[Int] = None, monthOfBirth: Option[Int] = None, description: Option[String] = None)

case class Wishlist(uuid: String,
                    name: String,
                    visibility: WishlistVisibility,
                    default: Boolean,
                    token: String = null,
                    ideas: List[WishIdea] = List(),
                    items: List[WishItem] = List(),
                    var dateCreated: Date = Calendar.getInstance().getTime,
                    var lastUpdated: Date = Calendar.getInstance().getTime)


case class WishlistList(uuid: String, wishlists: List[Wishlist] = List(), owner: WishlistOwner)