package com.mogobiz.cart

import java.util.Locale

/**
 * Created by Christophe on 09/05/2014.
 */
object CartRenderService {

  def render(cart:CartVO, currency:String,locale:Locale):Map[String,Any]={
    var map :Map[String,Any]= Map()

    map+=("count" -> cart.count)

    val items = List()
    map+=("cartItemVOs" -> items)

    val coupons = List()
    map+=("coupons"-> coupons)

    map.++:(renderPriceCart(cart,currency,locale))

    map
  }

  def renderPriceCart(cart:CartVO, currency:String,locale:Locale)={
    val formatedPrice = 0
    val formatedEndPrice = 0
    val formatedReduction = 0
    val formatedFinalPrice = 0
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
