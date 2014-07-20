package com.mogobiz.cart

import com.mogobiz.utils.Utils
import org.joda.time.DateTime
import org.json4s.JsonAST.JValue
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization._
import org.specs2.mutable.Specification
import java.util.{Locale, UUID}
import scalikejdbc.config.DBs
import com.mogobiz.Currency
import org.json4s.{DefaultFormats, Formats}

/**
 * Created by Christophe on 06/05/2014.
 */
class CartBoServiceSpec extends Specification {

  DBs.setupAll()

  val service = CartBoService
  val renderService = CartRenderService

  val defaultCurrency = new Currency(2, 0.01, "EUR", "euro")

  "init cart" in {
    val uuid = UUID.randomUUID.toString
    println(s"uuid=${uuid}")

    val cart = service.initCart(uuid)

    cart.uuid must_== (uuid)
    cart.finalPrice must_== 0
    cart.count must_== 0
    cart.cartItemVOs.length must_== 0
    cart.coupons.length must_== 0
  }

  "addToCart a product" in {
    val cur = "EUR"
    val uuid = UUID.randomUUID.toString
    println(s"uuid=${uuid}")
    val cart = service.initCart(uuid)
    val ttid = 63
    //val ticketType = TicketType.get(ttid)
    val quantity = 5
    val dateTime = None
    val resCart = service.addItem(Locale.getDefault, cur, cart, ttid, quantity, dateTime, List())

    val item = resCart.cartItemVOs(0)

    println("cart.price=" + resCart.price)
    resCart.count must be_==(1)
    resCart.cartItemVOs.size must be_==(1)
    item.shipping must beSome[ShippingVO]
    val shipping = item.shipping.get
    shipping.id must_== (55)
    shipping.weight must_== (25)
    shipping.free must beFalse
    shipping.amount must_== (0)

  }

  /*
  "addToCart should fail if quantity is too much" in {
    val cur = "EUR"
    val uuid = UUID.randomUUID.toString
    println(s"uuid=${uuid}")
    val cart = service.initCart(uuid)
    val ttid = 58
    //val ticketType = TicketType.get(ttid)
    val quantity = 100
    val dateTime = None
    service.addItem(Locale.getDefault, cur, cart, ttid, quantity, dateTime, List()) must throwA[AddCartItemException]

    /*
    service.addItem(Locale.getDefault,cur,cart,ttid,quantity,dateTime, List()) must throwA[Exception] like {
      case AddCartItemException(errors) => errors.contains("AddCartItemException")
      case _ => false
    } must beTrue
    */

  }



  "addToCart a product + another" in {
    val cur = "EUR"
    val uuid = UUID.randomUUID.toString
    println(s"uuid=${uuid}")
    val cart = service.initCart(uuid)
    val ttid58 = 58
    val tt58 = TicketType.get(ttid58)
    val quantity = 1
    val dateTime = None
    val resCart = service.addItem(Locale.getDefault, cur, cart, ttid58, quantity, dateTime, List())

    println("1. cart.price=" + resCart.price)
    resCart.price must be_==(tt58.price)
    resCart.price must be_==(35000)
    resCart.count must be_==(1)
    resCart.cartItemVOs.size must be_==(1)

    val ttid51 = 51
    val tt51 = TicketType.get(ttid51)
    val resCart2 = service.addItem(Locale.getDefault, cur, resCart, ttid51, quantity, dateTime, List())

    println("2. cart.price=" + resCart2.price)
    resCart2.price must be_==(tt58.price + tt51.price)
    resCart2.price must be_==(35000 + 30000)
    resCart2.count must be_==(2)
    resCart2.cartItemVOs.size must be_==(2)

  }

  "addToCart the same product twice" in {
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

    println("1. cart.price=" + resCart.price)
    resCart.price must be_==(tt58.price)
    resCart.price must be_==(35000)
    resCart.count must be_==(1)
    resCart.cartItemVOs.size must be_==(1)

    val resCart2 = service.addItem(Locale.getDefault, cur, resCart, ttid, quantity, dateTime, List())
    println("2. cart.price=" + resCart2.price)
    resCart2.price must be_==(tt58.price * 2)
    resCart2.price must be_==(35000 * 2)

    resCart2.count must be_==(1) //TODO fail here
    resCart2.cartItemVOs.size must be_==(1)

  }

  "updateCart change the quantity" in {
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
    val resCart = service.addItem(Locale.getDefault, cur, cart, ttid58, quantity, dateTime, List())
    println("1. cart.price=" + resCart.price)
    resCart.price must be_==(tt58.price * 5)
    resCart.price must be_==(35000 * 5)
    resCart.count must be_==(1)
    resCart.cartItemVOs.size must be_==(1)

    val itemId = resCart.cartItemVOs(0).id

    val resCart2 = service.updateItem(Locale.getDefault(), cur, resCart, itemId, 1)
    println("2. cart.price=" + resCart2.price)
    println("2. item: " + resCart2.cartItemVOs(0))
    resCart2.price must be_==(tt58.price * 1)
    resCart2.price must be_==(35000 * 1)
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

    val resCart3 = service.removeItem(locale, cur, resCart2, itemId58)
    println("3. cart.price=" + resCart3.price)
    resCart3.price must be_==(tt51.price)
    resCart3.price must be_==(30000)
    resCart3.count must be_==(1)
    resCart3.cartItemVOs.size must be_==(1)

  }

  "clear cart after 2 addItem" in {

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

    val resCart3 = service.clear(locale, cur, resCart2)
    println("3. cart.price=" + resCart3.price)
    resCart3.price must be_==(0)
    resCart3.count must be_==(0)
    resCart3.cartItemVOs.size must be_==(0)

  }

  def prepareCartWith2items: CartVO = {

    val cur = "EUR"
    val uuid = UUID.randomUUID.toString
    println(s"uuid=${uuid}")
    val cart = service.initCart(uuid)
    val ttid58 = 58
    val tt58 = TicketType.get(ttid58)
    val quantity = 1
    val dateTime = None
    val resCart = service.addItem(Locale.getDefault, cur, cart, ttid58, quantity, dateTime, List())

    println("1. cart.price=" + resCart.price)
    resCart.price must be_==(tt58.price)
    resCart.price must be_==(35000)
    resCart.count must be_==(1)
    resCart.cartItemVOs.size must be_==(1)

    val ttid51 = 51
    val tt51 = TicketType.get(ttid51)
    val resCart2 = service.addItem(Locale.getDefault, cur, resCart, ttid51, quantity, dateTime, List())

    println("2. cart.price=" + resCart2.price)
    resCart2.price must be_==(tt58.price + tt51.price)
    resCart2.price must be_==(35000 + 30000)
    resCart2.count must be_==(2)
    resCart2.cartItemVOs.size must be_==(2)

    resCart2
  }

  "prepare transaction without coupons" in {

    val companyCode = "mogobiz"
    val countryCode = "FR"
    val state = None
    val currency = defaultCurrency
    val cart = prepareCartWith2items

    val data = service.prepareBeforePayment(companyCode, countryCode, state, currency.code, cart, currency)


    /*
    implicit def json4sFormats: Formats = DefaultFormats
    import org.json4s.native.JsonMethods._
    import org.json4s.native.Serialization.{ write}
    import org.json4s.JsonDSL._

    println(pretty(render(write(data))))
*/
    println("-----------------------------------------------------------------------------------------------")
    println(data)

    val expectedAmount = 77740
    data("amount") must be_==(expectedAmount)
    data("currencyCode") must be_==(currency.code)
    data("currencyRate") must be_==(currency.rate)
  }

  "prepare transaction without coupons and commit" in {

    val companyCode = "mogobiz"
    val countryCode = "FR"
    val state = None
    val currency = defaultCurrency
    val preparedCart = prepareCartWith2items

    val data = service.prepareBeforePayment(companyCode, countryCode, state, currency.code, preparedCart, currency)

    val cartService = CartBoService
    val cart = cartService.initCart(preparedCart.uuid)
    cart.inTransaction must beTrue

    val transactionUuid = UUID.randomUUID().toString
    val emailsData = service.commit(cart, transactionUuid)

    emailsData.length must be_==(0)

    //TODO check en base le statut de la transaction
    //TODO check en base le valeur du panier
  }

  "prepare transaction without coupons and cancel" in {

    val companyCode = "mogobiz"
    val countryCode = "FR"
    val state = None
    val currency = defaultCurrency
    val preparedCart = prepareCartWith2items

    val data = service.prepareBeforePayment(companyCode, countryCode, state, currency.code, preparedCart, currency)

    val cartService = CartBoService
    val cart = cartService.initCart(preparedCart.uuid)
    cart.inTransaction must beTrue

    var locale = Locale.getDefault()
    val canceledCart = service.cancel(cart)

    canceledCart.inTransaction must beFalse

    //TODO check en base le statut de la transaction
    //TODO check en base le valeur du panier

  }

  "addToCart should" in {

      "add a DATE_ONLY product with a valid date and no registered person" in {

        val locale = Locale.getDefault()
        val currencyCode = "EUR"
        val ticketTypeId = 121 // or 119
        val quantity = 1
        val dateTime = Some(DateTime.now())
        val registeredCartItems = List()

        val uuid = UUID.randomUUID.toString
        val cart = service.initCart(uuid)

        //    val  = TicketType.get(ttid58)

        var exceptionCatched = false
        try{
          service.addItem(locale, currencyCode, cart,ticketTypeId, quantity, dateTime, registeredCartItems)  //must throwA[AddCartItemException]

        }catch{
          case e: AddCartItemException => {
            exceptionCatched = true
            e.errors.size mustEqual(1)
            e.errors.head._1 mustEqual("registeredCartItems")
            e.errors.head._2 mustEqual("size.error")
          }
          case t:Throwable => {
            t.printStackTrace()
          }
        }
        exceptionCatched must beTrue
      }

      "add a DATE_ONLY product with no date and a registered person" in {
        val locale = Locale.getDefault()
        val currencyCode = "EUR"
        val ticketTypeId = 121 // or 119
        val quantity = 1
        val dateTime = None
        val person = RegisteredCartItemVO(cartItemId = "", id = "", email = Some("christophe.galant@ebiznext.com") )
        val registeredCartItems = List(person)

        val uuid = UUID.randomUUID.toString
        val cart = service.initCart(uuid)

        var exceptionCatched = false

        try{
          service.addItem(locale, currencyCode, cart,ticketTypeId, quantity, dateTime, registeredCartItems) //must throwA[AddCartItemException]
        }catch{
          case e: AddCartItemException => {
            exceptionCatched = true
            e.errors.size mustEqual(1)
            e.errors.head._1 mustEqual("dateTime")
            e.errors.head._2 mustEqual("nullable.error")
          }
          case t:Throwable => {
            t.printStackTrace()
          }
        }
        exceptionCatched must beTrue
      }

      "add a DATE_ONLY product with an invalid date and a registered person" in {

        val locale = Locale.getDefault()
        val currencyCode = "EUR"
        val ticketTypeId = 121 // or 119
        val quantity = 1
        val dateTime = Some(new DateTime(2010,1,1,0,0))
        val person = RegisteredCartItemVO(cartItemId = "", id = "", email = Some("christophe.galant@ebiznext.com") )
        val registeredCartItems = List(person)

        val uuid = UUID.randomUUID.toString
        val cart = service.initCart(uuid)

        var exceptionCatched = false

        try{
          service.addItem(locale, currencyCode, cart,ticketTypeId, quantity, dateTime, registeredCartItems) //must throwA[AddCartItemException]
        }catch{
          case e: AddCartItemException => {
            exceptionCatched = true
            e.errors.size mustEqual(1)
            e.errors.head._1 mustEqual("dateTime")
            e.errors.head._2 mustEqual("unsaleable.error")
          }
          case t:Throwable => {
            t.printStackTrace()
          }
        }
        exceptionCatched must beTrue

      }

    }

    "add a DATE_ONLY product with a date and a registered person" in {


      val locale = Locale.getDefault()
      val currencyCode = "EUR"
      val ticketTypeId = 121 // or 119
      val quantity = 1
      val dateTime = Some(DateTime.now) //new DateTime(2010,1,1,0,0)
      val person = RegisteredCartItemVO(cartItemId = "", id = "", email = Some("christophe.galant@ebiznext.com") )
      val registeredCartItems = List(person)

      val uuid = UUID.randomUUID.toString
      val cart = service.initCart(uuid)

      val updatedCart = service.addItem(locale, currencyCode, cart,ticketTypeId, quantity, dateTime, registeredCartItems)

      Utils.printJSON(updatedCart)

      updatedCart.cartItemVOs.size must be_==(1)
      updatedCart.count must be_==(1)

    }


  "add a DATE_TIME product (cinema ticket) with a valid datetime and a registered person" in {

    val locale = Locale.getDefault()
    val currencyCode = "EUR"
    val ticketTypeId = 128 // or 126
    val quantity = 1
    val dateTime = Some(new DateTime(2014, 5, 7, 15, 0))
    val person = RegisteredCartItemVO(cartItemId = "", id = "", email = Some("christophe.galant@ebiznext.com"))
    val registeredCartItems = List(person)

    val uuid = UUID.randomUUID.toString
    val cart = service.initCart(uuid)

    /*
    val res = try{


      true
    }catch{
      case e: AddCartItemException => println(e.errors); false;
      case t:Throwable => t.printStackTrace;false;
    }

    res must beTrue
    */
    val updatedCart = service.addItem(locale, currencyCode, cart, ticketTypeId, quantity, dateTime, registeredCartItems)
    //      Utils.printJSON(updatedCart)

    updatedCart.cartItemVOs.size must be_==(1)
    updatedCart.count must be_==(1)

    "and should render in JSON with formatted amount " in {
      val currency = defaultCurrency
      val rCart = renderService.renderCart(updatedCart, currency, locale)
      val items = rCart("cartItemVOs")
      //println(items)
      //println(rCart)

      success
    }

  }

  "addToCart a DATE_TIME product (cinema ticket) with a valid datetime and no registered person" in {
    skipped("a implémenter")


  }

  "addToCart a DATE_TIME product (cinema ticket) with an invalid datetime and a registered person" in {
    skipped("a implémenter")


  }

  "addToCart a DATE_TIME product (cinema ticket) with a valid datetime and a complete familly registered" in {
    skipped("a implémenter")


  }
*/
}