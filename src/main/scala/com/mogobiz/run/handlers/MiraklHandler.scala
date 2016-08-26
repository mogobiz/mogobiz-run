package com.mogobiz.run.handlers

import java.util.UUID

import com.mogobiz.pay.common._
import com.mogobiz.pay.exceptions.Exceptions.CountryDoesNotExistException
import com.mogobiz.pay.model._
import com.mogobiz.run.es._
import com.mogobiz.run.model._
import com.mogobiz.pay.config.MogopayHandlers.handlers.accountHandler
import com.mogobiz.pay.config.MogopayHandlers.handlers.rateHandler
import com.mogobiz.pay.config.MogopayHandlers.handlers.countryHandler
import com.mogobiz.pay.config.MogopayHandlers.handlers.countryAdminHandler
import com.mogobiz.run.config.MogobizHandlers.handlers.taxRateHandler
import com.mogobiz.run.externals.mirakl.Mirakl.{ Customer, Offer, OrderBean, ShippingAddress }
import com.mogobiz.run.externals.mirakl.{ Mirakl, MiraklClient }
import com.mogobiz.run.model.Mogobiz.{BOCartItem, BOCart}

import scala.collection.Seq

object MiraklHandler {
  val MIRAKL_SHIPPING_PREFIX = "MIRAKL_"
  val MIRAKL_SHOP_PREFIX = "SHOP_"
  val MIRAKL_OFFER_PREFIX = "OFFER_"
}

trait MiraklHandler {

  def shippingPrices(cart: Cart, address: AccountAddress): List[ExternalShippingDataList]

  //passé privé pour l'instant  def getShippingZoneCode(shippingAddress: AccountAddress): String

  def createOrder(boCart: BOCart, currency: Currency, accountId: Mogopay.Document, shippingAddr: AccountAddress, selectShippingCart: SelectShippingCart): Option[String]

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

  def shippingPrices(cart: Cart, address: AccountAddress): List[ExternalShippingDataList] = Nil

  def createOrder(boCart: BOCart, currency: Currency, accountId: Mogopay.Document, shippingAddr: AccountAddress, selectShippingCart: SelectShippingCart) = None

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
  def shippingPrices(cart: Cart, address: AccountAddress): List[ExternalShippingDataList] = {
    // get only Mirakl Items
    val offerIdsAndQuantity: List[(Long, Int)] = cart.cartItems.flatMap(cartItem => {
      cartItem.externalCodes.find(_.provider == ExternalProvider.MIRAKL).map { externalOfferId =>
        (externalOfferId.code.toLong, cartItem.quantity)
      }
    }).toList

    if (!offerIdsAndQuantity.isEmpty) {
      // determine the shippingZoneCode from the shipping address
      val shippingZoneCode = getShippingZoneCode(address)

      // call Mirakl API to get shippingFees for every items in the cart
      val shippingFees = MiraklClient.getShippingFeesByOffersAndShippingType(shippingZoneCode, offerIdsAndQuantity)

      shippingFees.flatMap { fee =>
        val externalOfferId = fee.offerId.toString
        val externalCode = new ExternalCode(ExternalProvider.MIRAKL, externalOfferId)

        cart.cartItems.find { cartItem =>
          cartItem.externalCodes.find { externalCode =>
            externalCode.provider == ExternalProvider.MIRAKL && externalCode.code == externalOfferId
          }.isDefined
        }.map { cartItem =>
          val rate = rateHandler.findByCurrencyCode(fee.shopCurrencyIsoCode)
          val multiplyFactorToConvertToCents = rate.get.currencyFractionDigits
          val factor = 10 ^ multiplyFactorToConvertToCents
          val shippingPrice = (fee.lineShippingPrice * factor).toLongExact
          (externalCode, createShippingData(address, externalCode, cartItem.id, fee.shippingTypeCode, shippingPrice, fee.shopCurrencyIsoCode))
        }
      }.groupBy(_._1).mapValues(_.map(_._2)).map { keyValue: (ExternalCode, List[ShippingData]) =>
        new ExternalShippingDataList(keyValue._1, keyValue._2)
      }.toList
    }
    else Nil
  }

  protected def createShippingData(address: AccountAddress, miraklCode: ExternalCode, cartItemId: String, shippingCode: String, price: Long, currencyCode: String): ShippingData = {
    var rate: Option[Rate] = rateHandler.findByCurrencyCode(currencyCode)
    ShippingData(address, miraklCode.provider, miraklCode.code, shippingCode, shippingCode, shippingCode, price, currencyCode, if (rate.isDefined) rate.get.currencyFractionDigits else 2, Some(cartItemId))
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
   */
  def createOrder(boCart: BOCart, currency: Currency, accountId: Mogopay.Document, shippingAddr: AccountAddress, selectShippingCart: SelectShippingCart): Option[String] = {
    val account: Account = accountHandler.load(accountId).get

    val billAddr = account.address.get

    val billingCountry = countryHandler.findByCode(
      billAddr.country.getOrElse(throw new CountryDoesNotExistException("The billing address must have a country"))
    ).getOrElse(throw new CountryDoesNotExistException("The billing address must have a existing country"))
    val shippingCountry = countryHandler.findByCode(
      shippingAddr.country.getOrElse(throw new CountryDoesNotExistException("The shipping address must have a country"))
    ).getOrElse(throw new CountryDoesNotExistException("The shipping address must have a existing country"))
    val shippingState = shippingAddr.admin1.map{state => countryAdminHandler.getAdmin1ByCode(shippingCountry.code, state).flatMap{_.name}.getOrElse(state)}

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
        company = billAddr.company, country = billingCountry.name, country_iso_code = billingCountry.isoCode3,
        firstname = billAddr.firstName, lastname = billAddr.lastName.getOrElse(""),
        phone = billAddr.telephone.map { tel => tel.lphone }, phone_secondary = None,
        state = None, street_1 = billAddr.road, street_2 = billAddr.road2, zip_code = billAddr.zipCode),
      shipping_address = ShippingAddress(
        city = shippingAddr.city,
        civility = shippingAddr.civility.map { civ => civ.toString },
        company = shippingAddr.company,
        country = shippingCountry.name,
        country_iso_code = shippingCountry.isoCode3,
        firstname = shippingAddr.firstName,
        lastname = shippingAddr.lastName.getOrElse(""),
        phone = shippingAddr.telephone.map { tel => tel.lphone },
        phone_secondary = None,
        state = shippingState,
        street_1 = shippingAddr.road,
        street_2 = shippingAddr.road2,
        zip_code = shippingAddr.zipCode
      )
    )

    val offers = BOCartItemDao.findByBOCart(boCart).map { item : BOCartItem =>
        item.externalCodes.find { externalCode => externalCode.provider == ExternalProvider.MIRAKL }.map { externalCode =>

        val selectShippingData = selectShippingCart.externalShippingPrices.find { externalShippingData: ExternalShippingData =>
          externalShippingData.externalCode.provider == ExternalProvider.MIRAKL &&
          externalShippingData.shipping.cartItemId.getOrElse("") == item.uuid
        }.map {_.shipping }.get

        val shippingPrice = BigDecimal.exact(selectShippingData.price) / Math.pow(10, selectShippingData.currencyFractionDigits)

        val selectedShippingTypeCode = selectShippingData.service

        Offer(
          currency_iso_code = currency.code,
          leadtime_to_ship = None,
          offer_id = externalCode.code.toLong,
          offer_price = item.endPrice / 100,
          order_line_additional_fields = Array(),
          order_line_id = None, //Some(UUID.randomUUID().toString),
          price = item.totalEndPrice / 100,
          quantity = item.quantity,
          shipping_price = shippingPrice,
          shipping_taxes = Array(),
          shipping_type_code = selectedShippingTypeCode,
          taxes = Array()
        )
      }
    }.flatten
    if (!offers.isEmpty) {
      val shippingZoneCode = getShippingZoneCode(shippingAddr)
      val order = new OrderBean(boCart.transactionUuid.getOrElse(""), customer, offers.toArray, shippingZoneCode)
      val orderId = MiraklClient.createOrder(order)
      Some(orderId)
    }
    else None
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
