/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import java.util.Calendar
import com.mogobiz.es.EsClient
import com.mogobiz.run.exceptions.{ NotFoundException, NotAuthorizedException, DuplicateException }
import com.mogobiz.run.model._
import com.mogobiz.utils.GlobalUtil

import com.mogobiz.utils.GlobalUtil.newUUID
import com.mogobiz.run.exceptions.NotFoundException
import com.sksamuel.elastic4s.ElasticDsl._
import scalikejdbc.{ DB, DBSession }

import scala.util.{ Failure, Try, Success }

object WishlistHandler {
  def esStore(store: String) = s"${store}_wishlist"
}

class WishlistHandler {

  import WishlistHandler._

  def runInTransaction[U, T](store: String, wishlistListId: String, owneremail: String, call: (DBSession, BOWishList, WishlistList) => WishlistList, success: WishlistList => U): U = {
    GlobalUtil.runInTransaction({ implicit session: DBSession =>
      BOWishListDao.load(store, wishlistListId).map { boWishList =>
        EsClient.load[WishlistList](esStore(store), wishlistListId, "wishlistlist").map { wishlistList =>
          if (owneremail != wishlistList.owner.email) throw NotAuthorizedException("Not Authorized")
          call(session, boWishList, wishlistList)
        }.getOrElse(throw NotFoundException(s"Unknown wishlistList $wishlistListId"))
      }.getOrElse(throw NotFoundException(s"Unknown wishlistList $wishlistListId"))
    }, success)
  }

  def addItem(store: String, wishlistListId: String, wishlistId: String, item: WishItem, owneremail: String): String = {
    val transactionalBloc = { (session: DBSession, boWishList: BOWishList, wishlistList: WishlistList) =>
      wishlistList.wishlists.find(_.uuid == wishlistId).map { wishlist =>
        if (wishlist.items.exists { i: WishItem => (i.product == item.product && i.sku == item.sku) })
          throw new DuplicateException(s"${item.name}")

        val now = Calendar.getInstance().getTime
        val newWishList = wishlistList.copy(wishlists = wishlistList.wishlists.filter(_.uuid != wishlistId) :+ wishlist.copy(items = wishlist.items :+ item, lastUpdated = now))
        BOWishListDao.save(boWishList, newWishList)
        newWishList
      }.getOrElse(throw NotFoundException(s"Invalid wishlist uuid $wishlistId"))
    }
    val successBloc = { newWishList: WishlistList =>
      EsClient.update[WishlistList](esStore(store), newWishList, "wishlistlist", true, false)
      item.uuid
    }
    runInTransaction(store, wishlistListId, owneremail, transactionalBloc, successBloc)
  }

  def addBrand(store: String, wishlistListId: String, wishlistId: String, brand: WishBrand, owneremail: String): String = {
    val transactionalBloc = { (session: DBSession, boWishList: BOWishList, wishlistList: WishlistList) =>
      wishlistList.wishlists.find(_.uuid == wishlistId).map { wishlist =>
        if (wishlist.brands.exists(_.brand == brand.brand))
          throw new DuplicateException(s"${brand.name}")

        val now = Calendar.getInstance().getTime
        val newWishList = wishlistList.copy(wishlists = wishlistList.wishlists.filter(_.uuid != wishlistId) :+ wishlist.copy(brands = wishlist.brands :+ brand, lastUpdated = now))
        BOWishListDao.save(boWishList, newWishList)
        newWishList
      }.getOrElse(throw NotFoundException(s"Invalid wishlist uuid $wishlistId"))
    }
    val successBloc = { newWishList: WishlistList =>
      EsClient.update[WishlistList](esStore(store), newWishList, "wishlistlist", true, false)
      brand.uuid
    }
    runInTransaction(store, wishlistListId, owneremail, transactionalBloc, successBloc)
  }

  def addCategory(store: String, wishlistListId: String, wishlistId: String, cat: WishCategory, owneremail: String): String = {
    val transactionalBloc = { (session: DBSession, boWishList: BOWishList, wishlistList: WishlistList) =>
      wishlistList.wishlists.find(_.uuid == wishlistId).map { wishlist =>
        if (wishlist.categories.exists(_.category == cat.category))
          throw new DuplicateException(s"${cat.name}")

        val now = Calendar.getInstance().getTime
        val newWishList = wishlistList.copy(wishlists = wishlistList.wishlists.filter(_.uuid != wishlistId) :+ wishlist.copy(categories = wishlist.categories :+ cat, lastUpdated = now))
        BOWishListDao.save(boWishList, newWishList)
        newWishList
      }.getOrElse(throw NotFoundException(s"Invalid wishlist uuid $wishlistId"))
    }
    val successBloc = { newWishList: WishlistList =>
      EsClient.update[WishlistList](esStore(store), newWishList, "wishlistlist", true, false)
      cat.uuid
    }
    runInTransaction(store, wishlistListId, owneremail, transactionalBloc, successBloc)
  }

  def addIdea(store: String, wishlistListId: String, wishlistId: String, idea: WishIdea, owneremail: String): String = {
    val transactionalBloc = { (session: DBSession, boWishList: BOWishList, wishlistList: WishlistList) =>
      wishlistList.wishlists.find(_.uuid == wishlistId).map { wishlist =>
        val now = Calendar.getInstance().getTime
        val newWishList = wishlistList.copy(wishlists = wishlistList.wishlists.filter(_.uuid != wishlistId) :+ wishlist.copy(ideas = wishlist.ideas :+ idea, lastUpdated = now))
        BOWishListDao.save(boWishList, newWishList)
        newWishList
      }.getOrElse(throw NotFoundException(s"Invalid wishlist uuid $wishlistId"))
    }
    val successBloc = { newWishList: WishlistList =>
      EsClient.update[WishlistList](esStore(store), newWishList, "wishlistlist", true, false)
      idea.uuid
    }
    runInTransaction(store, wishlistListId, owneremail, transactionalBloc, successBloc)
  }

  def removeItem(store: String, wishlistListId: String, wishlistId: String, itemUuid: String, owneremail: String): Unit = {
    val transactionalBloc = { (session: DBSession, boWishList: BOWishList, wishlistList: WishlistList) =>
      wishlistList.wishlists.find(_.uuid == wishlistId).map { wishlist =>
        val now = Calendar.getInstance().getTime
        val newWishList = wishlistList.copy(wishlists = wishlistList.wishlists.filter(_.uuid != wishlist.uuid) :+ wishlist.copy(items = wishlist.items.filter(_.uuid != itemUuid), lastUpdated = now))
        BOWishListDao.save(boWishList, newWishList)
        newWishList
      }.getOrElse(throw NotFoundException(s"Invalid wishlist uuid $wishlistId"))
    }
    val successBloc = { newWishList: WishlistList =>
      EsClient.update[WishlistList](esStore(store), newWishList, "wishlistlist", true, false)
    }
    runInTransaction(store, wishlistListId, owneremail, transactionalBloc, successBloc)
  }

  def removeIdea(store: String, wishlistListId: String, wishlistId: String, ideaUuid: String, owneremail: String): Unit = {
    val transactionalBloc = { (session: DBSession, boWishList: BOWishList, wishlistList: WishlistList) =>
      wishlistList.wishlists.find(_.uuid == wishlistId).map { wishlist =>
        val now = Calendar.getInstance().getTime
        val newWishList = wishlistList.copy(wishlists = wishlistList.wishlists.filter(_.uuid != wishlist.uuid) :+ wishlist.copy(ideas = wishlist.ideas.filter(_.uuid != ideaUuid), lastUpdated = now))
        BOWishListDao.save(boWishList, newWishList)
        newWishList
      }.getOrElse(throw NotFoundException(s"Invalid wishlist uuid $wishlistId"))
    }
    val successBloc = { newWishList: WishlistList =>
      EsClient.update[WishlistList](esStore(store), newWishList, "wishlistlist", true, false)
    }
    runInTransaction(store, wishlistListId, owneremail, transactionalBloc, successBloc)
  }

  def removeBrand(store: String, wishlistListId: String, wishlistId: String, brandUuid: String, owneremail: String): Unit = {
    val transactionalBloc = { (session: DBSession, boWishList: BOWishList, wishlistList: WishlistList) =>
      wishlistList.wishlists.find(_.uuid == wishlistId).map { wishlist =>
        val now = Calendar.getInstance().getTime
        val newWishList = wishlistList.copy(wishlists = wishlistList.wishlists.filter(_.uuid != wishlist.uuid) :+ wishlist.copy(brands = wishlist.brands.filter(_.uuid != brandUuid), lastUpdated = now))
        BOWishListDao.save(boWishList, newWishList)
        newWishList
      }.getOrElse(throw NotFoundException(s"Invalid wishlist uuid $wishlistId"))
    }
    val successBloc = { newWishList: WishlistList =>
      EsClient.update[WishlistList](esStore(store), newWishList, "wishlistlist", true, false)
    }
    runInTransaction(store, wishlistListId, owneremail, transactionalBloc, successBloc)
  }

  def removeCategory(store: String, wishlistListId: String, wishlistId: String, catUuid: String, owneremail: String): Unit = {
    val transactionalBloc = { (session: DBSession, boWishList: BOWishList, wishlistList: WishlistList) =>
      wishlistList.wishlists.find(_.uuid == wishlistId).map { wishlist =>
        val now = Calendar.getInstance().getTime
        val newWishList = wishlistList.copy(wishlists = wishlistList.wishlists.filter(_.uuid != wishlist.uuid) :+ wishlist.copy(categories = wishlist.categories.filter(_.uuid != catUuid), lastUpdated = now))
        BOWishListDao.save(boWishList, newWishList)
        newWishList
      }.getOrElse(throw NotFoundException(s"Invalid wishlist uuid $wishlistId"))
    }
    val successBloc = { newWishList: WishlistList =>
      EsClient.update[WishlistList](esStore(store), newWishList, "wishlistlist", true, false)
    }
    runInTransaction(store, wishlistListId, owneremail, transactionalBloc, successBloc)
  }

  def setOwnerInfo(store: String, wishlistListId: String, owner: WishlistOwner): Unit = {
    val transactionalBloc = { (session: DBSession, boWishList: BOWishList, wishlistList: WishlistList) =>
      val newWishList = wishlistList.copy(owner = owner)
      BOWishListDao.save(boWishList, newWishList)
      newWishList
    }
    val successBloc = { newWishList: WishlistList =>
      EsClient.update[WishlistList](esStore(store), newWishList, "wishlistlist", true, false)
    }
    runInTransaction(store, wishlistListId, owner.email, transactionalBloc, successBloc)
  }

  def addWishlist(store: String, wishlistListId: String, wishlist: Wishlist, owneremail: String): String = {
    val transactionalBloc = { (session: DBSession, boWishList: BOWishList, wishlistList: WishlistList) =>
      if (wishlistList.wishlists.exists(_.name == wishlist.name)) throw new DuplicateException(s"${wishlist.name}")
      val useNewWishListAsDefault = wishlist.default || (wishlistList.wishlists.size == 0)
      val wishlistsWithUpdatedDefault = wishlistList.wishlists.map(ws => ws.copy(default = (!useNewWishListAsDefault && ws.default)))
      val now = Calendar.getInstance().getTime
      val newWishList = wishlistList.copy(wishlists = wishlistsWithUpdatedDefault :+ wishlist.copy(token = newUUID, default = useNewWishListAsDefault, dateCreated = now, lastUpdated = now))
      BOWishListDao.save(boWishList, newWishList)
      newWishList
    }
    val successBloc = { newWishList: WishlistList =>
      EsClient.update[WishlistList](esStore(store), newWishList, "wishlistlist", true, false)
      wishlist.uuid
    }
    runInTransaction(store, wishlistListId, owneremail, transactionalBloc, successBloc)
  }

  def removeWishlist(store: String, wishlistListId: String, wishlistId: String, owneremail: String): Unit = {
    val transactionalBloc = { (session: DBSession, boWishList: BOWishList, wishlistList: WishlistList) =>
      wishlistList.wishlists.find(_.uuid == wishlistId).map { wishlist =>
        def setFirstAsDefault(list: List[Wishlist]): List[Wishlist] = {
          if (list.isEmpty || !wishlist.default) list // List vide ou la liste supprimée n'était pas celle par défaut => aucune changement
          else list.tail :+ list.head.copy(default = true)
        }
        val now = Calendar.getInstance().getTime
        val newWishList = wishlistList.copy(wishlists = setFirstAsDefault(wishlistList.wishlists.filter(_.uuid != wishlistId)), lastUpdated = now)
        BOWishListDao.save(boWishList, newWishList)
        newWishList
      }.getOrElse(throw NotFoundException(s"Invalid wishlist uuid $wishlistId"))
    }
    val successBloc = { newWishList: WishlistList =>
      EsClient.update[WishlistList](esStore(store), newWishList, "wishlistlist", true, false)
    }
    runInTransaction(store, wishlistListId, owneremail, transactionalBloc, successBloc)
  }

  def setDefaultWishlist(store: String, wishlistListId: String, wishlistId: String, owneremail: String): Unit = {
    val transactionalBloc = { (session: DBSession, boWishList: BOWishList, wishlistList: WishlistList) =>
      wishlistList.wishlists.find(_.uuid == wishlistId).map { wishlist =>
        val now = Calendar.getInstance().getTime
        val newWishList = wishlistList.copy(wishlists = wishlistList.wishlists.map(ws => ws.copy(default = (ws.uuid == wishlist.uuid))), lastUpdated = now)
        BOWishListDao.save(boWishList, newWishList)
        newWishList
      }.getOrElse(throw NotFoundException(s"Invalid wishlist uuid $wishlistId"))
    }
    val successBloc = { newWishList: WishlistList =>
      EsClient.update[WishlistList](esStore(store), newWishList, "wishlistlist", true, false)
    }
    runInTransaction(store, wishlistListId, owneremail, transactionalBloc, successBloc)
  }

  def getWishlistList(store: String, owner_email: String, wishlistUuid: Option[String] = None): WishlistList = {
    val existingWishlistUuid = wishlistUuid.map { Some(_) }.getOrElse {
      val req = search in esStore(store) types "wishlistlist" query {
        filteredQuery query {
          matchAllQuery
        } filter {
          nestedFilter("owner") filter {
            termFilter("email", owner_email)
          }
        }
      }

      EsClient.search[WishlistList](req).map { _.uuid }
    }

    existingWishlistUuid.map { wishlistListId =>
      val transactionalBlock = { (session: DBSession, boWishList: BOWishList, wishlistList: WishlistList) => wishlistList }
      val successBlock = { newWishList: WishlistList => newWishList }
      runInTransaction(store, wishlistListId, owner_email, transactionalBlock, successBlock)
    }.getOrElse {
      val transactionalBlock = { implicit session: DBSession =>
        val wishlistList = WishlistList(newUUID, Nil, WishlistOwner(email = owner_email))
        BOWishListDao.create(store, wishlistList)
        wishlistList
      }
      val successBlock = { newWishList: WishlistList =>
        EsClient.update[WishlistList](esStore(store), newWishList, "wishlistlist", true, false)
        newWishList
      }
      GlobalUtil.runInTransaction(transactionalBlock, successBlock)
    }
  }

  def getWishlistToken(store: String, wishlistListId: String, wishlistId: String, ownerEmail: String): String = {
    val transactionalBloc = { (session: DBSession, boWishList: BOWishList, wishlistList: WishlistList) =>
      wishlistList.wishlists.find(_.uuid == wishlistId).map { wishlist =>
        if (wishlist.visibility == WishlistVisibility.PRIVATE) {
          val newWishlists = wishlistList.wishlists.filter(_.uuid != wishlistId) :+ wishlist.copy(visibility = WishlistVisibility.SHARED)
          val now = Calendar.getInstance().getTime
          val newWishList = wishlistList.copy(wishlists = newWishlists, lastUpdated = now)
          BOWishListDao.save(boWishList, newWishList)
          newWishList
        } else wishlistList
      }.getOrElse(throw NotFoundException(s"Invalid wishlist uuid $wishlistId"))
    }

    val successBloc = { newWishList: WishlistList =>
      EsClient.update[WishlistList](esStore(store), newWishList, "wishlistlist", true, false)
      val token = newWishList.wishlists.find(_.uuid == wishlistId).map { _.token }.getOrElse("")
      s"$store--${token}"
    }
    runInTransaction(store, wishlistListId, ownerEmail, transactionalBloc, successBloc)
  }

  def getWishlistByToken(token: String): Option[Wishlist] = {
    val tokens = token.split("--")
    val (store, wishlist) = (tokens(0), tokens(1))
    val req = search in esStore(store) types "wishlistlist" query {
      filteredQuery query {
        matchAllQuery
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

  val wll = service.getWishlistList(Store, "hayssam@saleh.fr", None)
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
