package com.mogobiz.actors

import akka.actor.Actor
import com.mogobiz.actors.WishlistActor._
import com.mogobiz.config.HandlersConfig._
import com.mogobiz.model.{WishIdea, WishItem, Wishlist, WishlistOwner}

import scala.util.Try

object WishlistActor {

  case class AddItemRequest(store: String, wishlistListId: String, wishlistId: String, item: WishItem, owneremail: String)

  case class RemoveItemRequest(store: String, wishlistListId: String, wishlistId: String, itemUuid: String, owneremail: String)

  case class AddIdeaRequest(store: String, wishlistListId: String, wishlistId: String, idea: WishIdea, owneremail: String)

  case class RemoveIdeaRequest(store: String, wishlistListId: String, wishlistId: String, ideaUuid: String, owneremail: String)

  case class SetOwnerInfoRequest(store: String, wishlistListId: String, owner: WishlistOwner)

  case class AddWishlistRequest(store: String, wishlistListId: String, wishlist: Wishlist, owner: WishlistOwner)

  case class RemoveWishlistRequest(store: String, wishlistListId: String, wishlistId: String, owneremail: String)

  case class GetWishlistListRequest(store: String, owner: WishlistOwner)

  case class GetWishlistTokenRequest(store: String, wishlistListId: String, wishlistId: String, ownerEmail: String)

  case class SetDefaultWishlistRequest(store: String, wishlistListId: String, wishlistId: String, ownerEmail: String)

  case class GetWishlistByTokenRequest(token: String)

}

class WishlistActor extends Actor {
  def receive = {
    case a: AddItemRequest =>
      sender ! wishlistHandler.addItem(a.store, a.wishlistListId, a.wishlistId, a.item, a.owneremail)
    case r: RemoveItemRequest =>
      sender ! Try(wishlistHandler.removeItem(r.store, r.wishlistListId, r.wishlistId, r.itemUuid, r.owneremail))
    case a: AddIdeaRequest =>
      sender ! wishlistHandler.addIdea(a.store, a.wishlistListId, a.wishlistId, a.idea, a.owneremail)
    case r: RemoveIdeaRequest =>
      sender ! Try(wishlistHandler.removeItem(r.store, r.wishlistListId, r.wishlistId, r.ideaUuid, r.owneremail))
    case s: SetOwnerInfoRequest =>
      sender ! Try(wishlistHandler.setOwnerInfo(s.store, s.wishlistListId, s.owner))
    case a: AddWishlistRequest =>
      sender ! Try(wishlistHandler.addWishlist(a.store, a.wishlistListId, a.wishlist, a.owner))
    case r: RemoveWishlistRequest =>
      sender ! Try(wishlistHandler.removeWishlist(r.store, r.wishlistListId, r.wishlistId, r.owneremail))
    case g: GetWishlistListRequest =>
      sender ! Try(wishlistHandler.getWishlistList(g.store, g.owner))
    case g: GetWishlistTokenRequest =>
      sender ! Try(wishlistHandler.getWishlistToken(g.store, g.wishlistListId, g.wishlistId, g.ownerEmail))
    case g: GetWishlistByTokenRequest =>
      sender ! Try(wishlistHandler.getWishlistByToken(g.token))
    case s: SetDefaultWishlistRequest =>
      sender ! Try(wishlistHandler.setDefaultWishlist(s.store, s.wishlistListId, s.wishlistId, s.ownerEmail))
  }
}
