package com.mogobiz.cart

import java.util.Locale
import com.mogobiz.{Currency, RateBoService}
import org.json4s.{FieldSerializer, DefaultFormats, Formats}

/**
 * Created by Christophe on 09/05/2014.
 */
object CartRenderService {

  val rateService = RateBoService

  def render(cart:CartVO, currency:Currency,locale:Locale):Map[String,Any]={
    var map :Map[String,Any]= Map()

    map+=("count" -> cart.count)

    val items = cart.cartItemVOs.map(item => renderCartItem(item,currency,locale))
    map+=("cartItemVOs" -> items)

    /*
            updateCoupons(cart)
        List<Map> listCoupon = []
        cart.coupons?.each { CouponVO c ->
            listCoupon << renderCoupon(locale, currencyCode, c)
        }

     */
    val coupons = List()
    map+=("coupons"-> coupons)

    val prices = renderPriceCart(cart,currency,locale)
    map++=prices

    println("renderCart->Map")
    println(map)
    map
  }

  def renderCartItem(item:CartItemVO,currency:Currency,locale:Locale) ={

    import org.json4s.native.JsonMethods._
    //import org.json4s.native.Serialization
    import org.json4s.native.Serialization.{read, write}
    implicit def json4sFormats: Formats = DefaultFormats + FieldSerializer[CartItemVO]()
    val jsonItem = parse(write(item))

    val formatedPrice = rateService.format(item.price, currency.code, locale, currency.rate)
    val formatedEndPrice = if(item.endPrice.isEmpty) None else Some(rateService.format(item.endPrice.get, currency.code, locale, currency.rate))
    val formatedTotalPrice = rateService.format(item.totalPrice, currency.code, locale, currency.rate)
    val formatedTotalEndPrice = if(item.totalEndPrice.isEmpty) None else Some(rateService.format(item.totalEndPrice.get, currency.code, locale, currency.rate))

    val additionalsData = parse(write(Map(
      ("formatedPrice"->formatedPrice),
      ("formatedEndPrice"->formatedEndPrice),
      ("formatedTotalPrice"->formatedTotalPrice),
      ("formatedTotalEndPrice"->formatedTotalEndPrice)
    )))

    //TODO Traduction aussi du nom en traduisant le produit et le sku
    /*
    price["productName"] = translateName(cartItem.productId, locale.language, cartItem.productName)
    price["skuName"] = translateName(cartItem.skuId, locale.language, cartItem.skuName)
    */

    val renderedItem = jsonItem merge additionalsData

    renderedItem
  }

  def renderPriceCart(cart:CartVO, currency:Currency,locale:Locale)={
    val formatedPrice = rateService.format(cart.price, currency.code, locale, currency.rate)
    val formatedEndPrice = rateService.format(cart.endPrice, currency.code, locale, currency.rate)
    val formatedReduction = rateService.format(cart.reduction, currency.code, locale, currency.rate)
    val formatedFinalPrice =  rateService.format(cart.finalPrice, currency.code, locale, currency.rate)

    val price :Map[String,Any]= Map(
      ("price"->cart.price),
      ("endPrice"->cart.endPrice),
      ("reduction"->cart.reduction),
      ("finalPrice"->cart.finalPrice),
      ("formatedPrice"->formatedPrice),
      ("formatedEndPrice"->formatedEndPrice),
      ("formatedReduction"->formatedReduction),
      ("formatedFinalPrice"->formatedFinalPrice)
    )
    price
  }

}
