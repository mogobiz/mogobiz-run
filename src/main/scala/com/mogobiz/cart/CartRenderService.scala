package com.mogobiz.cart

import java.util.Locale
import com.mogobiz.{Currency, RateBoService}
import org.json4s.{FieldSerializer, DefaultFormats, Formats}
import org.json4s.JsonAST._
import scala.Some
import org.json4s.JsonAST.JDouble
import org.json4s.JsonAST.JString
import com.mogobiz.Currency
import org.json4s.native.JsonMethods._
import scala.Some
import com.mogobiz.Currency
import org.json4s.native.Serialization._
import scala.Some
import com.mogobiz.Currency

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

    val cartWithUpdatedCoupons = updateCoupons(cart)

    val coupons = cartWithUpdatedCoupons.coupons.map{
      c => renderCoupon(c,currency, locale)
    }
    map+=("coupons"-> coupons)

    val prices = renderPriceCart(cartWithUpdatedCoupons,currency,locale)
    map++=prices

    println("renderCart->Map")
    println(map)
    map
  }

  def renderTransactionCart(cart:CartVO, currency:Currency,locale:Locale):Map[String,Any]={
    render(cart,currency,locale)
  }

  def renderCoupon(coupon:CouponVO, currency:Currency, locale:Locale) = {
    implicit def json4sFormats: Formats = DefaultFormats + FieldSerializer[CartItemVO]()
    val jsonCoupon = parse(write(coupon))

    //code from renderPriceCoupon
    val formatedPrice = rateService.format(coupon.price, currency.code, locale);
    val additionalsData = parse(write(Map(("formatedPrice"->formatedPrice))))

    val renderedCoupon = jsonCoupon merge additionalsData
    renderedCoupon
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
    val formatedEndPrice = cart.endPrice match{
      case Some(endprice) => rateService.format(endprice, currency.code, locale, currency.rate)
      case _ => None
    }
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

  private def updateCoupons(cart: CartVO):CartVO= {

    val reduc = cart.coupons.foldLeft(0l)((acc,c) => acc + CouponService.updateCoupon(c, cart).price)

    val finalprice = cart.endPrice match{
      case Some(endprice) => endprice - reduc
      case _ => cart.price - reduc
    }

    cart.copy(reduction = reduc, finalPrice = finalprice)
  }

}