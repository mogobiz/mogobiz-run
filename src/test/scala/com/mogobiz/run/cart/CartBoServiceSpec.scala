package com.mogobiz.run.cart

import java.util.{Locale, UUID}

import com.fasterxml.jackson.databind.annotation.{JsonDeserialize, JsonSerialize}
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import com.mogobiz.MogobizRouteTest
import com.mogobiz.run.handlers.StoreCartDao
import com.mogobiz.run.json.{JodaDateTimeOptionDeserializer, JodaDateTimeOptionSerializer}
import com.mogobiz.run.model.Mogobiz.{ProductCalendar, ProductType, Shipping}
import com.mogobiz.run.model.Render.RegisteredCartItem
import com.mogobiz.run.model.{Currency, StoreCoupon, StoreCartItem, StoreCart}
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

    "compute cart" in {
      val storeCartItem1 = new StoreCartItem("1",
                                            135,
                                            "product",
                                            ProductType.PRODUCT,
                                            ProductCalendar.NO_DATE,
                                            137,
                                            "sku",
                                            2,
                                            1989,
                                            1791,
                                            None,
                                            None,
                                            List(),
                                            None)

      val storeCartItem2 = new StoreCartItem("2",
        135,
        "product",
        ProductType.PRODUCT,
        ProductCalendar.NO_DATE,
        137,
        "sku",
        3,
        1000,
        900,
        None,
        None,
        List(),
        None)

      val storeCoupon = new StoreCoupon(161, "Promotion")

      val storeCart = new StoreCart(STORE, "uuid", None, cartItems=List(storeCartItem1, storeCartItem2)) //, coupons = List(storeCoupon)

      val cart = CartBoService.computeStoreCart(storeCart, Some("FR"), None)

      cart.count mustEqual 5
      cart.price mustEqual 1791 * 2 + 900 * 3
      cart.endPrice must beSome((1791 * 2 + 1791 * 2 * 0.196).toLong + (900 * 3 + 900 * 3 * 0.196).toLong)
      cart.reduction mustEqual 0
      cart.finalPrice mustEqual (1791 * 2 + 1791 * 2 * 0.196).toLong + (900 * 3 + 900 * 3 * 0.196).toLong

      cart.cartItemVOs(1).price mustEqual 1989
      cart.cartItemVOs(1).salePrice mustEqual 1791
      cart.cartItemVOs(1).endPrice must beSome((1989 + 1989 * 0.196).toLong)
      cart.cartItemVOs(1).saleEndPrice must beSome((1791 + 1791 * 0.196).toLong)
      cart.cartItemVOs(1).totalPrice mustEqual 1989 * 2
      cart.cartItemVOs(1).saleTotalPrice mustEqual 1791 * 2
      cart.cartItemVOs(1).totalEndPrice must beSome((1989 * 2 + 1989 * 2 * 0.196).toLong)
      cart.cartItemVOs(1).saleTotalEndPrice must beSome((1791 * 2 + 1791 * 2 * 0.196).toLong)
      cart.cartItemVOs(1).tax must beSome(19.6f)

      cart.cartItemVOs(0).price mustEqual 1000
      cart.cartItemVOs(0).salePrice mustEqual 900
      cart.cartItemVOs(0).endPrice must beSome((1000 + 1000 * 0.196).toLong)
      cart.cartItemVOs(0).saleEndPrice must beSome((900 + 900 * 0.196).toLong)
      cart.cartItemVOs(0).totalPrice mustEqual 1000 * 3
      cart.cartItemVOs(0).saleTotalPrice mustEqual 900 * 3
      cart.cartItemVOs(0).totalEndPrice must beSome((1000 * 3 + 1000 * 3 * 0.196).toLong)
      cart.cartItemVOs(0).saleTotalEndPrice must beSome((900 * 3 + 900 * 3 * 0.196).toLong)
      cart.cartItemVOs(0).tax must beSome(19.6f)
    }


    "commit cart" in {
      val registerCartItem = new RegisteredCartItem("1",
        "1",
        "yoann.baudy@ebiznext.com",
        Some("Yoann"),
        None,
        None,
        None,
        None
      )

      val storeCartItem1 = new StoreCartItem("1",
        135,
        "product",
        ProductType.SERVICE,
        ProductCalendar.NO_DATE,
        137,
        "sku",
        2,
        1989,
        1791,
        None,
        None,
        List(registerCartItem),
        None)

      val storeCart = new StoreCart(STORE, "uuid", None, cartItems=List(storeCartItem1))
      StoreCartDao.save(storeCart)

      CartBoService.prepareBeforePayment(Some("FR"), None, "mon adresse de livraison en json", new Currency(2, 0.01, "Euro", "EUR"), storeCart, "yoann.baudy@ebiznext.com", Locale.FRENCH)

      val newStoreCart = StoreCartDao.findByDataUuidAndUserUuid("uuid", None)
      CartBoService.commit(newStoreCart.get, "Test " + UUID.randomUUID().toString, Locale.FRENCH)

      val finalStoreCart = StoreCartDao.findByDataUuidAndUserUuid("uuid", None)
      finalStoreCart must beSome
    }

  }
}
