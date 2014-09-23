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

  lazy val addItem = path(Segment / "wishlist" / Segment / "item") { (wishlist_list_uuid, wishlist_uuid) =>
    post {
      entity(as[AddItemCommand]) { cmd =>
        val wishlist = WishItem(GlobalUtil.newUUID, cmd.name, cmd.product)
        onComplete((actor ? AddItemRequest(storeCode, wishlist_list_uuid, wishlist_uuid, wishlist, cmd.owner_email)).mapTo[Try[String]]) { call =>
          handleComplete(call, (id: String) => complete(StatusCodes.OK, Map("result" -> id)))
        }
      }
    }
  }

  lazy val removeItem = path(Segment / "wishlist" / Segment / "item" / Segment) { (wishlist_list_uuid, wishlist_uuid, item_uuid) =>
    delete {
      parameters('owner_email) { owner_email =>
          onComplete((actor ? RemoveItemRequest(storeCode, wishlist_list_uuid, wishlist_uuid, item_uuid, owner_email)).mapTo[Try[Unit]]) { call =>
            handleComplete(call, (x: Unit) => complete(StatusCodes.OK))
          }
      }
    }
  }


  lazy val addIdea = path(Segment / "wishlist" / Segment / "idea") { (wishlist_list_uuid, wishlist_uuid) =>
    post {
      parameters('idea, 'owner_email) { (idea, owner_email) =>
          onComplete((actor ? AddIdeaRequest(storeCode, wishlist_list_uuid, wishlist_uuid, WishIdea(GlobalUtil.newUUID, idea), owner_email)).mapTo[Try[String]]) { call =>
            handleComplete(call, (id: String) => complete(StatusCodes.OK, Map("result" -> id)))
          }
      }
    }
  }

  lazy val removeIdea = path(Segment / "wishlist" / Segment / "idea" / Segment) { (wishlist_list_uuid, wishlist_uuid, idea_uuid) =>
    delete {
      parameters('owner_email) {
        (owner_email) =>
          onComplete((actor ? RemoveIdeaRequest(storeCode, wishlist_list_uuid, wishlist_uuid, idea_uuid, owner_email)).mapTo[Try[Unit]]) { call =>
            handleComplete(call, (x: Unit) => complete(StatusCodes.OK))
          }
      }
    }
  }

  /*
  Does not update email. owner email is used for security check only
   */
  lazy val setOwnerInfo = path(Segment / "owner") { wishlist_list_uuid =>
    post {
      parameters('owner_email, 'owner_name.?, 'owner_day_of_birth.?.as[Option[Int]], 'owner_month_of_birth.?.as[Option[Int]], 'owner_description.?) {
        (owner_email, owner_name, owner_day_of_birth, owner_month_of_birth, owner_description) =>
          onComplete((actor ? SetOwnerInfoRequest(storeCode, wishlist_list_uuid, WishlistOwner(owner_email, owner_name, owner_day_of_birth, owner_month_of_birth, owner_description))).mapTo[Try[Unit]]) { call =>
            handleComplete(call, (x: Unit) => complete(StatusCodes.OK))
          }
      }
    }
  }

  lazy val addWishlist = path(Segment / "wishlist") { wishlist_list_uuid =>
    post {
      entity(as[AddWishlistCommand]) { cmd =>
        val wishlist = Wishlist(name = cmd.name, visibility = cmd.visibility, default = cmd.defaultIndicator)
        onComplete((actor ? AddWishlistRequest(storeCode, wishlist_list_uuid, wishlist, cmd.owner_email)).mapTo[Try[String]]) { call =>
          handleComplete(call, (id: String) => complete(StatusCodes.OK, Map("result" -> id)))
        }
      }
    }
  }

  lazy val removeWishlist = path(Segment / "wishlist" / Segment) { (wishlist_list_uuid, wishlist_uuid) =>
    delete {
      parameters('owner_email) { owner_email =>
          onComplete((actor ? RemoveWishlistRequest(storeCode, wishlist_list_uuid, wishlist_uuid, owner_email)).mapTo[Try[Unit]]) { call =>
            handleComplete(call, (x: Unit) => complete(StatusCodes.OK))
          }
      }
    }
  }

  lazy val getWishlistList = pathEnd {
    get {
      parameters('owner_email) { owner_email =>
          onComplete((actor ? GetWishlistListRequest(storeCode, owner_email)).mapTo[Try[WishlistList]]) { call =>
            handleComplete(call, (id: WishlistList) => complete(StatusCodes.OK, Map("result" -> id)))
          }
      }
    }
  }

  lazy val getWishlistToken = path(Segment / "wishlist" / Segment / "token") { (wishlist_list_uuid, wishlist_uuid) =>
    get {
      parameters('owner_email) { owner_email =>
        onComplete((actor ? GetWishlistTokenRequest(storeCode, wishlist_list_uuid, wishlist_uuid, owner_email)).mapTo[Try[String]]) { call =>
          handleComplete(call, (id: String) => complete(StatusCodes.OK, Map("result" -> id)))
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

  lazy val setDefaultWishlist = path(Segment / "wishlist" / Segment / "default") { (wishlist_list_uuid, wishlist_uuid) =>
    post {
      parameters('owner_email) { owner_email =>
          onComplete((actor ? SetDefaultWishlistRequest(storeCode, wishlist_list_uuid, wishlist_uuid, owner_email)).mapTo[Try[Unit]]) { call =>
            handleComplete(call, (x: Unit) => complete(StatusCodes.OK))
          }
      }
    }
  }


}
