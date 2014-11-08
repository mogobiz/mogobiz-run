package com.mogobiz.run.model

import java.util.Date

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import com.mogobiz.utils.GlobalUtil._


object WishlistVisibility extends Enumeration {

  type WishlistVisibility = Value

  val PUBLIC = Value("Public")
  val PRIVATE = Value("Private")
  val SHARED = Value("Shared")
}

class WishlistVisibilityRef extends TypeReference[WishlistVisibility.type]

import com.mogobiz.run.model.WishlistVisibility._

case class WishIdea(uuid: String = newUUID, name: String)

case class WishBrand(uuid: String = newUUID, name: String, brand: String)

case class WishCategory(uuid: String = newUUID, name: String, category: String)

case class WishItem(uuid: String = newUUID, name: String, product: String, sku : Option[String] = None)

case class WishlistOwner(email: String, name: Option[String] = None, dayOfBirth: Option[Int] = None, monthOfBirth: Option[Int] = None, description: Option[String] = None)

case class Wishlist(uuid: String = newUUID,
                    name: String,
                    @JsonScalaEnumeration(classOf[WishlistVisibilityRef]) visibility: WishlistVisibility = WishlistVisibility.PRIVATE,
                    default: Boolean = false,
                    token: String = newUUID,
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

case class AddWishlistCommand(name: String, visibility: WishlistVisibility = WishlistVisibility.PRIVATE, defaultIndicator: Boolean = false, owner_email: String)

case class AddItemCommand(name: String, product: String, owner_email: String, product_sku : Option[String] = None)

case class AddBrandCommand(name: String, brand: String, owner_email: String)

case class AddCategoryCommand(name: String, category: String, owner_email: String)
