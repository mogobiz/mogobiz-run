package com.mogobiz.run.cart

import java.util.{Locale, UUID}

import com.mogobiz.MogobizRouteTest
import com.mogobiz.run.config.MogobizDBsWithEnv
import com.mogobiz.run.es.EmbeddedElasticSearchNode
import com.mogobiz.run.handlers.UuidHandler
import com.mogobiz.run.model._
import com.mogobiz.run.cart.domain._
import org.specs2.mutable.Specification
import scalikejdbc.config.DBsWithEnv

/**
 *
 * Created by Christophe on 06/05/2014.
 */
class CartBoServiceSpec extends MogobizRouteTest {

  MogobizDBsWithEnv("test").setupAll()

  sequential

  val service = CartBoService
  val uuidService = new UuidHandler

  val renderService = CartRenderService

  val defaultCurrency = new Currency(2, 0.01, "EUR", "euro")
  val cur = "EUR"
  val buyer = "christophe.galant@ebiznext.com"

  node.client().admin().indices().prepareRefresh().execute().actionGet()

  private def generateUuid = {
    val uuid = UUID.randomUUID.toString
    println(s"uuid=$uuid")
    uuid
  }

  "init cart" in {
    // init vars
    val uuid = generateUuid

    //method to test
    val cart = service.initCart(uuid, None)

    //assertions
    cart.uuid must_== uuid
    cart.cartItems.length must_== 0
    cart.coupons.length must_== 0
  }

  "init cart can retreive authenticate cart" in {
    // prepare test
    val uuid = generateUuid
    val userUuid = generateUuid
    val authenticateCart = StoreCart(uuid, uuid, Some(userUuid), List(StoreCartItem("1", 61, "TV 100\" Full HD", ProductType.PRODUCT, ProductCalendar.NO_DATE, 63, "Standard", 1, 1000, None, None, List(), None)))
    uuidService.setCart(authenticateCart)

    // method to test
    val cart = service.initCart(uuid, Some(userUuid))

    //assertions
    cart.uuid must_== uuid
    cart.userUuid must_== Some(userUuid)
    cart.cartItems.length must_== 1
    cart.coupons.length must_== 0
  }


  "init cart can fusion authenticate and anonyme cart" in {
    // prepare test
    val uuid = generateUuid
    val userUuid = generateUuid
    val authenticateCart = StoreCart(uuid, uuid, Some(userUuid), List(
      StoreCartItem("1", 61, "TV 100\" Full HD", ProductType.PRODUCT, ProductCalendar.NO_DATE, 63, "Standard", 2, 1500, None, None, List(), None),
      StoreCartItem("2", 70, "TV 100\" HD", ProductType.PRODUCT, ProductCalendar.NO_DATE, 72, "Standard", 1, 1000, None, None, List(), None)
    ))
    uuidService.setCart(authenticateCart)
    val anonymeCart = StoreCart(uuid, uuid, None, List(
      StoreCartItem("1", 61, "TV 100\" Full HD", ProductType.PRODUCT, ProductCalendar.NO_DATE, 63, "Standard", 1, 1500, None, None, List(), None),
      StoreCartItem("2", 79, "TV 90\"", ProductType.PRODUCT, ProductCalendar.NO_DATE, 81, "Standard", 1, 750, None, None, List(), None)
    ))
    uuidService.setCart(anonymeCart)

    // method to test
    val cart = service.initCart(uuid, Some(userUuid))
    val cart2 = service.initCart(uuid, None)

    //assertions
    cart.uuid must_== uuid
    cart.userUuid must_== Some(userUuid)
    cart.cartItems.length must_== 3
    cart.cartItems.foreach(cartItem => {
      if (cartItem.productId == 61) cartItem.quantity must_== 3
      else if (cartItem.productId == 70) cartItem.quantity must_== 1
      else if (cartItem.productId == 79) cartItem.quantity must_== 1
    })
    cart.coupons.length must_== 0

    cart2.uuid must_== uuid
    cart2.userUuid must_== None
    cart2.cartItems.length must_== 0
    cart2.coupons.length must_== 0
  }

  "service can computeStoreCart and return fill cart" in {
    // prepare test
    val uuid = generateUuid
    val userUuid = generateUuid
    val authenticateCart = StoreCart(uuid, uuid, Some(userUuid), List(
      StoreCartItem("1", 61, "TV 100\" Full HD", ProductType.PRODUCT, ProductCalendar.NO_DATE, 63, "Standard", 3, 30000, None, None, List(), None),
      StoreCartItem("2", 70, "TV 100\" HD", ProductType.PRODUCT, ProductCalendar.NO_DATE, 72, "Standard", 2, 35000, None, None, List(), None),
      StoreCartItem("3", 114, "TShirt Puma", ProductType.PRODUCT, ProductCalendar.NO_DATE, 116, "Blanc taille M", 6, 1500, None, None, List(), None)
    ), List(StoreCoupon(4512, "TEST2", "mogobiz")))

    // method to test
    val cart = service.computeStoreCart("mogobiz", authenticateCart, Some("FR"), None)

    //assertions
    cart.uuid must_== uuid
    cart.cartItemVOs.length must_== 3
    cart.cartItemVOs.foreach(cartItem => {
      if (cartItem.productId == 61) {
        cartItem.price must_== 30000
        cartItem.endPrice must beSome(35880)
        cartItem.tax must beSome(19.6f)
        cartItem.totalPrice must_== 90000
        cartItem.totalEndPrice must beSome(107640)
      }
      else if (cartItem.productId == 70) {
        cartItem.price must_== 35000
        cartItem.endPrice must beSome(41860)
        cartItem.tax must beSome(19.6f)
        cartItem.totalPrice must_== 70000
        cartItem.totalEndPrice must beSome(83720)
      }
      else if (cartItem.productId == 114) {
        cartItem.price must_== 1500
        cartItem.endPrice must beSome(1794)
        cartItem.tax must beSome(19.6f)
        cartItem.totalPrice must_== 9000
        cartItem.totalEndPrice must beSome(10764)
      }
    })
    cart.coupons.length must_== 2
    cart.coupons.foreach(coupon => {
      if (coupon.code == "TEST2") {
        coupon.active must beTrue
        coupon.price must_== 19136 // le coupon TEST2 ne s'applique que sur les TV
      }
      else {
        coupon.active must beTrue
        coupon.price must_== 3588
      }
    })
    cart.price must_== 169000
    cart.endPrice must beSome(202124)
    cart.reduction must_== 22724
    cart.finalPrice must_== 179400
  }

  "addToCart a NO_DATE product" in {
    val uuid = generateUuid

    val cart = service.initCart(uuid, None)
    val ttid = 63
    val ticketType = TicketType.get(ttid)

    val product = ticketType.product.get
    val expectedShipping = product.shipping.get

    val quantity = 5
    val dateTime = None


    val resCart = service.addItem(cart, ttid, quantity, dateTime, List())
    val item = resCart.cartItems(0)

    resCart.cartItems.size must be_==(1)
    item.shipping must beSome[ShippingVO]
    val shipping = item.shipping.get
    shipping.id must_== expectedShipping.id
    shipping.weight must_== expectedShipping.weight
    shipping.free must beFalse
    shipping.amount must_== 0

  }


  "addToCart should fail if quantity is too much" in {
    val uuid = generateUuid

    val cart = service.initCart(uuid, None)
    val ttid = 63
    val ticketType = TicketType.get(ttid)
    val quantity = 100
    val dateTime = None
    service.addItem(cart, ttid, quantity, dateTime, List()) must throwA[AddCartItemException]

    //TODO check the message

  }


  "addToCart a product + another" in {
    val uuid = generateUuid

    val cart = service.initCart(uuid, None)
    val ttid58 = 63
    val tt58 = TicketType.get(ttid58)
    val quantity = 1
    val dateTime = None
    val resCart = service.addItem(cart, ttid58, quantity, dateTime, List())

    resCart.cartItems.size must be_==(1)

    val ttid51 = 72
    val tt51 = TicketType.get(ttid51)
    val resCart2 = service.addItem(resCart, ttid51, quantity, dateTime, List())

    resCart2.cartItems.size must be_==(2)

  }
  /*
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
  */


  "updateCart change the quantity" in {
    //took 5 and update to 1
    val uuid = generateUuid
    val cart = service.initCart(uuid, None)

    val ttid58 = 72
    val tt58 = TicketType.get(ttid58)
    println(tt58)
    val quantity = 5
    val dateTime = None
    //try{
    val resCart = service.addItem(cart, ttid58, quantity, dateTime, List())
    resCart.cartItems.size must be_==(1)

    val itemId = resCart.cartItems(0).id

    val resCart2 = service.updateItem(resCart, itemId, 1)
    println("2. item: " + resCart2.cartItems(0))
    resCart2.cartItems.size must be_==(1)
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

    val uuid = generateUuid

    var locale = Locale.getDefault
    val cart = service.initCart(uuid, None)
    val ttid58 = 72
    val tt58 = TicketType.get(ttid58)
    val quantity = 1
    val dateTime = None
    val resCart = service.addItem(cart, ttid58, quantity, dateTime, List())

    val itemId58 = resCart.cartItems(0).id

    resCart.cartItems.size must be_==(1)

    val ttid51 = 63
    val tt51 = TicketType.get(ttid51)
    val resCart2 = service.addItem(resCart, ttid51, quantity, dateTime, List())

    resCart2.cartItems.size must be_==(2)

    val resCart3 = service.removeItem(resCart2, itemId58)
    resCart3.cartItems.size must be_==(1)

  }


  "clear cart after 2 addItem" in {

    val uuid = generateUuid

    var locale = Locale.getDefault
    val cart = service.initCart(uuid, None)
    val ttid58 = 72
    val tt58 = TicketType.get(ttid58)
    val quantity = 1
    val dateTime = None
    val resCart = service.addItem(cart, ttid58, quantity, dateTime, List())

    val itemId58 = resCart.cartItems(0).id

    resCart.cartItems.size must be_==(1)

    val ttid51 = 63
    val tt51 = TicketType.get(ttid51)
    val resCart2 = service.addItem(resCart, ttid51, quantity, dateTime, List())

    resCart2.cartItems.size must be_==(2)

    val resCart3 = service.clear(resCart2)
    resCart3.cartItems.size must be_==(0)

  }

  /*

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

  /**
   * Prepare data for  tests
   * @return
   */
  private def prepareCartWith2items: StoreCart = {

    val uuid = generateUuid
    val cart = service.initCart(uuid, None)
    val ttid58 = 72
    val tt58 = TicketType.get(ttid58)
    val quantity = 1
    val dateTime = None
    val resCart = service.addItem(cart, ttid58, quantity, dateTime, List())

    resCart.cartItems.size must be_==(1)

    val ttid51 = 63
    val tt51 = TicketType.get(ttid51)
    val resCart2 = service.addItem(resCart, ttid51, quantity, dateTime, List())

    resCart2.cartItems.size must be_==(2)

    resCart2
  }

  "prepare transaction without coupons" in {

    val companyCode = "mogobiz"
    val countryCode = "FR"
    val state = None
    val currency = defaultCurrency
    val cart = prepareCartWith2items

    val data = service.prepareBeforePayment(companyCode, Some(countryCode), state, currency, cart, buyer)


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

    val data = service.prepareBeforePayment(companyCode, Some(countryCode), state, currency, preparedCart, buyer)

    val cartService = CartBoService
    val cart = cartService.initCart(preparedCart.uuid, None)
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

    val data = service.prepareBeforePayment(companyCode, Some(countryCode), state, currency, preparedCart, buyer)

    val cartService = CartBoService
    val cart = cartService.initCart(preparedCart.uuid, None)
    cart.inTransaction must beTrue

    var locale = Locale.getDefault
    val canceledCart = service.cancel(cart)

    canceledCart.inTransaction must beFalse

    //TODO check en base le statut de la transaction
    //TODO check en base le valeur du panier

  }
}