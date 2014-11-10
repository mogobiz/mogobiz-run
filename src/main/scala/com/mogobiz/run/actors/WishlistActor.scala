package com.mogobiz.run.actors

import akka.actor.Actor
import com.mogobiz.run.actors.WishlistActor._
import com.mogobiz.run.config.HandlersConfig
import HandlersConfig._
import com.mogobiz.run.model._

import scala.util.Try

object WishlistActor {

  case class AddItemRequest(store: String, wishlistListId: String, wishlistId: String, item: WishItem, owneremail: String)

  case class RemoveItemRequest(store: String, wishlistListId: String, wishlistId: String, itemUuid: String, owneremail: String)

  case class AddIdeaRequest(store: String, wishlistListId: String, wishlistId: String, idea: WishIdea, owneremail: String)

  case class RemoveIdeaRequest(store: String, wishlistListId: String, wishlistId: String, ideaUuid: String, owneremail: String)

  case class AddBrandRequest(store: String, wishlistListId: String, wishlistId: String, brand: WishBrand, owneremail: String)

  case class RemoveBrandRequest(store: String, wishlistListId: String, wishlistId: String, brandUuid: String, owneremail: String)

  case class AddCategoryRequest(store: String, wishlistListId: String, wishlistId: String, category: WishCategory, owneremail: String)

  case class RemoveCategoryRequest(store: String, wishlistListId: String, wishlistId: String, categoryUuid: String, owneremail: String)

  case class SetOwnerInfoRequest(store: String, wishlistListId: String, owner: WishlistOwner)

  case class AddWishlistRequest(store: String, wishlistListId: String, wishlist: Wishlist, owneremail: String)

  case class RemoveWishlistRequest(store: String, wishlistListId: String, wishlistId: String, owneremail: String)

  case class GetWishlistListRequest(store: String, owner_email: String, wishlistListId:Option[String] = None)

  case class GetWishlistTokenRequest(store: String, wishlistListId: String, wishlistId: String, ownerEmail: String)

  case class SetDefaultWishlistRequest(store: String, wishlistListId: String, wishlistId: String, ownerEmail: String)

  case class GetWishlistByTokenRequest(token: String)

}

class WishlistActor extends Actor {
  def receive = {
    case a: AddItemRequest =>
      sender ! Try(wishlistHandler.addItem(a.store, a.wishlistListId, a.wishlistId, a.item, a.owneremail))
    case r: RemoveItemRequest =>
      sender ! Try(wishlistHandler.removeItem(r.store, r.wishlistListId, r.wishlistId, r.itemUuid, r.owneremail))
    case a: AddIdeaRequest =>
      sender ! Try(wishlistHandler.addIdea(a.store, a.wishlistListId, a.wishlistId, a.idea, a.owneremail))
    case r: RemoveIdeaRequest =>
      sender ! Try(wishlistHandler.removeIdea(r.store, r.wishlistListId, r.wishlistId, r.ideaUuid, r.owneremail))
    case a: AddBrandRequest =>
      sender ! Try(wishlistHandler.addBrand(a.store, a.wishlistListId, a.wishlistId, a.brand, a.owneremail))
    case r: RemoveBrandRequest =>
      sender ! Try(wishlistHandler.removeBrand(r.store, r.wishlistListId, r.wishlistId, r.brandUuid, r.owneremail))
    case a: AddCategoryRequest =>
      sender ! Try(wishlistHandler.addCategory(a.store, a.wishlistListId, a.wishlistId, a.category, a.owneremail))
    case r: RemoveCategoryRequest =>
      sender ! Try(wishlistHandler.removeCategory(r.store, r.wishlistListId, r.wishlistId, r.categoryUuid, r.owneremail))
    case s: SetOwnerInfoRequest =>
      sender ! Try(wishlistHandler.setOwnerInfo(s.store, s.wishlistListId, s.owner))
    case a: AddWishlistRequest =>
      sender ! Try(wishlistHandler.addWishlist(a.store, a.wishlistListId, a.wishlist, a.owneremail))
    case r: RemoveWishlistRequest =>
      sender ! Try(wishlistHandler.removeWishlist(r.store, r.wishlistListId, r.wishlistId, r.owneremail))
    case g: GetWishlistListRequest =>
      sender ! Try(wishlistHandler.getWishlistList(g.store, g.owner_email, g.wishlistListId))
    case g: GetWishlistTokenRequest =>
      sender ! Try(wishlistHandler.getWishlistToken(g.store, g.wishlistListId, g.wishlistId, g.ownerEmail))
    case g: GetWishlistByTokenRequest =>
      sender ! Try(wishlistHandler.getWishlistByToken(g.token))
    case s: SetDefaultWishlistRequest =>
      sender ! Try(wishlistHandler.setDefaultWishlist(s.store, s.wishlistListId, s.wishlistId, s.ownerEmail))
  }
}