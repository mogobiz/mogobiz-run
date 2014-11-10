package com.mogobiz.run.cart

import java.util.Locale

import com.mogobiz.run.cart.domain.Coupon
import com.mogobiz.run.implicits.Json4sProtocol
import com.mogobiz.run.model.Currency
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

  def renderCart(cart:CartVO,companyCode:String, currency:Currency,locale:Locale):Map[String,Any]={
    logger.info(s"currency: ${currency.code}, ${currency.rate}")
    var map :Map[String,Any]= Map()

    map+=("count" -> cart.count)

    val items = cart.cartItemVOs.map(item => renderCartItem(item,currency,locale))
    map+=("cartItemVOs" -> items)

    val cartWithUpdatedCoupons = updateCoupons(cart)

    val cartWithUpdatedCouponsAndPromotion = updateWithPromotions(cartWithUpdatedCoupons, companyCode)

    val coupons = cartWithUpdatedCouponsAndPromotion.coupons.map{
      c => renderCoupon(c,currency, locale)
    }
    map+=("coupons"-> coupons)

    val prices = renderPriceCart(cartWithUpdatedCouponsAndPromotion,currency,locale)
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
  def renderTransactionCart(cart:CartVO, companyCode:String, rate:Currency):Map[String,Any]={

    val items = cart.cartItemVOs.map(item => renderTransactionCartItem(item,rate))
    val cartWithUpdatedCoupons = updateCoupons(cart)

    val cartWithUpdatedCouponsAndPromotion = updateWithPromotions(cartWithUpdatedCoupons, companyCode)

    val coupons = cartWithUpdatedCouponsAndPromotion.coupons.map{
      c => renderTransactionCoupon(c,rate)
    }
    val prices = renderTransactionPriceCart(cartWithUpdatedCouponsAndPromotion,rate)

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
  def renderCoupon(coupon:CouponVO, currency:Currency, locale:Locale) = {
    implicit def json4sFormats: Formats = DefaultFormats + FieldSerializer[CartItemVO]()
    val jsonCoupon = parse(write(coupon))

    //code from renderPriceCoupon
    val formatedPrice = rateService.format(coupon.price, currency.code, locale)
    val additionalsData = parse(write(Map("formatedPrice" -> formatedPrice)))

    val renderedCoupon = jsonCoupon merge additionalsData
    renderedCoupon
  }

  def renderTransactionCoupon(coupon:CouponVO, rate:Currency) = {
    implicit def json4sFormats: Formats = DefaultFormats + FieldSerializer[CartItemVO]()
    val jsonCoupon = parse(write(coupon))

    val price = rateService.calculateAmount(coupon.price, rate)
    val updatedData = parse(write(Map("price" -> price)))

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
  def renderCartItem(item:CartItemVO,currency:Currency,locale:Locale) ={

    import org.json4s.native.JsonMethods._
    //import org.json4s.native.Serialization
    import org.json4s.native.Serialization.write
    implicit def json4sFormats: Formats = DefaultFormats + FieldSerializer[CartItemVO]() + new org.json4s.ext.EnumNameSerializer(ProductCalendar) ++ JodaTimeSerializers.all
    val jsonItem = parse(write(item))

    val formatedPrice = rateService.format(item.price, currency.code, locale, currency.rate)
    val formatedEndPrice = if(item.endPrice.isEmpty) None else Some(rateService.format(item.endPrice.get, currency.code, locale, currency.rate))
    val formatedTotalPrice = rateService.format(item.totalPrice, currency.code, locale, currency.rate)
    val formatedTotalEndPrice = if(item.totalEndPrice.isEmpty) None else Some(rateService.format(item.totalEndPrice.get, currency.code, locale, currency.rate))

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

  def renderTransactionCartItem(item:CartItemVO,rate:Currency) ={

    import org.json4s.native.JsonMethods._
    import org.json4s.native.Serialization.write
    //implicit def json4sFormats: Formats = DefaultFormats + FieldSerializer[CartItemVO]()
    import Json4sProtocol._
    val jsonItem = parse(write(item))

    val price = rateService.calculateAmount(item.price, rate)
    val endPrice = rateService.calculateAmount(item.endPrice.getOrElse(0l), rate)
    val totalPrice = rateService.calculateAmount(item.totalPrice, rate)
    val totalEndPrice = rateService.calculateAmount(item.totalEndPrice.getOrElse(0l), rate)

    val updatedData = parse(write(Map(
      "price" -> price,
      "endPrice" -> endPrice,
      "totalPrice" -> totalPrice,
      "totalEndPrice" -> totalEndPrice
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
  def renderPriceCart(cart:CartVO, currency:Currency,locale:Locale)={
    val formatedPrice = rateService.format(cart.price, currency.code, locale, currency.rate)
    val formatedEndPrice = cart.endPrice match{
      case Some(endprice) => rateService.format(endprice, currency.code, locale, currency.rate)
      case _ => None
    }
    val formatedReduction = rateService.format(cart.reduction, currency.code, locale, currency.rate)
    val formatedFinalPrice =  rateService.format(cart.finalPrice, currency.code, locale, currency.rate)

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

  def renderTransactionPriceCart(cart:CartVO, rate:Currency)={

    val price = rateService.calculateAmount(cart.price, rate)
    val endPrice = rateService.calculateAmount(cart.endPrice.getOrElse(0l), rate)
    val reduction = rateService.calculateAmount(cart.reduction, rate)
    val finalPrice= rateService.calculateAmount(cart.finalPrice, rate)

    val prices :Map[String,Any]= Map(
      "price" -> price,
      "endPrice" -> endPrice,
      "reduction" -> reduction,
      "finalPrice" -> finalPrice
    )
    prices
  }

  private def updateCoupons(cart: CartVO):CartVO= {

    println("updateCoupons")
    //active le coupon et renvoie la réduction appliqué pour chaque coupon
    val updatedCoupons = cart.coupons.map(c => CouponService.updateCoupon(c, cart))
    //somme des réductions
    val reduc = updatedCoupons.foldLeft(0l)((acc,c) => acc + c.price)

    println(s"reduc=$reduc")
    println(s"cart.endPrice=${cart.endPrice}")

    val finalprice = cart.endPrice match{
      case Some(endprice) => endprice - reduc
      case _ => cart.price - reduc
    }
    println(s"finalprice=$finalprice")

    cart.copy(reduction = reduc, finalPrice = finalprice, coupons = updatedCoupons)
  }

  private def updateWithPromotions(cart: CartVO, companyCode:String):CartVO = {
    val promotions = CouponService.getPromotions(cart,companyCode)
    val reduc = promotions.foldLeft(0l)((acc,c) => acc + c.price)

    println(s"reduc=$reduc")
    println(s"cart.endPrice=${cart.endPrice}")

    val finalprice = cart.endPrice match{
      case Some(endprice) => endprice - reduc
      case _ => cart.price - reduc
    }
    println(s"finalprice=$finalprice")

    cart.copy(reduction = reduc, finalPrice = finalprice)
  }
}