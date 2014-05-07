package com.mogobiz.cart

import org.specs2.mutable.Specification
import java.util.{Locale, UUID}
import scalikejdbc.config.DBs

/**
 * Created by Christophe on 06/05/2014.
 */
class CartBoServiceSpec extends Specification {

  DBs.setupAll()

  val service = CartBoService

  "init cart" in {
    val uuid = UUID.randomUUID.toString
    println(s"uuid=${uuid}")

    val cart = service.initCart(uuid)

    cart.uuid must_==("")
    cart.finalPrice must_== 0
    cart.count must_== 0
    cart.cartItemVOs.length must_== 0
    cart.coupons.length must_==0
  }

  "addToCart" in {
    val cur = "EUR"
    val uuid = UUID.randomUUID.toString
    println(s"uuid=${uuid}")
    val cart = service.initCart(uuid)
    val ttid = 58
    val ticketType = TicketType.get(ttid)
    val quantity = 5
    val dateTime = None
    val resCart = service.addItem(Locale.getDefault,cur,cart,ticketType,quantity,dateTime, List())

    println(resCart.price)
    resCart.count must be_==(1)
    resCart.cartItemVOs.size must be_==(1)

  }
}
