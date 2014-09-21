package com.mogobiz.services

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import com.mogobiz.actors.WishlistActor._
import com.mogobiz.json.JsonSupport._
import com.mogobiz.model._
import com.mogobiz.utils.GlobalUtil
import spray.http.StatusCodes
import spray.routing.Directives

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Try

class WishlistService(storeCode: String, actor: ActorRef)(implicit executionContext: ExecutionContext) extends Directives with DefaultComplete {
  implicit val timeout = Timeout(10 seconds)

  implicit val system = ActorSystem()

  // execution context for futures


  val serviceName = "wishlist"
  val route = {
    pathPrefix(serviceName) {
      addItem ~
        removeItem ~
        addIdea ~
        removeIdea ~
        setOwnerInfo ~
        addWishlist ~
        removeWishlist ~
        getWishlistList ~
        getWishlistToken ~
        getWishlistByToken ~
        setDefaultWishlist
    }
  }

  lazy val addItem = path("add-item") {
    post {
      anyParams('wishlist_list_uuid, 'wishlist_uuid, 'item_name, 'product_uuid, 'owner_email) {
        (wishlist_list_uuid, wishlist_uuid, item_name, product_uuid, owner_email) =>
          onComplete((actor ? AddItemRequest(storeCode, wishlist_list_uuid, wishlist_uuid, WishItem(GlobalUtil.newUUID, item_name, product_uuid), owner_email)).mapTo[Try[String]]) { call =>
            handleComplete(call, (id: String) => complete(StatusCodes.OK, id))
          }
      }
    }
  }

  lazy val removeItem = path("remove-item") {
    (delete | get | post) {
      anyParams('wishlist_list_uuid, 'wishlist_uuid, 'item_nuuid, 'owner_email) {
        (wishlist_list_uuid, wishlist_uuid, item_uuid, owner_email) =>
          onComplete((actor ? RemoveItemRequest(storeCode, wishlist_list_uuid, wishlist_uuid, item_uuid, owner_email)).mapTo[Try[Unit]]) { call =>
            handleComplete(call, (x: Unit) => complete(StatusCodes.OK))
          }
      }
    }
  }


  lazy val addIdea = path("add-idea") {
    post {
      anyParams('wishlist_list_uuid, 'wishlist_uuid, 'idea, 'owner_email) {
        (wishlist_list_uuid, wishlist_uuid, idea, owner_email) =>
          onComplete((actor ? AddIdeaRequest(storeCode, wishlist_list_uuid, wishlist_uuid, WishIdea(GlobalUtil.newUUID, idea), owner_email)).mapTo[Try[String]]) { call =>
            handleComplete(call, (id: String) => complete(StatusCodes.OK, id))
          }
      }
    }
  }

  lazy val removeIdea = path("remove-idea") {
    (delete | get | post) {
      anyParams('wishlist_list_uuid, 'wishlist_uuid, 'idea_nuuid, 'owner_email) {
        (wishlist_list_uuid, wishlist_uuid, idea_uuid, owner_email) =>
          onComplete((actor ? RemoveIdeaRequest(storeCode, wishlist_list_uuid, wishlist_uuid, idea_uuid, owner_email)).mapTo[Try[Unit]]) { call =>
            handleComplete(call, (x: Unit) => complete(StatusCodes.OK))
          }
      }
    }
  }
  /*
  case class WishlistOwner(email: String, name: Option[String] = None, dayOfBirth: Option[Int] = None, monthOfBirth: Option[Int] = None, description: Option[String] = None)
   */
  lazy val setOwnerInfo = path("set-owner-info") {
    post {
      anyParams('wishlist_list_uuid, 'owner_email, 'owner_name.?, 'owner_day_of_birth.?.as[Option[Int]], 'owner_month_of_birth.?.as[Option[Int]], 'owner_description.?) {
        (wishlist_list_uuid, owner_email, owner_name, owner_day_of_birth, owner_month_of_birth, owner_description) =>
          onComplete((actor ? SetOwnerInfoRequest(storeCode, wishlist_list_uuid, WishlistOwner(owner_email, owner_name, owner_day_of_birth, owner_month_of_birth, owner_description))).mapTo[Try[Unit]]) { call =>
            handleComplete(call, (x: Unit) => complete(StatusCodes.OK))
          }
      }
    }
  }

  lazy val addWishlist = path("add-wishlist") {
    post {
      anyParams('wishlist_list_uuid, 'wishlist_name, 'wishlist_visibility, 'wishlist_default.as[Boolean],
        'owner_email, 'owner_name.?, 'owner_day_of_birth.?.as[Option[Int]], 'owner_month_of_birth.?.as[Option[Int]], 'owner_description.?) {
        (wishlist_list_uuid, wishlist_name, wishlist_visibility, wishlist_default,
         owner_email, owner_name, owner_day_of_birth, owner_month_of_birth, owner_description) =>
          val wishlist = Wishlist(name = wishlist_name, visibility = WishlistVisibility.withName(wishlist_visibility), default = wishlist_default)
          val owner = WishlistOwner(owner_email, owner_name, owner_day_of_birth, owner_month_of_birth, owner_description)
          onComplete((actor ? AddWishlistRequest(storeCode, wishlist_list_uuid, wishlist, owner)).mapTo[Try[String]]) { call =>
            handleComplete(call, (id: String) => complete(StatusCodes.OK, id))
          }
      }
    }
  }

  lazy val removeWishlist = path("remove-wishlist") {
    (delete | get | post) {
      anyParams('wishlist_list_uuid, 'wishlist_uuid, 'owner_email) {
        (wishlist_list_uuid, wishlist_uuid, owner_email) =>
          onComplete((actor ? RemoveWishlistRequest(storeCode, wishlist_list_uuid, wishlist_uuid, owner_email)).mapTo[Try[Unit]]) { call =>
            handleComplete(call, (x: Unit) => complete(StatusCodes.OK))
          }
      }
    }
  }

  lazy val getWishlistList = path("get-wishlists") {
    get {
      parameter('owner_email) { owner_email =>
        onComplete((actor ? GetWishlistListRequest(storeCode, owner_email)).mapTo[Try[WishlistList]]) { call =>
          handleComplete(call, (id: WishlistList) => complete(StatusCodes.OK, id))
        }
      }
    }
  }

  lazy val getWishlistToken = path("get-wishlist-token") {
    get {
      parameters('wishlist_list_uuid, 'wishlist_uuid, 'owner_email) { (wishlist_list_uuid, wishlist_uuid, owner_email) =>
        onComplete((actor ? GetWishlistTokenRequest(storeCode, wishlist_list_uuid, wishlist_uuid, owner_email)).mapTo[Try[String]]) { call =>
          handleComplete(call, (id: String) => complete(StatusCodes.OK, id))
        }
      }
    }
  }

  lazy val getWishlistByToken = path("get-wishlist-by-token") {
    get {
      parameter('token) { token =>
        onComplete((actor ? GetWishlistByTokenRequest(token)).mapTo[Try[Option[Wishlist]]]) { call =>
          handleComplete(call, (wl: Option[Wishlist]) => complete(wl.map((StatusCodes.OK, _)).getOrElse((StatusCodes.NotFound, ""))))
        }
      }
    }
  }

  lazy val setDefaultWishlist = path("set-default-wishlist") {
    (get | post) {
      anyParams('wishlist_list_uuid, 'wishlist_uuid, 'owner_email) {
        (wishlist_list_uuid, wishlist_uuid, owner_email) =>
          onComplete((actor ? SetDefaultWishlistRequest(storeCode, wishlist_list_uuid, wishlist_uuid, owner_email)).mapTo[Try[Unit]]) { call =>
            handleComplete(call, (x: Unit) => complete(StatusCodes.OK))
          }
      }
    }
  }


}
