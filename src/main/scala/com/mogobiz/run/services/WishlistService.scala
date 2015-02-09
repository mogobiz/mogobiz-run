package com.mogobiz.run.services

import akka.actor.{ActorRef}
import akka.pattern.ask
import akka.util.Timeout
import com.mogobiz.run.config.HandlersConfig._
import com.mogobiz.run.implicits.JsonSupport._
import com.mogobiz.run.model._
import com.mogobiz.utils.GlobalUtil
import spray.http.StatusCodes
import spray.routing.Directives

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Try

class WishlistService(storeCode: String)(implicit executionContext: ExecutionContext) extends Directives with DefaultComplete {
  implicit val timeout = Timeout(10 seconds)


  val serviceName = "wishlists"
  val route = {
    pathPrefix(serviceName) {
      addItem ~
        removeItem ~
        addIdea ~
        removeIdea ~
        addBrand ~
        removeBrand ~
        addCategory ~
        removeCategory ~
        setOwnerInfo ~
        addWishlist ~
        removeWishlist ~
        getWishlistList ~
        getWishlistToken ~
        getWishlistByToken ~
        setDefaultWishlist
    }
  }

  lazy val addItem = path(Segment / "wishlist" / Segment / "item") { (wishlist_list_uuid, wishlist_uuid) =>
    post {
      entity(as[AddItemCommand]) { cmd =>
        val wishItem = WishItem(GlobalUtil.newUUID, cmd.name, cmd.product, cmd.product_sku)
        handleCall(wishlistHandler.addItem(storeCode, wishlist_list_uuid, wishlist_uuid, wishItem, cmd.owner_email),
          (id: String) => complete(StatusCodes.OK, id))
      }
    }
  }

  lazy val removeItem = path(Segment / "wishlist" / Segment / "item" / Segment) { (wishlist_list_uuid, wishlist_uuid, item_uuid) =>
    delete {
      parameters('owner_email) { owner_email =>
        handleCall(wishlistHandler.removeItem(storeCode, wishlist_list_uuid, wishlist_uuid, item_uuid, owner_email),
          (x: Unit) => complete(StatusCodes.OK))
      }
    }
  }


  lazy val addIdea = path(Segment / "wishlist" / Segment / "idea") { (wishlist_list_uuid, wishlist_uuid) =>
    post {
      parameters('idea, 'owner_email) { (idea, owner_email) =>
        handleCall(wishlistHandler.addIdea(storeCode, wishlist_list_uuid, wishlist_uuid, WishIdea(GlobalUtil.newUUID, idea), owner_email),
          (id: String) => complete(StatusCodes.OK, id))
      }
    }
  }

  lazy val removeIdea = path(Segment / "wishlist" / Segment / "idea" / Segment) { (wishlist_list_uuid, wishlist_uuid, idea_uuid) =>
    delete {
      parameters('owner_email) {
        (owner_email) =>
          handleCall(wishlistHandler.removeIdea(storeCode, wishlist_list_uuid, wishlist_uuid, idea_uuid, owner_email),
            (x: Unit) => complete(StatusCodes.OK))
      }
    }
  }

  lazy val addBrand = path(Segment / "wishlist" / Segment / "brand") {
    (wishlist_list_uuid, wishlist_uuid) =>
      post {
        entity(as[AddBrandCommand]) {
          cmd =>
            handleCall(wishlistHandler.addBrand(storeCode, wishlist_list_uuid, wishlist_uuid, WishBrand(GlobalUtil.newUUID, cmd.name, cmd.brand), cmd.owner_email),
              (id: String) => complete(StatusCodes.OK, id))
        }
      }
  }

  lazy val removeBrand = path(Segment / "wishlist" / Segment / "brand" / Segment) {
    (wishlist_list_uuid, wishlist_uuid, brand_uuid) =>
      delete {
        parameters('owner_email) {
          (owner_email) =>
            handleCall(wishlistHandler.removeBrand(storeCode, wishlist_list_uuid, wishlist_uuid, brand_uuid, owner_email),
              (x: Unit) => complete(StatusCodes.OK))
        }
      }
  }

  lazy val addCategory = path(Segment / "wishlist" / Segment / "category") {
    (wishlist_list_uuid, wishlist_uuid) =>
      post {
        entity(as[AddCategoryCommand]) {
          cmd =>
            handleCall(wishlistHandler.addCategory(storeCode, wishlist_list_uuid, wishlist_uuid, WishCategory(GlobalUtil.newUUID, cmd.name, cmd.category), cmd.owner_email),
              (id: String) => complete(StatusCodes.OK, id))
        }
      }
  }

  lazy val removeCategory = path(Segment / "wishlist" / Segment / "category" / Segment) {
    (wishlist_list_uuid, wishlist_uuid, category_uuid) =>
      delete {
        parameters('owner_email) {
          (owner_email) =>
            handleCall(wishlistHandler.removeCategory(storeCode, wishlist_list_uuid, wishlist_uuid, category_uuid, owner_email),
              (x: Unit) => complete(StatusCodes.OK))
        }
      }
  }

  /*
  Does not update email. owner email is used for security check only
   */
  lazy val setOwnerInfo = path(Segment / "owner") {
    wishlist_list_uuid =>
      post {
        parameters('owner_email, 'owner_name.?, 'owner_day_of_birth.?.as[Option[Int]], 'owner_month_of_birth.?.as[Option[Int]], 'owner_description.?) {
          (owner_email, owner_name, owner_day_of_birth, owner_month_of_birth, owner_description) =>
            handleCall(wishlistHandler.setOwnerInfo(storeCode, wishlist_list_uuid, WishlistOwner(owner_email, owner_name, owner_day_of_birth, owner_month_of_birth, owner_description)),
              (x: Unit) => complete(StatusCodes.OK))
        }
      }
  }

  lazy val addWishlist = path(Segment / "wishlist") {
    wishlist_list_uuid =>
      post {
        entity(as[AddWishlistCommand]) {
          cmd =>
            val wishlist = Wishlist(name = cmd.name, visibility = cmd.visibility, default = cmd.defaultIndicator)
            handleCall(wishlistHandler.addWishlist(storeCode, wishlist_list_uuid, wishlist, cmd.owner_email),
              (id: String) => complete(StatusCodes.OK, id))
        }
      }
  }

  lazy val removeWishlist = path(Segment / "wishlist" / Segment) {
    (wishlist_list_uuid, wishlist_uuid) =>
      delete {
        parameters('owner_email) {
          owner_email =>
            handleCall(wishlistHandler.removeWishlist(storeCode, wishlist_list_uuid, wishlist_uuid, owner_email),
              (x: Unit) => complete(StatusCodes.OK))
        }
      }
  }

  lazy val getWishlistList = pathEnd {
    get {
      parameters('owner_email, 'wishlist_uuid.?) {
        (owner_email, wishlist_uuid) =>
          handleCall(wishlistHandler.getWishlistList(storeCode, owner_email, wishlist_uuid),
            (id: WishlistList) => complete(StatusCodes.OK, id))
      }
    }
  }

  lazy val getWishlistToken = path(Segment / "wishlist" / Segment / "token") {
    (wishlist_list_uuid, wishlist_uuid) =>
      get {
        parameters('owner_email) {
          owner_email =>
            handleCall(wishlistHandler.getWishlistToken(storeCode, wishlist_list_uuid, wishlist_uuid, owner_email),
              (id: String) => complete(StatusCodes.OK, id))
        }
      }
  }

  lazy val getWishlistByToken = path("wishlist" / Segment) {
    token =>
      get {
        handleCall(wishlistHandler.getWishlistByToken(token),
          (wl: Option[Wishlist]) => complete(wl.map((StatusCodes.OK, _)).getOrElse((StatusCodes.NotFound, ""))))
      }
  }

  lazy val setDefaultWishlist = path(Segment / "wishlist" / Segment / "default") {
    (wishlist_list_uuid, wishlist_uuid) =>
      post {
        parameters('owner_email) {
          owner_email =>
            handleCall(wishlistHandler.setDefaultWishlist(storeCode, wishlist_list_uuid, wishlist_uuid, owner_email),
              (x: Unit) => complete(StatusCodes.OK))
        }
      }
  }
}
