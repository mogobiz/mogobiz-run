package com.mogobiz.handlers

import java.util.Calendar

import com.mogobiz.es.EsClient
import com.mogobiz.model._
import com.mogobiz.utils.{NotFoundException, NotAuthorizedException, DuplicateException}
import com.mogobiz.utils.GlobalUtil._
import com.sksamuel.elastic4s.ElasticDsl._

import scala.util.Success


object WishlistHandler {
  def esStore(store: String) = s"$store-wishlist"
}

class WishlistHandler {

  import com.mogobiz.handlers.WishlistHandler._

  def addItem(store: String, wishlistListId: String, wishlistId: String, item: WishItem, owneremail: String): String = {
    val wishlistList = EsClient.load[WishlistList](esStore(store), wishlistListId).getOrElse(throw NotFoundException(s"Unknown wishlistList $wishlistListId"))
    if (owneremail != wishlistList.owner.email)
      throw NotAuthorizedException("Not Authorized")
    val wishlist = wishlistList.wishlists.find(_.uuid == wishlistId) getOrElse (throw NotFoundException(s"Invalid wishlist uuid $wishlistId"))
    if (wishlist.items.exists(_.product == item.product))
      throw new DuplicateException(s"${item.name}")
    else {
      val now = Calendar.getInstance().getTime
      val res = wishlistList.copy(wishlists = wishlistList.wishlists.filter(_.uuid != wishlistId) :+ wishlist.copy(items = wishlist.items :+ item, lastUpdated = now))
      EsClient.update[WishlistList](esStore(store), res)
      item.uuid
    }
  }

  def addBrand(store: String, wishlistListId: String, wishlistId: String, brand: WishBrand, owneremail: String): String = {
    val wishlistList = EsClient.load[WishlistList](esStore(store), wishlistListId).getOrElse(throw NotFoundException(s"Unknown wishlistList $wishlistListId"))
    if (owneremail != wishlistList.owner.email)
      throw NotAuthorizedException("Not Authorized")
    val wishlist = wishlistList.wishlists.find(_.uuid == wishlistId) getOrElse (throw NotFoundException(s"Invalid wishlist uuid $wishlistId"))
    if (wishlist.brands.exists(_.brand == brand.brand))
      throw new DuplicateException(s"${brand.name}")
    else {
      val now = Calendar.getInstance().getTime
      val res = wishlistList.copy(wishlists = wishlistList.wishlists.filter(_.uuid != wishlistId) :+ wishlist.copy(brands = wishlist.brands :+ brand, lastUpdated = now))
      Success(EsClient.update[WishlistList](esStore(store), res))
      brand.uuid
    }
  }


  def removeItem(store: String, wishlistListId: String, wishlistId: String, itemUuid: String, owneremail: String): Unit = {
    val wishlistList = EsClient.load[WishlistList](esStore(store), wishlistListId).getOrElse(throw NotFoundException(s"Unknown wishlistList $wishlistListId"))
    if (owneremail != wishlistList.owner.email)
      throw NotAuthorizedException("Not Authorized")
    val wishlist = wishlistList.wishlists.find(_.uuid == wishlistId) getOrElse (throw NotFoundException(s"Invalid wishlist uuid $wishlistId"))
    val now = Calendar.getInstance().getTime
    val res = wishlistList.copy(wishlists = wishlistList.wishlists.filter(_.uuid != wishlist.uuid) :+ wishlist.copy(items = wishlist.items.filter(_.uuid != itemUuid), lastUpdated = now))
    EsClient.update[WishlistList](esStore(store), res)
  }

  def addIdea(store: String, wishlistListId: String, wishlistId: String, idea: WishIdea, owneremail: String): String = {
    val wishlistList = EsClient.load[WishlistList](esStore(store), wishlistListId).getOrElse(throw NotFoundException(s"Unknown wishlistList $wishlistListId"))
    if (owneremail != wishlistList.owner.email)
      throw NotAuthorizedException("Not Authorized")
    val wishlist = wishlistList.wishlists.find(_.uuid == wishlistId) getOrElse (throw NotFoundException(s"Invalid wishlist uuid $wishlistId"))
    val res = wishlistList.copy(wishlists = wishlistList.wishlists.filter(_.uuid != wishlistId) :+ wishlist.copy(ideas = wishlist.ideas :+ idea))
    EsClient.update[WishlistList](esStore(store), res)
    idea.uuid
  }

  def removeIdea(store: String, wishlistListId: String, wishlistId: String, ideaUuid: String, owneremail: String): Unit = {
    val wishlistList = EsClient.load[WishlistList](esStore(store), wishlistListId).getOrElse(throw NotFoundException(s"Unknown wishlistList $wishlistListId"))
    if (owneremail != wishlistList.owner.email)
      throw NotAuthorizedException("Not Authorized")
    val now = Calendar.getInstance().getTime
    val wishlist = wishlistList.wishlists.find(_.uuid == wishlistId) getOrElse (throw NotFoundException(s"Invalid wishlist uuid $wishlistId"))
    val res = wishlistList.copy(wishlists = wishlistList.wishlists.filter(_.uuid != wishlist.uuid) :+ wishlist.copy(items = wishlist.items.filter(_.uuid != ideaUuid), lastUpdated = now))
    EsClient.update[WishlistList](esStore(store), res)
  }

  def setOwnerInfo(store: String, wishlistListId: String, owner: WishlistOwner): Unit = {
    val wishlistList: WishlistList = EsClient.load[WishlistList](esStore(store), wishlistListId).getOrElse(throw NotFoundException(s"Unknown wishlistList $wishlistListId"))
    if (owner.email != wishlistList.owner.email)
      throw NotAuthorizedException("Not Authorized")
    EsClient.update[WishlistList](esStore(store), wishlistList.copy(owner = owner))
  }

  def addWishlist(store: String, wishlistListId: String, wishlist: Wishlist, owneremail: String): String = {
    val wishlistList = EsClient.load[WishlistList](esStore(store), wishlistListId).getOrElse(throw NotFoundException(s"Unknown wishlistList $wishlistListId"))
    if (owneremail != wishlistList.owner.email)
      throw NotAuthorizedException("Not Authorized")
    if (wishlistList.wishlists.exists(_.name == wishlist.name))
      throw new DuplicateException(s"${wishlist.name}")
    val now = Calendar.getInstance().getTime
    val default = if (wishlistList.wishlists.size == 0) true else wishlist.default
    val wishlists =
      if (default) {
        wishlistList.wishlists.map(_.copy(default = false))
      }
      else {
        wishlistList.wishlists
      }
    EsClient.update[WishlistList](esStore(store), wishlistList.copy(wishlists = wishlists :+ wishlist.copy(token = newUUID, default = default, dateCreated = now, lastUpdated = now)))
    wishlist.uuid
  }

  def removeWishlist(store: String, wishlistListId: String, wishlistId: String, owneremail: String): Unit = {
    val wishlistList = EsClient.load[WishlistList](esStore(store), wishlistListId).getOrElse(throw NotFoundException(s"Unknown wishlistList $wishlistListId"))
    if (owneremail != wishlistList.owner.email)
      throw NotAuthorizedException("Not Authorized")
    val wishlist = wishlistList.wishlists.find(_.uuid == wishlistId).getOrElse(throw NotFoundException("Invalid wishlistId"))
    val res = wishlistList.copy(wishlists = wishlistList.wishlists.filter(_.uuid != wishlistId))
    val newWishlists =
      if (wishlist.default) {
        if (res.wishlists.size > 0) {
          val head = res.wishlists.head
          head.copy(default = true)
          res.wishlists.drop(1) :+ head.copy(default = true)
        }
        else
          List()
      }
      else
        res.wishlists
    EsClient.update[WishlistList](esStore(store), res.copy(wishlists = newWishlists))
  }

  def setDefaultWishlist(store: String, wishlistListId: String, wishlistId: String, owneremail: String): Unit = {
    val wishlistList = EsClient.load[WishlistList](esStore(store), wishlistListId).getOrElse(throw NotFoundException(s"Unknown wishlistList $wishlistListId"))
    if (owneremail != wishlistList.owner.email)
      throw NotAuthorizedException("Not Authorized")
    val wishlist = wishlistList.wishlists.find(_.uuid == wishlistId).getOrElse(throw NotFoundException("Invalid wishlistId"))
    val wishlists = wishlistList.wishlists.map(_.copy(default = false)).filter(_.uuid != wishlist.uuid) :+ wishlist.copy(default = true)

    EsClient.update[WishlistList](esStore(store), wishlistList.copy(wishlists = wishlists))
  }

  def getWishlistList(store: String, owner_email: String): WishlistList = {
    val req = search in esStore(store) types "wishlistlist" query {
      filteredQuery query {
        matchall
      } filter {
        nestedFilter("owner") filter {
          termFilter("email", owner_email)
        }
      }
    }

    println(req._builder.toString)
    EsClient.search[WishlistList](req).getOrElse {
      val wishlistList = WishlistList(newUUID, Nil, WishlistOwner(email = owner_email))
      EsClient.indexTimestamped[WishlistList](esStore(store), wishlistList)
      wishlistList
    }
  }

  def getWishlistToken(store: String, wishlistListId: String, wishlistId: String, ownerEmail: String): String = {
    val wishlistList = EsClient.load[WishlistList](esStore(store), wishlistListId).getOrElse(throw NotFoundException(s"Unknown wishlistList $wishlistListId"))
    if (ownerEmail != wishlistList.owner.email)
      throw NotAuthorizedException("Not Authorized")
    val wishlist = wishlistList.wishlists.find(_.uuid == wishlistId) getOrElse (throw NotFoundException(s"Invalid wishlist uuid $wishlistId"))
    if (wishlist.visibility == WishlistVisibility.PRIVATE) {
      val res = wishlistList.copy(wishlists = wishlistList.wishlists.filter(_.uuid != wishlistId))
      EsClient.update[WishlistList](esStore(store), res.copy(wishlists = res.wishlists :+ wishlist.copy(visibility = WishlistVisibility.SHARED)))
    }
    s"$store--${wishlist.token}"
  }

  def getWishlistByToken(token: String): Option[Wishlist] = {
    val tokens = token.split("--")
    val (store, wishlist) = (tokens(0), tokens(1))
    val req = search in esStore(store) types "wishlistlist" query {
      filteredQuery query {
        matchall
      } filter {
        nestedFilter("wishlists") filter {
          termFilter("token", wishlist)
        }
      }
    }
    EsClient.search[WishlistList](req) flatMap {
      _.wishlists.find(_.uuid == wishlist)
    }
  }
}


object RunApp extends App {
  val service = new WishlistHandler()
  val Store = "mogobiz"

  val wll = service.getWishlistList(Store, "hayssam@saleh.fr")
  val wluuid = service.addWishlist(Store, wll.uuid, Wishlist(name = "Ma 1ere liste"), wll.owner.email)
  service.addIdea(Store, wll.uuid, wluuid, WishIdea(name = "My first idea"), "hayssam@saleh.fr")
  service.addIdea(Store, wll.uuid, wluuid, WishIdea(name = "My second idea"), "hayssam@saleh.fr")
  service.addItem(Store, wll.uuid, wluuid, WishItem(name = "My first item", product = "product-uuid"), "hayssam@saleh.fr")
  service.addItem(Store, wll.uuid, wluuid, WishItem(name = "My second item", product = "product-uuid"), "hayssam@saleh.fr")
  service.setOwnerInfo(Store, wll.uuid, WishlistOwner("hayssam@saleh.fr", Some("Hayssam Saleh"), Some(15), Some(9), Some("Qui suis-je ?")))
  service.addWishlist(Store, wll.uuid, Wishlist(name = "Ma deuxième liste"), wll.owner.email)

  service.removeIdea(Store, wll.uuid, wll.wishlists.head.uuid, wll.wishlists.head.ideas.head.uuid, "hayssam@saleh.fr")
  service.removeItem(Store, wll.uuid, wll.wishlists.head.uuid, wll.wishlists.head.items.head.uuid, "hayssam@saleh.fr")
  service.removeWishlist(Store, wll.uuid, wll.wishlists.head.uuid, "hayssam@saleh.fr")
}
