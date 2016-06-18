package com.mogobiz.run.handlers

import java.util.Locale

import com.mogobiz.pay.model.Mogopay.{ Account, AccountAddress, Document, PaymentResult, PaymentStatus, SessionData }
import com.mogobiz.run.model.{ Currency, StoreCart, StoreCartItem }
import com.mogobiz.pay.config.MogopayHandlers.handlers.accountHandler
import com.mogobiz.run.config.MogobizHandlers.handlers.taxRateHandler
import com.mogobiz.run.externals.mirakl.Mirakl.{ Customer, Offer, OrderBean, ShippingAddress }
import com.mogobiz.run.externals.mirakl.{ Mirakl, MiraklClient }
import com.mogobiz.run.model.Mogobiz.BOCart

class MiraklHandler {

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

    val miraklItems = cart.cartItems.filter(item => item.externalOfferId.isDefined)

    val offers = miraklItems.map { item =>
      Offer(
        currency_iso_code = currency.code,
        leadtime_to_ship = None,
        offer_id = item.externalOfferId.get,
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
      val externalCartItems = cart.cartItems.filter(item => item.externalOfferId.isDefined)
      var amount = 0l; //TODO faire un foldLeft ou récup amount depuis boCart ou autre
      externalCartItems.foreach(it => amount = amount + computeMiraklPrices(cart, it))

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
