package com.mogobiz.handlers

import java.util.Calendar

import com.mogobiz.es.EsClient
import com.mogobiz.model._
import com.mogobiz.utils.GlobalUtil._
import com.sksamuel.elastic4s.ElasticDsl._

import scala.util.{Failure, Success, Try}


object WishlistHandler {
  def esStore(store: String) = s"$store-wishlist"
}

class WishlistHandler {

  import com.mogobiz.handlers.WishlistHandler._

  def addItem(store: String, wishlistListId: String, wishlistId: String, item: WishItem, owneremail: String): Try[Unit] = {
    val wishlistList = EsClient.load[WishlistList](esStore(store), wishlistListId).getOrElse(throw new Exception(s"Unknown wishlistList $wishlistListId"))
    if (owneremail != wishlistList.owner.email)
      throw new Exception("Not Authorized")
    val wishlist = wishlistList.wishlists.find(_.uuid == wishlistId) getOrElse (throw new Exception(s"Invalid wishlist uuid $wishlistId"))
    if (wishlist.items.exists(_.name == item.name))
      Failure(new DuplicateException(s"${item.name}"))
    else {
      val now = Calendar.getInstance().getTime
      val res = wishlistList.copy(wishlists = wishlistList.wishlists :+ wishlist.copy(items = wishlist.items :+ item, lastUpdated = now))
      EsClient.index[WishlistList](esStore(store), res)
      Success(())
    }
  }
  def addBrand(store: String, wishlistListId: String, wishlistId: String, item: WishItem, owneremail: String): Try[Unit] = {
    val wishlistList = EsClient.load[WishlistList](esStore(store), wishlistListId).getOrElse(throw new Exception(s"Unknown wishlistList $wishlistListId"))
    if (owneremail != wishlistList.owner.email)
      throw new Exception("Not Authorized")
    val wishlist = wishlistList.wishlists.find(_.uuid == wishlistId) getOrElse (throw new Exception(s"Invalid wishlist uuid $wishlistId"))
    if (wishlist.items.exists(_.name == item.name))
      Failure(new DuplicateException(s"${item.name}"))
    else {
      val now = Calendar.getInstance().getTime
      val res = wishlistList.copy(wishlists = wishlistList.wishlists :+ wishlist.copy(items = wishlist.items :+ item, lastUpdated = now))
      EsClient.index[WishlistList](esStore(store), res)
      Success(())
    }
  }


  def removeItem(store: String, wishlistListId: String, wishlistId: String, itemUuid: String, owneremail: String): String = {
    val wishlistList = EsClient.load[WishlistList](esStore(store), wishlistListId).getOrElse(throw new Exception(s"Unknown wishlistList $wishlistListId"))
    if (owneremail != wishlistList.owner.email)
      throw new Exception("Not Authorized")
    val wishlist = wishlistList.wishlists.find(_.uuid == wishlistId) getOrElse (throw new Exception(s"Invalid wishlist uuid $wishlistId"))
    val now = Calendar.getInstance().getTime
    val res = wishlistList.copy(wishlists = wishlistList.wishlists.filter(_.uuid == wishlist.uuid) :+ wishlist.copy(items = wishlist.items.filter(_.uuid == itemUuid), lastUpdated = now))
    EsClient.index[WishlistList](esStore(store), res)
  }

  def addIdea(store: String, wishlistListId: String, wishlistId: String, idea: WishIdea, owneremail: String): String = {
    val wishlistList = EsClient.load[WishlistList](esStore(store), wishlistListId).getOrElse(throw new Exception(s"Unknown wishlistList $wishlistListId"))
    if (owneremail != wishlistList.owner.email)
      throw new Exception("Not Authorized")
    val wishlist = wishlistList.wishlists.find(_.uuid == wishlistId) getOrElse (throw new Exception(s"Invalid wishlist uuid $wishlistId"))
    val res = wishlistList.copy(wishlists = wishlistList.wishlists :+ wishlist.copy(ideas = wishlist.ideas :+ idea))
    EsClient.index[WishlistList](esStore(store), res)
  }

  def removeIdea(store: String, wishlistListId: String, wishlistId: String, ideaUuid: String, owneremail: String): String = {
    val wishlistList = EsClient.load[WishlistList](esStore(store), wishlistListId).getOrElse(throw new Exception(s"Unknown wishlistList $wishlistListId"))
    if (owneremail != wishlistList.owner.email)
      throw new Exception("Not Authorized")
    val wishlist = wishlistList.wishlists.find(_.uuid == wishlistId) getOrElse (throw new Exception(s"Invalid wishlist uuid $wishlistId"))
    val res = wishlistList.copy(wishlists = wishlistList.wishlists.filter(_.uuid == wishlist.uuid) :+ wishlist.copy(items = wishlist.items.filter(_.uuid == ideaUuid)))
    EsClient.index[WishlistList](esStore(store), res)
  }

  def setOwnerInfo(store: String, wishlistListId: String, owner: WishlistOwner): String = {
    val wishlistList: WishlistList = EsClient.load[WishlistList](esStore(store), wishlistListId).getOrElse(throw new Exception(s"Unknown wishlistList $wishlistListId"))
    if (owner.email != wishlistList.owner.email)
      throw new Exception("Not Authorized")
    EsClient.index[WishlistList](esStore(store), wishlistList.copy(owner = owner))
  }

  def addWishlist(store: String, wishlistListId: String, wishlist: Wishlist, owner: WishlistOwner): Try[Unit] = {
    val wishlistList = EsClient.load[WishlistList](esStore(store), wishlistListId).getOrElse(throw new Exception(s"Unknown wishlistList $wishlistListId"))
    if (wishlistList.wishlists.exists(_.name == wishlist.name))
      Failure(new DuplicateException(s"${wishlist.name}"))
    val now = Calendar.getInstance().getTime
    val default = if (wishlistList.wishlists.size == 0) true else wishlist.default
    EsClient.index[WishlistList](esStore(store), wishlistList.copy(wishlists = wishlistList.wishlists :+ wishlist.copy(token = newUUID, default = default, dateCreated = now, lastUpdated = now)))
    Success(())
  }

  def removeWishlist(store: String, wishlistListId: String, wishlistId: String, owneremail: String): Unit = {
    val wishlistList = EsClient.load[WishlistList](esStore(store), wishlistListId).getOrElse(throw new Exception(s"Unknown wishlistList $wishlistListId"))
    if (owneremail != wishlistList.owner.email)
      throw new Exception("Not Authorized")
    val res = wishlistList.copy(wishlists = wishlistList.wishlists.filter(_.uuid == wishlistId))
    EsClient.index[WishlistList](esStore(store), res)
  }

  def setDefaultWishlist(store: String, wishlistListId: String, wishlistId: String, owneremail: String): Unit = {
    val wishlistList = EsClient.load[WishlistList](esStore(store), wishlistListId).getOrElse(throw new Exception(s"Unknown wishlistList $wishlistListId"))
    if (owneremail != wishlistList.owner.email)
      throw new Exception("Not Authorized")
    val wishlist = wishlistList.wishlists.find(_.uuid == wishlistId).getOrElse(throw new Exception("Invalid wishlistId"))
    val wishlists = wishlistList.wishlists.map(_.copy(default = false)).filter(_.uuid == wishlist.uuid) :+ wishlist.copy(default=true)

    EsClient.update[WishlistList](esStore(store), wishlistList.copy(wishlists = wishlists))
  }

  def getWishlistList(store: String, owner: WishlistOwner): WishlistList = {
    val req = search in esStore(store) -> "WishlistList" filter {
      termFilter("owner.email", owner.email)
    }
    EsClient.search[WishlistList](req).getOrElse {
      val wishlistList = WishlistList(newUUID, Nil, owner)
      EsClient.index[WishlistList](esStore(store), wishlistList)
      wishlistList
    }
  }

  def getWishlistToken(store: String, wishlistListId: String, wishlistId: String, ownerEmail: String): String = {
    val wishlistList = EsClient.load[WishlistList](esStore(store), wishlistListId).getOrElse(throw new Exception(s"Unknown wishlistList $wishlistListId"))
    if (ownerEmail != wishlistList.owner.email)
      throw new Exception("Not Authorized")
    val wishlist = wishlistList.wishlists.find(_.uuid == wishlistId) getOrElse (throw new Exception(s"Invalid wishlist uuid $wishlistId"))
    if (wishlist.visibility == WishlistVisibility.PRIVATE) {
      val res = wishlistList.copy(wishlists = wishlistList.wishlists.filter(_.uuid == wishlistId))
      EsClient.update[WishlistList](esStore(store), res.copy(wishlists = wishlistList.wishlists :+ wishlist.copy(visibility = WishlistVisibility.SHARED)))
    }
    s"$store--${wishlist.token}"
  }

  def getWishlistByToken(token: String): Option[Wishlist] = {
    val tokens = token.split("--")
    val (store, wishlist) = (tokens(0), tokens(1))
    val req = search in esStore(store) -> "WishlistList" filter {
      termFilter("wishlists.token", wishlist)
    }
    EsClient.search[WishlistList](req) flatMap {
      _.wishlists.find(_.uuid == wishlist)
    }
  }
}

case class DuplicateException(message: String) extends Exception
