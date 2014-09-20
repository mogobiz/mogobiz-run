package com.mogobiz.services

import akka.actor.{ActorSystem, ActorRef}
import akka.util.Timeout
import spray.routing.Directives

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

/**
 * Created by hayssams on 19/09/14.
 */
class WishlistService(storeCode: String, actor: ActorRef)(implicit executionContext: ExecutionContext) extends Directives {
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
        getWishlistByToken
    }
  }

  lazy val addItem = path("add-item") {
    get {

      complete("ok")
    }

  }

  lazy val removeItem = path("remove-item") {
    complete("ok")
  }

  lazy val addIdea = path("add-idea") {
    complete("ok")
  }
  lazy val removeIdea = path("remove-idea") {
    complete("ok")
  }

  lazy val setOwnerInfo = path("set-owner-info") {
    complete("ok")
  }

  lazy val addWishlist = path("add-wishlist") {
    complete("ok")
  }

  lazy val removeWishlist = path("remove-wishlist") {
    complete("ok")
  }

  lazy val getWishlistList = path("get-wishlist") {
    complete("ok")
  }

  lazy val getWishlistToken = path("") {
    complete("ok")
  }

  lazy val getWishlistByToken = path("") {
    complete("ok")
  }


}
