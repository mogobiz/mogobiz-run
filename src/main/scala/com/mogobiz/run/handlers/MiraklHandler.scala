package com.mogobiz.run.handlers

import java.util.UUID

import com.mogobiz.mirakl.{CommonModel, MiraklClient}
import com.mogobiz.mirakl.ShippingModel._
import com.mogobiz.mirakl.OrderModel.{Offer => OrderOffer, _}
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
import com.mogobiz.run.model.Mogobiz.{BOCart, BOCartItem}

import scala.util.{Failure, Success}

object MiraklHandler {
  val MIRAKL_SHIPPING_PREFIX = "MIRAKL_"
  val MIRAKL_SHOP_PREFIX = "SHOP_"
  val MIRAKL_OFFER_PREFIX = "OFFER_"
}

trait MiraklHandler {

  def shippingPrices(cart: Cart, address: AccountAddress): Map[String, ExternalShippingDataList]

  //passé privé pour l'instant  def getShippingZoneCode(shippingAddress: AccountAddress): String

  def createOrder(boCart: BOCart, currency: Currency, accountId: Mogopay.Document, shippingAddr: AccountAddress, selectShippingCart: SelectShippingCart): Option[String]

}

class MiraklHandlerUndef extends MiraklHandler {

  def shippingPrices(cart: Cart, address: AccountAddress): Map[String, ExternalShippingDataList] = Map()

  def createOrder(boCart: BOCart, currency: Currency, accountId: Mogopay.Document, shippingAddr: AccountAddress, selectShippingCart: SelectShippingCart) = None
}

class MiraklHandlerImpl extends MiraklHandler {
  def shippingPrices(cart: Cart, address: AccountAddress): Map[String, ExternalShippingDataList] = {
    // get only Mirakl Items
    val miraklCartItems = cart.cartItems.filter{cartItem : CartItem => cartItem.isExternalItemFor(ExternalProvider.MIRAKL)}

    val offerIdsAndQuantity: List[(Long, Int)] = miraklCartItems.map { cartItem =>
      (cartItem.externalCode.get.code.toLong, cartItem.quantity)
    }

    val shopShippingFeesDto = if (!offerIdsAndQuantity.isEmpty) {
      // determine the shippingZoneCode from the shipping address
      val shippingZoneCode = getShippingZoneCode(address)

      // call Mirakl API to get shippingFees for every items in the cart
      MiraklClient.getShopShippingsFees(shippingZoneCode, offerIdsAndQuantity)
    } else (None, None)

    val shippingDataById = miraklCartItems.map { cartItem =>
      val externalCode = cartItem.externalCode.get
      val externalOfferId = externalCode.code.toLong
      val shippingPrices = shopShippingFeesDto._1.map { error =>
        ExternalShippingDataList(Some(convertError(error)), Nil)
      }.getOrElse {
        shopShippingFeesDto._2.map { shopShippingFees =>
          val shopAndOffer = shopShippingFees.shops.map { shop =>
            val offer = shop.offers.find { offer =>
              offer.offer_id == externalOfferId
            }
            (shop, offer)
          }.find(_._2.isDefined).map { shopAndOffer => (shopAndOffer._1, shopAndOffer._2.get)}

          shopAndOffer.map { shopAndOffer =>
            val shop = shopAndOffer._1
            val error = shop.error_code.map {convertError(_)}
            val shippingPrices = shopAndOffer._2.shipping_types.map { shipping =>
              val rate = rateHandler.findByCurrencyCode(shop.currency_iso_code)
              val multiplyFactorToConvertToCents = rate.get.currencyFractionDigits
              val factor = 10 ^ multiplyFactorToConvertToCents
              val shippingPrice = (shipping.line_shipping_price * factor).toLongExact
              ExternalShippingData(externalCode, createShippingData(address, shipping.code, shippingPrice, shop.currency_iso_code))
            }
            ExternalShippingDataList(error, shippingPrices)
          }.getOrElse(ExternalShippingDataList(Some(ShippingPriceError.UNKNOWN), Nil))
        }.getOrElse(ExternalShippingDataList(Some(ShippingPriceError.UNKNOWN), Nil))
      }
      (cartItem.id, shippingPrices)
    }
    Map(shippingDataById : _*)
  }

  private def convertError(error: ShippingFeeErrorCode.ShippingFeeErrorCode): ShippingPriceError.ShippingPriceError = {
    error match {
      case ShippingFeeErrorCode.SHIPPING_ZONE_NOT_ALLOWED => ShippingPriceError.SHIPPING_ZONE_NOT_ALLOWED
      case ShippingFeeErrorCode.SHIPPING_TYPE_NOT_ALLOWED => ShippingPriceError.SHIPPING_TYPE_NOT_ALLOWED
      case _ => ShippingPriceError.UNKNOWN
    }
  }

  protected def createShippingData(address: AccountAddress, shippingCode: String, price: Long, currencyCode: String): ShippingData = {
    val rate: Option[Rate] = rateHandler.findByCurrencyCode(currencyCode)
    val shipmentId = UUID.randomUUID().toString
    ShippingData(address, shipmentId, shipmentId, shippingCode, shippingCode, shippingCode, price, currencyCode, if (rate.isDefined) rate.get.currencyFractionDigits else 2)
  }

  /**
   * Permet de faire la correspondance entre le pays de livraison et la zone de shipping correspondante configuré dans Mirakl
   */
  protected def getShippingZoneCode(shippingAddress: AccountAddress): String = {
    // L'implémentation par défaut prendre le code dy pays
    shippingAddress.country.getOrElse("WORLDWIDE")
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

    val customer = CommonModel.Customer(
      customer_id = account.uuid,
      civility = account.civility.map { civ => civ.toString },
      firstname = account.firstName.getOrElse(""),
      lastname = account.lastName.getOrElse(""),
      email = account.email,
      locale = None,
      billing_address = CommonModel.Address(
        city = billAddr.city,
        civility = billAddr.civility.map { civ => civ.toString },
        company = billAddr.company, country = billingCountry.name, country_iso_code = billingCountry.isoCode3,
        firstname = billAddr.firstName, lastname = billAddr.lastName.getOrElse(""),
        phone = billAddr.telephone.map { tel => tel.lphone }, phone_secondary = None,
        state = None, street_1 = billAddr.road, street_2 = billAddr.road2, zip_code = billAddr.zipCode),
      shipping_address = CommonModel.ShippingAddress(
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
        zip_code = shippingAddr.zipCode,
        additional_info = None,
        internal_additional_info = None)
    )

    val offers = BOCartItemDao.findByBOCart(boCart).map { item : BOCartItem =>
        item.getExternalCode(ExternalProvider.MIRAKL).map { externalCode =>

        val selectShippingData = selectShippingCart.externalShippingPriceByCode.get(externalCode).get

        val shippingPrice = BigDecimal.exact(selectShippingData.price) / Math.pow(10, selectShippingData.currencyFractionDigits)

        val selectedShippingTypeCode = selectShippingData.service

        OrderOffer(
          currency_iso_code = currency.code,
          leadtime_to_ship = None,
          offer_id = externalCode.code.toLong,
          offer_price = item.endPrice / 100,
          order_line_additional_fields = Nil,
          order_line_id = None, //Some(UUID.randomUUID().toString),
          price = item.totalEndPrice / 100,
          quantity = item.quantity,
          shipping_price = shippingPrice,
          shipping_taxes = Nil,
          shipping_type_code = selectedShippingTypeCode,
          taxes = Nil
        )
      }
    }.flatten
    if (!offers.isEmpty) {
      val shippingZoneCode = getShippingZoneCode(shippingAddr)
      val order = new OrderBean(channel_code = None,
        commercial_id = boCart.transactionUuid.getOrElse(""),
        customer = customer,
        offers = offers,
        order_additional_fields = Nil,
        payment_info = None, //TODO voir comment passer les infos de paiements
        payment_workflow = Some(PaymentWorkflow.PAY_ON_ACCEPTANCE),
        scored = true,
        shipping_zone_code = shippingZoneCode)
      MiraklClient.createOrder(order) match {
        case Success(r) => r.orders.collectFirst{case o: OrderCreated => o.order_id}
        case Failure(_) => None
      }
    }
    else None
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
