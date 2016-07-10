package com.mogobiz.run.handlers

import java.util.Locale

import com.mogobiz.pay.common.{ CartRate, CompanyAddress, Cart => CartPay, CartItem => CartItemPay, Coupon => CouponPay, RegisteredCartItem => RegisteredCartItemPay, Shipping => ShippingPay }
import com.mogobiz.pay.common.{ Cart }
import com.mogobiz.pay.model.Mogopay._
import com.mogobiz.run.model.{ Currency, StoreCart, StoreCartItem }
import com.mogobiz.pay.config.MogopayHandlers.handlers.accountHandler
import com.mogobiz.pay.config.MogopayHandlers.handlers.rateHandler
import com.mogobiz.run.config.MogobizHandlers.handlers.taxRateHandler
import com.mogobiz.run.externals.mirakl.Mirakl.{ Customer, Offer, OrderBean, ShippingAddress }
import com.mogobiz.run.externals.mirakl.{ Mirakl, MiraklClient }
import com.mogobiz.run.model.Mogobiz.{ ExternalSource, BOCart }

import scala.collection.Seq

object MiraklHandler {
  val MIRAKL_SHIPPING_PREFIX = "MIRAKL_"
  val MIRAKL_SHOP_PREFIX = "SHOP_"
  val MIRAKL_OFFER_PREFIX = "OFFER_"
}

trait MiraklHandler {

  def shippingPrices(cart: Cart, address: AccountAddress): Seq[ShippingData]

  //passé privé pour l'instant  def getShippingZoneCode(shippingAddress: AccountAddress): String

  def createOrder(cart: StoreCart, accountId: Option[Document], locale: Locale, currency: Currency, countryCode: Option[String], stateCode: Option[String], shippingAddr: AccountAddress): Option[String]

  /**
   * valide une commande auprès de Mirakl
   */
  def validateOrder(cart: StoreCart, boCart: BOCart)

  /**
   * Annule une commande auprès de Mirakl
   */
  def cancelOrder(boCart: BOCart, customerId: String)

  def refundOrder(cart: StoreCart, boCart: BOCart)

}

class MiraklHandlerUndef extends MiraklHandler {

  def shippingPrices(cart: Cart, address: AccountAddress): Seq[ShippingData] = Seq()

  def createOrder(cart: StoreCart, accountId: Option[Document], locale: Locale, currency: Currency, countryCode: Option[String], stateCode: Option[String], shippingAddr: AccountAddress) = None

  /**
   * valide une commande auprès de Mirakl
   */
  def validateOrder(cart: StoreCart, boCart: BOCart) = {}

  /**
   * Annule une commande auprès de Mirakl
   */
  def cancelOrder(boCart: BOCart, customerId: String) = {}

  def refundOrder(cart: StoreCart, boCart: BOCart) = {}
}

class MiraklHandlerImpl extends MiraklHandler {
  def shippingPrices(cart: Cart, address: AccountAddress): Seq[ShippingData] = {

    def createShippingData(externalOfferId: String, shippingCode: String, shippingTypeCode: String, price: Long, currencyCode: String): ShippingData = {
      val rate: Option[Rate] = rateHandler.findByCurrencyCode(currencyCode)
      ShippingData(address, externalOfferId, shippingTypeCode, shippingCode, shippingCode, shippingTypeCode, price, currencyCode, if (rate.isDefined) rate.get.currencyFractionDigits else 2)
    }

    // get only Mirakl Items
    val miraklItems = cart.cartItems.filter(cartItem => cartItem.externalCodes.isEmpty == false && cartItem.externalCodes.contains(ExternalSource.MIRAKL))

    // extract Mirakl item ids and qty
    val offerIdsAndQuantity: List[(Long, Int)] = miraklItems.flatMap { cartItem =>
      ExternalSource.MIRAKL.extractExternalCodeFromList(cartItem.externalCodes).map { externalOfferId =>
        (externalOfferId.toLong, cartItem.quantity)
      }
    }.toList

    // determine the shippingZoneCode from the shipping address
    val shippingZoneCode = getShippingZoneCode(address)

    // call Mirakl API to get shippingFees for every items in the cart
    val shippingFees = MiraklClient.getShippingFeesByOffersAndShippingType(shippingZoneCode, offerIdsAndQuantity)

    shippingFees.map { fee =>
      //val externalOfferId = MiraklHandler.MIRAKL_SHIPPING_PREFIX + "|" + MiraklHandler.MIRAKL_SHOP_PREFIX + fee.shopId + "|" + MiraklHandler.MIRAKL_OFFER_PREFIX + fee.offerId
      val externalOfferId = fee.offerId.toString
      val rate = rateHandler.findByCurrencyCode(fee.shopCurrencyIsoCode)
      val multiplyFactorToConvertToCents = rate.get.currencyFractionDigits
      val factor = 10^multiplyFactorToConvertToCents
      val shippingPrice = (fee.lineShippingPrice * factor).toLongExact
      createShippingData(externalOfferId, ExternalSource.MIRAKL.toString, fee.shippingTypeCode, shippingPrice, fee.shopCurrencyIsoCode)
    }
  }

  /**
  * Permet de faire la correspondance entre le pays de livraison et la zone de shipping correspondante configuré dans Mirakl
  * TODO à externaliser pour être surchargé par le client
  */
  private def getShippingZoneCode(shippingAddress: AccountAddress): String = {

    shippingAddress.country match {
      case Some(countryCode) => {
        countryCode match {
          case "FR" => "FR1"
          case "IT" => "EUROPE"
          case "UK" => "EUROPE"
          case "DE" => "EUROPE"
          case "ES" => "EUROPE"
          case _ => "WORLDWIDE"
        }
      }
      case None => "FR1"
    }
  }

  /**
  * Création d'une commande coté Mirakl
  * TODO => changer en CartPay ??
  */
  def createOrder(cart: StoreCart, accountId: Option[Document], locale: Locale, currency: Currency, countryCode: Option[String], stateCode: Option[String], shippingAddr: AccountAddress): Option[String] = {

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

    val offers = cart.cartItems.flatMap { item =>
      ExternalSource.MIRAKL.extractExternalCodeFromList(item.externalCodes).map { externalOfferId =>
        //TODO BigDecimal, a récupérer via MiraklClient.getShippingFees() et le shipping_type_code
        val shippingPrice = if(item.shipping.isDefined) item.shipping.get.amount else 0

        //TODO=CODE TYPE DE LIVRAISON (ex: STD, EXPRESS, SUPEX) : à retrouver depuis property API Mirakl + StoreCart ou BoCart => valeurs
        val selectedShippingTypeCode = if(item.shipping.isDefined) item.shipping.get.id.toString else "TODO"

        Offer(
          currency_iso_code = currency.code,
          leadtime_to_ship = None,
          offer_id = externalOfferId.toLong,
          offer_price = (item.price / 100),
          order_line_additional_fields = Array(),
          order_line_id = item.boCartItemUuid, // should be unique
          price = (item.price / 100) * item.quantity,
          quantity = item.quantity,
          shipping_price = shippingPrice,
          shipping_taxes = Array(),
          shipping_type_code = selectedShippingTypeCode,
          taxes = Array()

        )
      }
    }
    val shippingZoneCode = getShippingZoneCode(shippingAddr)
    val order = new OrderBean(cart.boCartUuid.getOrElse(""), customer, offers.toArray, shippingZoneCode)
    val orderId = MiraklClient.createOrder(order)
    Some(orderId)
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
