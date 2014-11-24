package com.mogobiz.run.cart

import com.fasterxml.jackson.databind.annotation.{JsonDeserialize, JsonSerialize}
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import com.mogobiz.MogobizRouteTest
import com.mogobiz.run.cart.ProductCalendar._
import com.mogobiz.run.cart.ProductType._
import com.mogobiz.run.handlers.StoreCartDao
import com.mogobiz.run.json.{JodaDateTimeOptionDeserializer, JodaDateTimeOptionSerializer}
import com.mogobiz.run.model.Mogobiz.Shipping
import com.mogobiz.run.model.{StoreCartItem, StoreCart}
import org.joda.time.DateTime

/**
 * Created by yoannbaudy on 22/11/2014.
 */
class CartBoServiceSpec extends MogobizRouteTest {

  "CartBoService" should {

    "init cart for anonyme user" in {
      val cart = CartBoService.initCart(STORE, "uuid-anonyme", None)
      cart.storeCode mustEqual STORE
      cart.uuid mustEqual "uuid-anonyme--None"
      cart.dataUuid mustEqual "uuid-anonyme"
      cart.userUuid must beNone
      cart.cartItems must size(0)
    }

    "init cart for connected user without anonyme cart" in {
      val cart = CartBoService.initCart(STORE, "uuid-connected", Some("useruuid"))
      cart.storeCode mustEqual STORE
      cart.uuid mustEqual "uuid-connected--useruuid"
      cart.dataUuid mustEqual "uuid-connected"
      cart.userUuid must beSome("useruuid")
      cart.cartItems must size(0)
    }

    "init cart for connected user with anonyme cart" in {
      val cartItem = new StoreCartItem("1", 1, "product", ProductType.PRODUCT, ProductCalendar.NO_DATE,
      2, "sku", 1, 1000, 750, None, None, List(), None)
      val anonymCart = new StoreCart(STORE, "uuid", None, cartItems = List(cartItem))
      StoreCartDao.save(anonymCart)

      val cart = CartBoService.initCart(STORE, "uuid", Some("useruuid"))
      cart.storeCode mustEqual STORE
      cart.uuid mustEqual "uuid--useruuid"
      cart.dataUuid mustEqual "uuid"
      cart.userUuid must beSome("useruuid")
      cart.cartItems must size(1)
    }

  }
}
