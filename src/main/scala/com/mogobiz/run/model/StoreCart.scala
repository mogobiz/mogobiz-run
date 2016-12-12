/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.model

import java.util.{Calendar, Date}

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.annotation.{JsonDeserialize, JsonSerialize}
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import com.mogobiz.pay.common.ExternalCode
import com.mogobiz.pay.model.Mogopay
import com.mogobiz.run.config.Settings
import com.mogobiz.run.handlers.BOCart
import com.mogobiz.run.json.{JodaDateTimeDeserializer, JodaDateTimeOptionDeserializer, JodaDateTimeOptionSerializer, JodaDateTimeSerializer}
import com.mogobiz.run.model.Mogobiz.ProductCalendar.ProductCalendar
import com.mogobiz.run.model.Mogobiz.ProductType.ProductType
import com.mogobiz.run.model.Mogobiz._
import com.mogobiz.run.model.Render.RegisteredCartItem
import org.joda.time.DateTime

case class SaleChange(esIndex: String,
                      product: Mogobiz.Product,
                      sku: Mogobiz.Sku,
                      newNbProductSales: Long,
                      newNbSkuSales: Long)

case class StockChange(esIndex: String, product: Product, sku: Sku, stock: Stock, stockCalendar: StockCalendar)

case class CartChanges(stockChanges: List[StockChange] = Nil,
                       boCartChange: Option[BOCart] = None,
                       deletedBOCart: Option[BOCart] = None,
                       saleChanges: List[SaleChange] = Nil)

case class CartWithChanges(cart: StoreCart, changes: CartChanges)

case class CartWithPricesAndChanges(cart: StoreCartWithPrices, changes: CartChanges)

trait RunCart {
  val uuid: String = dataUuid + "--" + userUuid.getOrElse("None")
  def storeCode: String
  def dataUuid: String
  def userUuid: Option[Mogopay.Document]
  def boCartUuid: Option[String]
  def transactionUuid: Option[String]
  def externalOrderId: Option[String]
  def validate: Boolean
  def validateUuid: Option[String]
  def shopCarts: List[RunShopCart]
  def expireDate: DateTime
  var dateCreated: Date
  var lastUpdated: Date
  def countryCode: Option[String]
  def stateCode: Option[String]
  def rate: Option[Currency]
}

trait RunShopCart {
  def shopId: String
  def shopTransactionUuid: Option[String]
  def cartItems: List[RunCartItem]
  def coupons: List[CartCoupon]
}

trait RunCartItem {
  def indexEs: String
  def id: String
  def productId: Long
  def productName: String
  def productPicture: String
  def productUrl: String
  def xtype: ProductType
  def calendarType: ProductCalendar
  def skuId: Long
  def skuName: String
  def quantity: Int
  def initPrice: Long
  def initSalePrice: Long
  def startDate: Option[DateTime]
  def endDate: Option[DateTime]
  def registeredCartItems: List[RegisteredCartItem]
  def shipping: Option[Shipping]
  def downloadableLink: Option[String]
  def externalCode: Option[ExternalCode]
}

@JsonIgnoreProperties(ignoreUnknown = true)
case class StoreCart(storeCode: String,
                     dataUuid: String,
                     userUuid: Option[Mogopay.Document],
                     boCartUuid: Option[String] = None,
                     transactionUuid: Option[String] = None,
                     externalOrderId: Option[String] = None,
                     validate: Boolean = false,
                     validateUuid: Option[String] = None,
                     shopCarts: List[StoreShopCart] = Nil,
                     @JsonSerialize(using = classOf[JodaDateTimeSerializer])
                     @JsonDeserialize(using = classOf[JodaDateTimeDeserializer])
                     expireDate: DateTime = DateTime.now.plusSeconds(60 * Settings.Cart.Lifetime),
                     var dateCreated: Date = Calendar.getInstance().getTime,
                     var lastUpdated: Date = Calendar.getInstance().getTime,
                     countryCode: Option[String] = None,
                     stateCode: Option[String] = None,
                     rate: Option[Currency] = None) extends RunCart {

  def findShopCart(shopId: String) = {
    shopCarts.find(_.shopId == shopId)
  }
}

case class StoreCartWithPrices(storeCode: String,
                               dataUuid: String,
                               userUuid: Option[Mogopay.Document],
                               boCartUuid: Option[String] = None,
                               transactionUuid: Option[String] = None,
                               externalOrderId: Option[String] = None,
                               validate: Boolean = false,
                               validateUuid: Option[String] = None,
                               shopCarts: List[StoreShopCartWithPrices] = Nil,
                               @JsonSerialize(using = classOf[JodaDateTimeSerializer])
                               @JsonDeserialize(using = classOf[JodaDateTimeDeserializer])
                               expireDate: DateTime = DateTime.now.plusSeconds(60 * Settings.Cart.Lifetime),
                               var dateCreated: Date = Calendar.getInstance().getTime,
                               var lastUpdated: Date = Calendar.getInstance().getTime,
                               countryCode: Option[String] = None,
                               stateCode: Option[String] = None,
                               rate: Option[Currency] = None,
                               totalPrice: Long,
                               totalEndPrice: Long,
                               totalReduction: Long,
                               totalFinalPrice: Long) extends RunCart {

  def this(cart: StoreCart,
           shopCarts: List[StoreShopCartWithPrices],
           totalPrice: Long,
           totalEndPrice: Long,
           totalReduction: Long,
           totalFinalPrice: Long) = this(cart.storeCode,
    cart.dataUuid,
    cart.userUuid,
    cart.boCartUuid,
    cart.transactionUuid,
    cart.externalOrderId,
    cart.validate,
    cart.validateUuid,
    shopCarts,
    cart.expireDate,
    cart.dateCreated,
    cart.lastUpdated,
    cart.countryCode,
    cart.stateCode,
    cart.rate,
    totalPrice,
    totalEndPrice,
    totalReduction,
    totalFinalPrice)

  def this(cart: StoreCartWithPrices, boCartUuid: String, shopCarts: List[StoreShopCartWithPrices]) = this(cart.storeCode,
    cart.dataUuid,
    cart.userUuid,
    Some(boCartUuid),
    cart.transactionUuid,
    cart.externalOrderId,
    cart.validate,
    cart.validateUuid,
    shopCarts,
    cart.expireDate,
    cart.dateCreated,
    cart.lastUpdated,
    cart.countryCode,
    cart.stateCode,
    cart.rate,
    cart.totalPrice,
    cart.totalEndPrice,
    cart.totalReduction,
    cart.totalFinalPrice)

  def findShopCart(shopId: String) = {
    shopCarts.find(_.shopId == shopId)
  }
}

case class StoreShopCart(shopId: String,
                         shopTransactionUuid: Option[String] = None,
                         cartItems: List[StoreCartItem] = Nil,
                         coupons: List[StoreCartCoupon] = Nil) extends RunShopCart

case class StoreShopCartWithPrices(shopId: String,
                                   shopTransactionUuid: Option[String] = None,
                                   cartItems: List[StoreCartItemWithPrices] = Nil,
                                   coupons: List[CouponWithPrices] = Nil,
                                   totalPrice: Long,
                                   totalEndPrice: Long,
                                   totalReduction: Long,
                                   totalFinalPrice: Long) extends RunShopCart {

  def this(shopCart: StoreShopCart,
           cartItems: List[StoreCartItemWithPrices],
           coupons: List[CouponWithPrices],
           totalPrice: Long,
           totalEndPrice: Long,
           totalReduction: Long,
           totalFinalPrice: Long) = this(shopCart.shopId,
    shopCart.shopTransactionUuid,
    cartItems,
    coupons,
    totalPrice,
    totalEndPrice,
    totalReduction,
    totalFinalPrice)

  def this(shopCart: StoreShopCartWithPrices,
           boShopCartUuid: String,
           newCartItems: List[StoreCartItemWithPrices]) = this(shopCart.shopId,
          shopCart.shopTransactionUuid,
          newCartItems,
          shopCart.coupons,
          shopCart.totalPrice,
          shopCart.totalEndPrice,
          shopCart.totalReduction,
          shopCart.totalFinalPrice)
}

case class StoreCartItem(indexEs: String,
                         id: String,
                         productId: Long,
                         productName: String,
                         productPicture: String,
                         productUrl: String,
                         @JsonScalaEnumeration(classOf[ProductTypeRef])
                         xtype: ProductType,
                         @JsonScalaEnumeration(classOf[ProductCalendarRef])
                         calendarType: ProductCalendar,
                         skuId: Long,
                         skuName: String,
                         quantity: Int,
                         initPrice: Long,
                         initSalePrice: Long,
                         @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                         @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                         startDate: Option[DateTime],
                         @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                         @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                         endDate: Option[DateTime],
                         registeredCartItems: List[RegisteredCartItem],
                         shipping: Option[Shipping],
                         downloadableLink: Option[String],
                         externalCode: Option[ExternalCode]) extends RunCartItem

case class StoreCartItemWithPrices(indexEs: String,
                                   id: String,
                                   productId: Long,
                                   productName: String,
                                   productPicture: String,
                                   productUrl: String,
                                   @JsonScalaEnumeration(classOf[ProductTypeRef]) xtype: ProductType,
                                   @JsonScalaEnumeration(classOf[ProductCalendarRef]) calendarType: ProductCalendar,
                                   skuId: Long,
                                   skuName: String,
                                   quantity: Int,
                                   initPrice: Long,
                                   initSalePrice: Long,
                                   @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                                   @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                                   startDate: Option[DateTime],
                                   @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                                   @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                                   endDate: Option[DateTime],
                                   registeredCartItems: List[RegisteredCartItem],
                                   shipping: Option[Shipping],
                                   downloadableLink: Option[String],
                                   externalCode: Option[ExternalCode],
                                   price: Long,
                                   endPrice: Option[Long],
                                   tax: Option[Float],
                                   totalPrice: Long,
                                   totalEndPrice: Option[Long],
                                   salePrice: Long,
                                   saleEndPrice: Option[Long],
                                   saleTotalPrice: Long,
                                   saleTotalEndPrice: Option[Long],
                                   reduction: Long) extends RunCartItem {

  def this(cartItem: RunCartItem,
           price: Long,
           endPrice: Option[Long],
           tax: Option[Float],
           totalPrice: Long,
           totalEndPrice: Option[Long],
           salePrice: Long,
           saleEndPrice: Option[Long],
           saleTotalPrice: Long,
           saleTotalEndPrice: Option[Long],
           reduction: Long) = this(cartItem.indexEs,
    cartItem.id,
    cartItem.productId,
    cartItem.productName,
    cartItem.productPicture,
    cartItem.productUrl,
    cartItem.xtype,
    cartItem.calendarType,
    cartItem.skuId,
    cartItem.skuName,
    cartItem.quantity,
    cartItem.initPrice,
    cartItem.initSalePrice,
    cartItem.startDate,
    cartItem.endDate,
    cartItem.registeredCartItems,
    cartItem.shipping,
    cartItem.downloadableLink,
    cartItem.externalCode,
    price,
    endPrice,
    tax,
    totalPrice,
    totalEndPrice,
    salePrice,
    saleEndPrice,
    saleTotalPrice,
    saleTotalEndPrice,
    reduction)

  def this(cartItem: StoreCartItemWithPrices,
           downloadableLink: Option[String],
           registeredCartItems: List[RegisteredCartItem]) = this(cartItem.indexEs,
    cartItem.id,
    cartItem.productId,
    cartItem.productName,
    cartItem.productPicture,
    cartItem.productUrl,
    cartItem.xtype,
    cartItem.calendarType,
    cartItem.skuId,
    cartItem.skuName,
    cartItem.quantity,
    cartItem.initPrice,
    cartItem.initSalePrice,
    cartItem.startDate,
    cartItem.endDate,
    registeredCartItems,
    cartItem.shipping,
    downloadableLink,
    cartItem.externalCode,
    cartItem.price,
    cartItem.endPrice,
    cartItem.tax,
    cartItem.totalPrice,
    cartItem.totalEndPrice,
    cartItem.salePrice,
    cartItem.saleEndPrice,
    cartItem.saleTotalPrice,
    cartItem.saleTotalEndPrice,
    cartItem.reduction)

}
