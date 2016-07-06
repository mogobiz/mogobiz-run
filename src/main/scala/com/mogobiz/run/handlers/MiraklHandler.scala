package com.mogobiz.run.handlers

import java.util.Locale

import com.mogobiz.pay.common._
import com.mogobiz.pay.model.Mogopay._
import com.mogobiz.run.model.{ Currency, StoreCart, StoreCartItem }
import com.mogobiz.pay.config.MogopayHandlers.handlers.accountHandler
import com.mogobiz.pay.config.MogopayHandlers.handlers.rateHandler
import com.mogobiz.run.config.MogobizHandlers.handlers.taxRateHandler
import com.mogobiz.run.externals.mirakl.Mirakl.{ Customer, Offer, OrderBean, ShippingAddress }
import com.mogobiz.run.externals.mirakl.{ Mirakl, MiraklClient }
import com.mogobiz.run.model.Mogobiz.BOCart

class MiraklHandler {

  def shippingPrices(cart: Cart, address: AccountAddress): Map[ExternalCode, List[ShippingData]] = {
    computeMiraklShipping(address, cart.cartItems)
  }

  protected def createShippingData(address: AccountAddress, miraklCode: ExternalCode, shippingCode: String, price: Long, currencyCode: String): ShippingData = {
    var rate: Option[Rate] = rateHandler.findByCurrencyCode(currencyCode)
    ShippingData(address, miraklCode.provider, miraklCode.code, shippingCode, shippingCode, shippingCode, price, currencyCode, if (rate.isDefined) rate.get.currencyFractionDigits else 2)
  }

  protected def computeMiraklShipping(address: AccountAddress, cartItems: Array[CartItem]): Map[ExternalCode, List[ShippingData]] = {
    val cartItem = cartItems.head
    val tailResult = computeMiraklShipping(address, cartItems.tail)

    //TODO faire l'appel à MIRAKL pour récupérer les shippingData
    cartItem.externalCodes.find(_.provider == ExternalProvider.MIRAKL).map { miraklCode =>
      cartItem.shipping.map { shipping =>
        val map: Map[ExternalCode, List[ShippingData]] = if (shipping.isDefine) {
          //TODO remplacer les données par celles recues de MIRAKL
          Map(miraklCode -> List(createShippingData(address, miraklCode, "UPS", 500, "EUR")))
        } else Map()
        map
      }.getOrElse(tailResult)
    }.getOrElse(tailResult)
  }

  /**
   * Création d'une commande coté Mirakl
   */
  def createOrder(cart: StoreCart, accountId: Option[Document], locale: Locale, currency: Currency, countryCode: Option[String], stateCode: Option[String], shippingAddr: AccountAddress) = {

    val account: Account = accountHandler.load(accountId.get).get

    val billAddr = account.address.get

    val customer = Customer(
      customer_id = account.uuid,
      civility = account.civility.map { civ => civ.toString },
      firstname = account.firstName.getOrElse(""),
      lastname = account.lastName.getOrElse(""),
      email = account.email,
      locale = None,
      billing_address = Mirakl.Address(
        city = billAddr.city,
        civility = billAddr.civility.map { civ => civ.toString },
        company = billAddr.company, country = billAddr.country.getOrElse(""), country_iso_code = billAddr.country.get,
        firstname = billAddr.firstName, lastname = billAddr.lastName.getOrElse(""),
        phone = billAddr.telephone.map { tel => tel.toString }, phone_secondary = None,
        state = None, street_1 = billAddr.road, street_2 = billAddr.road2, zip_code = billAddr.zipCode),
      shipping_address = ShippingAddress(
        city = shippingAddr.city,
        civility = shippingAddr.civility.map { civ => civ.toString },
        company = shippingAddr.company,
        country = shippingAddr.country.getOrElse(countryCode.getOrElse("")),
        country_iso_code = countryCode.getOrElse(""),
        firstname = shippingAddr.firstName,
        lastname = shippingAddr.lastName.getOrElse(""),
        phone = shippingAddr.telephone.map { tel => tel.toString },
        phone_secondary = None,
        state = stateCode,
        street_1 = shippingAddr.road,
        street_2 = shippingAddr.road2,
        zip_code = shippingAddr.zipCode
      )
    )

    val offers = cart.cartItems.map { item =>
      item.externalCodes.find(_.provider == ExternalProvider.MIRAKL).map { miraklCode =>
        Offer(
          currency_iso_code = currency.code,
          leadtime_to_ship = None,
          offer_id = miraklCode.code.toLong,
          offer_price = (item.price / 100),
          order_line_additional_fields = Array(),
          order_line_id = item.boCartItemUuid, // should be unique
          price = (item.price / 100) * item.quantity,
          quantity = item.quantity,
          shipping_price = 0, //TODO BigDecimal,
          shipping_taxes = Array(), //TODO : Array[ShippingTax],
          shipping_type_code = "TODO", //TODO a retrouver depuis property API Mirakl +
          taxes = Array() //TODO Array[Tax]
        )
      }
    }.flatten
    val shippingZoneCode = "TODO" //TODO retrieve from SH01
    val order = new OrderBean(cart.boCartUuid.getOrElse(""), customer, offers.toArray, shippingZoneCode)
    val orderId = MiraklClient.createOrder(order)
    //TODO stocker l'orderId

  }

  /**
   * valide une commande auprès de Mirakl
   */
  def validateOrder(cart: StoreCart, boCart: BOCart) = {
    val orderId = boCart.externalOrderId
    if (orderId.isDefined) {
      //TODO il faut reprendre le prix du BOCart
      //val externalCartItems = cart.cartItems.filter(item => item.externalOfferId.isDefined)
      var amount = 0l; //TODO faire un foldLeft ou récup amount depuis boCart ou autre
      //externalCartItems.foreach(it => amount = amount + computeMiraklPrices(cart, it))

      MiraklClient.validateOrder(amount, boCart.currencyCode, orderId.get, cart.userUuid.get, Some(boCart.lastUpdated.toDate), boCart.transactionUuid)
    }
  }

  /**
   * Annule une commande auprès de Mirakl
   */
  def cancelOrder(boCart: BOCart, customerId: String) = {
    val orderId = boCart.externalOrderId
    if (orderId.isDefined) {
      MiraklClient.cancelOrder(orderId.get, customerId)
    }
  }

  /**
   * exécute un ordre de remboursement
   * TODO
   */
  def refundOrder(cart: StoreCart, boCart: BOCart) = {
    val orderId = boCart.externalOrderId
    if (orderId.isDefined) {
      val refundId = ???
      val amount = ???
      MiraklClient.refund(amount, boCart.currencyCode, refundId, Mirakl.PaymentStatus.OK)
    }
  }

  /**
   * Soit on calcul à la volée, soit il faut stocker les montants calculé au createOrdre
   */
  private def computeMiraklPrices(cart: StoreCart, cartItem: StoreCartItem): Long = {
    val countryCode = cart.countryCode
    val stateCode = cart.stateCode
    val product = ProductDao.get(cart.storeCode, cartItem.productId).get
    val tax = taxRateHandler.findTaxRateByProduct(product, countryCode, stateCode)
    //    val discounts = findSuggestionDiscount(cart, cartItem.productId)
    val price = cartItem.price
    //    val salePrice = computeDiscounts(Math.max(0, cartItem.price - reduction), discounts)
    val endPrice = taxRateHandler.calculateEndPrice(price, tax)
    //    val saleEndPrice = taxRateHandler.calculateEndPrice(salePrice, tax)
    val totalPrice = price * cartItem.quantity
    //    val saleTotalPrice = salePrice * cartItem.quantity
    val totalEndPrice = endPrice.map { p: Long => p * cartItem.quantity }
    //    val saleTotalEndPrice = saleEndPrice.map { p: Long => p * cartItem.quantity }
    totalEndPrice.get
  }
}
