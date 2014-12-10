package com.mogobiz.run.cart

import java.util.Locale

import com.mogobiz.run.implicits.Json4sProtocol
import com.mogobiz.run.model.Currency
import com.mogobiz.run.model.Mogobiz.ProductCalendar
import com.mogobiz.run.model.Render.{CartItem, Coupon, Cart}
import com.mogobiz.run.services.RateBoService
import com.typesafe.scalalogging.slf4j.Logger
import org.json4s.ext.JodaTimeSerializers
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization._
import org.json4s.{DefaultFormats, FieldSerializer, Formats}
import org.slf4j.LoggerFactory

/**
 *
 * Created by Christophe on 09/05/2014.
 */
object CartRenderService {

  val logger = Logger(LoggerFactory.getLogger("CartRenderService"))

  val rateService = RateBoService

  def renderCart(cart:Cart, currency:Currency, locale:Locale):Map[String,Any]={
    logger.info(s"currency: ${currency.code}, ${currency.rate}")
    var map :Map[String,Any]= Map()

    map+=("count" -> cart.count)

    val items = cart.cartItemVOs.map(item => renderCartItem(item,currency,locale))
    map+=("cartItemVOs" -> items)

    val coupons = cart.coupons.map(c => renderCoupon(c,currency, locale))
    map+=("coupons"-> coupons)

    val prices = renderPriceCart(cart,currency,locale)
    map++=prices

    println("renderCart->Map")
    println(map)
    map
  }

  /**
   * Idem que renderCart à quelques différences près d'où la dupplication de code
   * @param cart
   * @param rate
   * @return
   */
  def renderTransactionCart(cart:Cart, rate:Currency, locale: Locale):Map[String,Any]={

    val items = cart.cartItemVOs.map(item => renderTransactionCartItem(item,rate, locale))

    val coupons = cart.coupons.map(c => renderTransactionCoupon(c,rate, locale))

    val prices = renderTransactionPriceCart(cart,rate, locale)

    var map :Map[String,Any]= Map(
      "uuid" -> cart.uuid,
      "count" -> cart.count,
      "cartItemVOs" -> items,
      "coupons"-> coupons
    )

    map++=prices

    map
  }

  /**
   * Renvoie un coupon JSONiné augmenté par un calcul de prix formaté
   * @param coupon
   * @param currency
   * @param locale
   * @return
   */
  def renderCoupon(coupon:Coupon, currency:Currency, locale:Locale) = {
    implicit def json4sFormats: Formats = DefaultFormats + FieldSerializer[Coupon]() ++ JodaTimeSerializers.all
    val jsonCoupon = parse(write(coupon))

    //code from renderPriceCoupon
    val formatedPrice = rateService.formatPrice(coupon.price, currency, locale)
    val additionalsData = parse(write(Map("formatedPrice" -> formatedPrice)))

    val renderedCoupon = jsonCoupon merge additionalsData
    renderedCoupon
  }

  def renderTransactionCoupon(coupon:Coupon, rate:Currency, locale: Locale) = {
    implicit def json4sFormats: Formats = DefaultFormats + FieldSerializer[Coupon]() ++ JodaTimeSerializers.all
    val jsonCoupon = parse(write(coupon))

    val price = rateService.calculateAmount(coupon.price, rate)
    val updatedData = parse(write(Map(
      "price" -> price,
      "formatedPrice" -> rateService.formatPrice(coupon.price, rate, locale)
    )))

    val renderedCoupon = jsonCoupon merge updatedData
    renderedCoupon
  }

  /**
   * Renvoie un cartItem JSONinsé augmenté par le calcul des prix formattés
   * @param item item du panier
   * @param currency
   * @param locale
   * @return
   */
  def renderCartItem(item:CartItem,currency:Currency,locale:Locale) ={

    import org.json4s.native.JsonMethods._
    //import org.json4s.native.Serialization
    import org.json4s.native.Serialization.write
    implicit def json4sFormats: Formats = DefaultFormats + FieldSerializer[CartItem]() + new org.json4s.ext.EnumNameSerializer(ProductCalendar) ++ JodaTimeSerializers.all
    val jsonItem = parse(write(item))

    val formatedPrice = rateService.formatPrice(item.price, currency, locale)
    val formatedEndPrice = if(item.endPrice.isEmpty) None else Some(rateService.formatPrice(item.endPrice.get, currency, locale))
    val formatedTotalPrice = rateService.formatPrice(item.totalPrice, currency, locale)
    val formatedTotalEndPrice = if(item.totalEndPrice.isEmpty) None else Some(rateService.formatPrice(item.totalEndPrice.get, currency, locale))

    val additionalsData = parse(write(Map(
      "formatedPrice" -> formatedPrice,
      "formatedEndPrice" -> formatedEndPrice,
      "formatedTotalPrice" -> formatedTotalPrice,
      "formatedTotalEndPrice" -> formatedTotalEndPrice
    )))

    //TODO Traduction aussi du nom en traduisant le produit et le sku
    /*
    price["productName"] = translateName(cartItem.productId, locale.language, cartItem.productName)
    price["skuName"] = translateName(cartItem.skuId, locale.language, cartItem.skuName)
    */

    val renderedItem = jsonItem merge additionalsData

    renderedItem
  }

  def renderTransactionCartItem(item:CartItem,rate:Currency, locale: Locale) ={

    import org.json4s.native.JsonMethods._
    import org.json4s.native.Serialization.write
    //implicit def json4sFormats: Formats = DefaultFormats + FieldSerializer[CartItemVO]()
    import Json4sProtocol._
    val jsonItem = parse(write(item))

    val price = rateService.calculateAmount(item.price, rate)
    val endPrice = rateService.calculateAmount(item.endPrice.getOrElse(item.price), rate)
    val totalPrice = rateService.calculateAmount(item.totalPrice, rate)
    val totalEndPrice = rateService.calculateAmount(item.totalEndPrice.getOrElse(item.totalPrice), rate)

    val updatedData = parse(write(Map(
      "price" -> price,
      "endPrice" -> endPrice,
      "totalPrice" -> totalPrice,
      "totalEndPrice" -> totalEndPrice,
      "formatedPrice" -> rateService.formatLongPrice(item.price, rate, locale),
      "formatedEndPrice" -> rateService.formatLongPrice(item.endPrice.getOrElse(item.price), rate, locale),
      "formatedTotalPrice" -> rateService.formatLongPrice(item.totalPrice, rate, locale),
      "formatedTotalEndPrice" -> rateService.formatLongPrice(item.totalEndPrice.getOrElse(item.totalPrice), rate, locale)
    )))

    val renderedItem = jsonItem merge updatedData

    renderedItem
  }

  /**
   * Renvoie tous les champs prix calculé sur le panier
   * @param cart
   * @param currency
   * @param locale
   * @return
   */
  def renderPriceCart(cart:Cart, currency:Currency,locale:Locale)={
    val formatedPrice = rateService.formatLongPrice(cart.price, currency, locale)
    val formatedEndPrice = rateService.formatLongPrice(cart.endPrice.getOrElse(cart.price), currency, locale)
    val formatedReduction = rateService.formatLongPrice(cart.reduction, currency, locale)
    val formatedFinalPrice =  rateService.formatLongPrice(cart.finalPrice, currency, locale)

    val prices :Map[String,Any]= Map(
      "price" -> cart.price,
      "endPrice" -> cart.endPrice,
      "reduction" -> cart.reduction,
      "finalPrice" -> cart.finalPrice,
      "formatedPrice" -> formatedPrice,
      "formatedEndPrice" -> formatedEndPrice,
      "formatedReduction" -> formatedReduction,
      "formatedFinalPrice" -> formatedFinalPrice
    )
    prices
  }

  def renderTransactionPriceCart(cart:Cart, rate:Currency, locale: Locale)={

    val price = rateService.calculateAmount(cart.price, rate)
    val endPrice = rateService.calculateAmount(cart.endPrice.getOrElse(cart.price), rate)
    val reduction = rateService.calculateAmount(cart.reduction, rate)
    val finalPrice= rateService.calculateAmount(cart.finalPrice, rate)

    val prices :Map[String,Any]= Map(
      "price" -> price,
      "endPrice" -> endPrice,
      "reduction" -> reduction,
      "finalPrice" -> finalPrice,
      "formatedPrice" -> rateService.formatLongPrice(cart.price, rate, locale),
      "formatedEndPrice" -> rateService.formatLongPrice(cart.endPrice.getOrElse(cart.price), rate, locale),
      "formatedReduction" -> rateService.formatLongPrice(cart.reduction, rate, locale),
      "formatedFinalPrice" -> rateService.formatLongPrice(cart.finalPrice, rate, locale)
    )
    prices
  }
}
