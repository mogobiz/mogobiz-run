package com.mogobiz.cart

import org.specs2.mutable.Specification
import java.util.{Locale, UUID}
import scalikejdbc.config.DBs
import com.mogobiz.Currency

/**
 * Created by Christophe on 06/05/2014.
 */
class CartBoServiceSpec extends Specification {

  DBs.setupAll()

  val service = CartBoService

  "init cart" in {
    skipped
    val uuid = UUID.randomUUID.toString
    println(s"uuid=${uuid}")

    val cart = service.initCart(uuid)

    cart.uuid must_==("")
    cart.finalPrice must_== 0
    cart.count must_== 0
    cart.cartItemVOs.length must_== 0
    cart.coupons.length must_==0
  }

  "addToCart a product" in {
    skipped
    val cur = "EUR"
    val uuid = UUID.randomUUID.toString
    println(s"uuid=${uuid}")
    val cart = service.initCart(uuid)
    val ttid = 58
    //val ticketType = TicketType.get(ttid)
    val quantity = 5
    val dateTime = None
    val resCart = service.addItem(Locale.getDefault,cur,cart,ttid,quantity,dateTime, List())

    val item = resCart.cartItemVOs(0)

    println("cart.price="+resCart.price)
    resCart.count must be_==(1)
    resCart.cartItemVOs.size must be_==(1)
    item.shipping must beSome[ShippingVO]
    val shipping = item.shipping.get
    shipping.id must_==(55)
    shipping.weight must_==(25)
    shipping.free must beFalse
    shipping.amount must_==(0)

  }

  "addToCart should fail if quantity is too much" in {
    skipped
    val cur = "EUR"
    val uuid = UUID.randomUUID.toString
    println(s"uuid=${uuid}")
    val cart = service.initCart(uuid)
    val ttid = 58
    //val ticketType = TicketType.get(ttid)
    val quantity = 100
    val dateTime = None
    service.addItem(Locale.getDefault,cur,cart,ttid,quantity,dateTime, List()) must throwA[AddCartItemException]

    /*
    service.addItem(Locale.getDefault,cur,cart,ttid,quantity,dateTime, List()) must throwA[Exception] like {
      case AddCartItemException(errors) => errors.contains("AddCartItemException")
      case _ => false
    } must beTrue
    */

  }



  "addToCart a product + another" in {
    skipped
    val cur = "EUR"
    val uuid = UUID.randomUUID.toString
    println(s"uuid=${uuid}")
    val cart = service.initCart(uuid)
    val ttid58 = 58
    val tt58 = TicketType.get(ttid58)
    val quantity = 1
    val dateTime = None
    val resCart = service.addItem(Locale.getDefault,cur,cart,ttid58,quantity,dateTime, List())

    println("1. cart.price="+resCart.price)
    resCart.price must be_==(tt58.price)
    resCart.price must be_==(35000)
    resCart.count must be_==(1)
    resCart.cartItemVOs.size must be_==(1)

    val ttid51 = 51
    val tt51 = TicketType.get(ttid51)
    val resCart2 = service.addItem(Locale.getDefault,cur,resCart,ttid51,quantity,dateTime, List())

    println("2. cart.price="+resCart2.price)
    resCart2.price must be_==(tt58.price+tt51.price)
    resCart2.price must be_==(35000+30000)
    resCart2.count must be_==(2)
    resCart2.cartItemVOs.size must be_==(2)

  }

  "addToCart the same product twice" in {
    skipped
    //FIXME fail("ne gere pas l'ajout du mm produit au panier")
    val cur = "EUR"
    val uuid = UUID.randomUUID.toString
    println(s"uuid=${uuid}")
    val cart = service.initCart(uuid)
    val ttid = 58
    val tt58 = TicketType.get(ttid)
    val quantity = 1
    val dateTime = None
    val resCart = service.addItem(Locale.getDefault, cur, cart, ttid, quantity, dateTime, List())

    println("1. cart.price="+resCart.price)
    resCart.price must be_==(tt58.price)
    resCart.price must be_==(35000)
    resCart.count must be_==(1)
    resCart.cartItemVOs.size must be_==(1)

    val resCart2 = service.addItem(Locale.getDefault, cur, resCart, ttid, quantity, dateTime, List())
    println("2. cart.price="+resCart2.price)
    resCart2.price must be_==(tt58.price*2)
    resCart2.price must be_==(35000*2)

    resCart2.count must be_==(1) //TODO fail here
    resCart2.cartItemVOs.size must be_==(1)

  }


    "addToCart a service" in {
    //TODO ticket de cinéma
    skipped

  }

  "updateCart change the quantity" in {
    skipped
    //took 5 and update to 1
    val cur = "EUR"
    val uuid = UUID.randomUUID.toString
    println(s"uuid=${uuid}")
    val cart = service.initCart(uuid)
    val ttid58 = 58
    val tt58 = TicketType.get(ttid58)
    println(tt58)
    val quantity = 5
    val dateTime = None
    //try{
    val resCart = service.addItem(Locale.getDefault,cur,cart,ttid58,quantity,dateTime, List())
    println("1. cart.price="+resCart.price)
    resCart.price must be_==(tt58.price*5)
    resCart.price must be_==(35000*5)
    resCart.count must be_==(1)
    resCart.cartItemVOs.size must be_==(1)

    val itemId = resCart.cartItemVOs(0).id

    val resCart2 = service.updateItem(Locale.getDefault(),cur,resCart,itemId,1)
    println("2. cart.price="+resCart2.price)
    println("2. item: "+resCart2.cartItemVOs(0))
    resCart2.price must be_==(tt58.price*1)
    resCart2.price must be_==(35000*1)
    resCart2.count must be_==(1)
    resCart2.cartItemVOs.size must be_==(1)
    /*
    }catch{
      case AddCartItemException(errors) => {
        println(errors)
        failure("AddCartItemException")
        true must beFalse
      }
      case UpdateCartItemException(errors) => {
        println(errors)
        failure("UpdateCartItemException")
        true must beFalse
      }
      case t:Throwable => {
        failure("unknown exception")
        t.printStackTrace()
        true must beFalse}
    }*/
  }

  "delete item from cart after 2 addItem" in {
    skipped

    val cur = "EUR"
    val uuid = UUID.randomUUID.toString
    var locale = Locale.getDefault()
    println(s"uuid=${uuid}")
    val cart = service.initCart(uuid)
    val ttid58 = 58
    val tt58 = TicketType.get(ttid58)
    val quantity = 1
    val dateTime = None
    val resCart = service.addItem(locale,cur,cart,ttid58,quantity,dateTime, List())

    val itemId58 = resCart.cartItemVOs(0).id

    println("1. cart.price="+resCart.price)
    resCart.price must be_==(tt58.price)
    resCart.price must be_==(35000)
    resCart.count must be_==(1)
    resCart.cartItemVOs.size must be_==(1)

    val ttid51 = 51
    val tt51 = TicketType.get(ttid51)
    val resCart2 = service.addItem(locale,cur,resCart,ttid51,quantity,dateTime, List())

    println("2. cart.price="+resCart2.price)
    resCart2.price must be_==(tt58.price+tt51.price)
    resCart2.price must be_==(35000+30000)
    resCart2.count must be_==(2)
    resCart2.cartItemVOs.size must be_==(2)

    val resCart3 = service.removeItem(locale, cur, resCart2, itemId58)
    println("3. cart.price="+resCart3.price)
    resCart3.price must be_==(tt51.price)
    resCart3.price must be_==(30000)
    resCart3.count must be_==(1)
    resCart3.cartItemVOs.size must be_==(1)

  }

  "clear cart after 2 addItem" in {
    skipped

    val cur = "EUR"
    val uuid = UUID.randomUUID.toString
    var locale = Locale.getDefault()
    println(s"uuid=${uuid}")
    val cart = service.initCart(uuid)
    val ttid58 = 58
    val tt58 = TicketType.get(ttid58)
    val quantity = 1
    val dateTime = None
    val resCart = service.addItem(locale, cur, cart, ttid58, quantity, dateTime, List())

    val itemId58 = resCart.cartItemVOs(0).id

    println("1. cart.price=" + resCart.price)
    resCart.price must be_==(tt58.price)
    resCart.price must be_==(35000)
    resCart.count must be_==(1)
    resCart.cartItemVOs.size must be_==(1)

    val ttid51 = 51
    val tt51 = TicketType.get(ttid51)
    val resCart2 = service.addItem(locale, cur, resCart, ttid51, quantity, dateTime, List())

    println("2. cart.price=" + resCart2.price)
    resCart2.price must be_==(tt58.price + tt51.price)
    resCart2.price must be_==(35000 + 30000)
    resCart2.count must be_==(2)
    resCart2.cartItemVOs.size must be_==(2)

    val resCart3 = service.clear(locale,cur,resCart2)
    println("3. cart.price=" + resCart3.price)
    resCart3.price must be_==(0)
    resCart3.count must be_==(0)
    resCart3.cartItemVOs.size must be_==(0)

  }

  "prepare transaction without coupons" in {
    val cur = "EUR"
    val uuid = UUID.randomUUID.toString
    println(s"uuid=${uuid}")
    val cart = service.initCart(uuid)
    val ttid58 = 58
    val tt58 = TicketType.get(ttid58)
    val quantity = 1
    val dateTime = None
    val resCart = service.addItem(Locale.getDefault,cur,cart,ttid58,quantity,dateTime, List())

    println("1. cart.price="+resCart.price)
    resCart.price must be_==(tt58.price)
    resCart.price must be_==(35000)
    resCart.count must be_==(1)
    resCart.cartItemVOs.size must be_==(1)

    val ttid51 = 51
    val tt51 = TicketType.get(ttid51)
    val resCart2 = service.addItem(Locale.getDefault,cur,resCart,ttid51,quantity,dateTime, List())

    println("2. cart.price="+resCart2.price)
    resCart2.price must be_==(tt58.price+tt51.price)
    resCart2.price must be_==(35000+30000)
    resCart2.count must be_==(2)
    resCart2.cartItemVOs.size must be_==(2)


    val companyId = 8
    val countryCode = "FR"
    val state = None
    val currency = Currency(2, 1960,"euro","EUR")
    val preparedCart = service.prepareBeforePayment(companyId, countryCode, state, currency.code, resCart2, currency)

    //TODO println(render(preparedCart))

    true must beTrue
  }
}