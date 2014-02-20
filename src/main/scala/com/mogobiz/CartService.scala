package com.mogobiz

import spray.routing.HttpService
import spray.http.MediaTypes._

/**
 * Created by Christophe on 17/02/14.
 */


trait CartService extends HttpService {

  import Json4sProtocol._

  val cartRoute = path("cart") {
    respondWithMediaType(`application/json`) {
      parameters(
        'cartId
        , 'storeCode
        , 'currencyCode
        , 'countryCode
        , 'lang).as(CartRequest) {
        cr =>
          complete {
            //TODO search with ES
            val cartItems = CartItem(1, 1000, 0.20f, endPrice = 20, totalPrice = 20, totalEndPrice = 20, hidden = false, startDate = "10/10/10", endDate = "12/12/12", quantity = 2) :: Nil
            cartItems
          }
      }
    }
  }

  val addCartItemRoute = path("addToCart") {
    respondWithMediaType(`application/json`) {
      parameters(
        'cartId
        , 'sskuId
        , 'dateTime
        , 'quantity
        , 'storeCode
        , 'currencyCode
        , 'countryCode
        , 'lang).as(AddCartRequest) {
        acir =>
          complete {
            //TODO search with ES
            val cartItems = CartItem(1, 1000, 0.20f, endPrice = 20, totalPrice = 20, totalEndPrice = 20, hidden = false, startDate = "10/10/10", endDate = "12/12/12", quantity = 2) :: Nil
            cartItems
          }
      }
    }
  }

  val updateCartRoute = path("updateCartItem") {
    respondWithMediaType(`application/json`) {
      parameters(
        'cartId
        , 'cartItemId
        , 'quantity
        , 'storeCode
        , 'currencyCode
        , 'countryCode
        , 'lang).as(UpdateCartItemRequest) {
        ucr =>
          complete {
            //TODO search with ES
            val cartItems = CartItem(1, 1000, 0.20f, endPrice = 20, totalPrice = 20, totalEndPrice = 20, hidden = false, startDate = "10/10/10", endDate = "12/12/12", quantity = 2) :: Nil
            cartItems
          }
      }
    }
  }


  val removeCartRoute = path("removeCartItem") {
    respondWithMediaType(`application/json`) {
      parameters(
        'cartId
        , 'cartItemId
        , 'quantity
        , 'storeCode
        , 'currencyCode
        , 'countryCode
        , 'lang).as(RemoveCartItemRequest) {
        rcr =>
          complete {
            //TODO search with ES
            val cartItems = CartItem(1, 1000, 0.20f, endPrice = 20, totalPrice = 20, totalEndPrice = 20, hidden = false, startDate = "10/10/10", endDate = "12/12/12", quantity = 2) :: Nil
            cartItems
          }
      }
    }
  }

  val prepareCartRoute = path("prepareCart") {
    respondWithMediaType(`application/json`) {
      parameters(
        'storeCode
        , 'currencyCode
        , 'countryCode
        , 'lang).as(PrepareCartRequest) {
        pcr =>
          complete {
            //TODO search with ES
            "OK (CART LOCKED)"
          }
      }
    }
  }


  val commitCartRoute = path("commitCart") {
    respondWithMediaType(`application/json`) {
      parameters(
        'cartId
        ,'transcationUUID
        ,'storeCode
        , 'countryCode
        , 'lang).as(CommitCartRequest) {
        ccr =>
          complete {
            //TODO search with ES
            "OK"
          }
      }
    }
  }


  val cancelCartRoute = path("cancelCart") {
    respondWithMediaType(`application/json`) {
      parameters(
        'storeCode
        , 'countryCode
        , 'lang).as(CancelCartRequest) {
        ccr =>
          complete {
            //TODO search with ES
            "CANCELED"
          }
      }
    }
  }

  val cartRoutes =
    cartRoute ~
      addCartItemRoute ~
      removeCartRoute ~
      prepareCartRoute ~
      commitCartRoute ~
      cancelCartRoute
}
