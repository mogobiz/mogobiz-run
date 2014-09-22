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


  val serviceName = "wishlists"
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

  lazy val addItem = path("wishlist" / "item") {
    post {
      anyParams('wishlist_list_uuid, 'wishlist_uuid, 'item_name, 'product_uuid, 'owner_email) {
        (wishlist_list_uuid, wishlist_uuid, item_name, product_uuid, owner_email) =>
          onComplete((actor ? AddItemRequest(storeCode, wishlist_list_uuid, wishlist_uuid, WishItem(GlobalUtil.newUUID, item_name, product_uuid), owner_email)).mapTo[Try[String]]) { call =>
            handleComplete(call, (id: String) => complete(StatusCodes.OK, id))
          }
      }
    }
  }

  lazy val removeItem = path("wishlist" / "item" / Segment) { item_uuid =>
    delete {
      parameters('wishlist_list_uuid, 'wishlist_uuid, 'owner_email) {
        (wishlist_list_uuid, wishlist_uuid, owner_email) =>
          onComplete((actor ? RemoveItemRequest(storeCode, wishlist_list_uuid, wishlist_uuid, item_uuid, owner_email)).mapTo[Try[Unit]]) { call =>
            handleComplete(call, (x: Unit) => complete(StatusCodes.OK))
          }
      }
    }
  }


  lazy val addIdea = path("wishlist" / "idea") {
    post {
      anyParams('wishlist_list_uuid, 'wishlist_uuid, 'idea, 'owner_email) {
        (wishlist_list_uuid, wishlist_uuid, idea, owner_email) =>
          onComplete((actor ? AddIdeaRequest(storeCode, wishlist_list_uuid, wishlist_uuid, WishIdea(GlobalUtil.newUUID, idea), owner_email)).mapTo[Try[String]]) { call =>
            handleComplete(call, (id: String) => complete(StatusCodes.OK, id))
          }
      }
    }
  }

  lazy val removeIdea = path("wishlist" / "idea" / Segment) { idea_uuid =>
    delete {
      anyParams('wishlist_list_uuid, 'wishlist_uuid, 'owner_email) {
        (wishlist_list_uuid, wishlist_uuid, owner_email) =>
          onComplete((actor ? RemoveIdeaRequest(storeCode, wishlist_list_uuid, wishlist_uuid, idea_uuid, owner_email)).mapTo[Try[Unit]]) { call =>
            handleComplete(call, (x: Unit) => complete(StatusCodes.OK))
          }
      }
    }
  }

  /*
  Does not update email. owner email is used for security check only
   */
  lazy val setOwnerInfo = path("owner" / Segment) { wishlist_list_uuid =>
    post {
      anyParams('owner_email, 'owner_name.?, 'owner_day_of_birth.?.as[Option[Int]], 'owner_month_of_birth.?.as[Option[Int]], 'owner_description.?) {
        (owner_email, owner_name, owner_day_of_birth, owner_month_of_birth, owner_description) =>
          onComplete((actor ? SetOwnerInfoRequest(storeCode, wishlist_list_uuid, WishlistOwner(owner_email, owner_name, owner_day_of_birth, owner_month_of_birth, owner_description))).mapTo[Try[Unit]]) { call =>
            handleComplete(call, (x: Unit) => complete(StatusCodes.OK))
          }
      }
    }
  }

  lazy val addWishlist = path("wishlist") {
    post {
      anyParams('wishlist_list_uuid, 'wishlist_name, 'wishlist_visibility, 'wishlist_default.as[Boolean],
        'owner_email) {
        (wishlist_list_uuid, wishlist_name, wishlist_visibility, wishlist_default, owner_email) =>
          val wishlist = Wishlist(name = wishlist_name, visibility = WishlistVisibility.withName(wishlist_visibility), default = wishlist_default)
          onComplete((actor ? AddWishlistRequest(storeCode, wishlist_list_uuid, wishlist, owner_email)).mapTo[Try[String]]) { call =>
            handleComplete(call, (id: String) => complete(StatusCodes.OK, id))
          }
      }
    }
  }

  lazy val removeWishlist = path("wishlist" / Segment) { wishlist_uuid =>
    delete {
      anyParams('wishlist_list_uuid, 'owner_email) {
        (wishlist_list_uuid, owner_email) =>
          onComplete((actor ? RemoveWishlistRequest(storeCode, wishlist_list_uuid, wishlist_uuid, owner_email)).mapTo[Try[Unit]]) { call =>
            handleComplete(call, (x: Unit) => complete(StatusCodes.OK))
          }
      }
    }
  }

  lazy val getWishlistList = path(Segment) { owner_email =>
    get {
      onComplete((actor ? GetWishlistListRequest(storeCode, owner_email)).mapTo[Try[WishlistList]]) { call =>
        handleComplete(call, (id: WishlistList) => complete(StatusCodes.OK, id))
      }
    }
  }

  lazy val getWishlistToken = path("wishlist" / "token" / Segment) { wishlist_uuid =>
    get {
      parameters('wishlist_list_uuid, 'owner_email) { (wishlist_list_uuid, owner_email) =>
        onComplete((actor ? GetWishlistTokenRequest(storeCode, wishlist_list_uuid, wishlist_uuid, owner_email)).mapTo[Try[String]]) { call =>
          handleComplete(call, (id: String) => complete(StatusCodes.OK, id))
        }
      }
    }
  }

  lazy val getWishlistByToken = path("wishlist" / Segment) { token =>
    get {
      onComplete((actor ? GetWishlistByTokenRequest(token)).mapTo[Try[Option[Wishlist]]]) { call =>
        handleComplete(call, (wl: Option[Wishlist]) => complete(wl.map((StatusCodes.OK, _)).getOrElse((StatusCodes.NotFound, ""))))
      }
    }
  }

  lazy val setDefaultWishlist = path("wishlist" / "default" / Segment) { wishlist_uuid =>
    post {
      anyParams('wishlist_list_uuid, 'owner_email) {
        (wishlist_list_uuid, owner_email) =>
          onComplete((actor ? SetDefaultWishlistRequest(storeCode, wishlist_list_uuid, wishlist_uuid, owner_email)).mapTo[Try[Unit]]) { call =>
            handleComplete(call, (x: Unit) => complete(StatusCodes.OK))
          }
      }
    }
  }


}
