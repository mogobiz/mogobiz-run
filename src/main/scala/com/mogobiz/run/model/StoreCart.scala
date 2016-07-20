/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.model

import java.util.{UUID, Calendar, Date}

import com.fasterxml.jackson.databind.annotation.{JsonDeserialize, JsonSerialize}
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import com.mogobiz.pay.common.ExternalCode
import com.mogobiz.pay.model.Mogopay
import com.mogobiz.run.config.Settings
import com.mogobiz.run.json.{JodaDateTimeOptionDeserializer, JodaDateTimeOptionSerializer, JodaDateTimeDeserializer, JodaDateTimeSerializer}
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

case class CartWithPricesAndChanges(cart: StoreCartWithPrice, changes: CartChanges)

case class StoreCart(
    storeCode: String,
    dataUuid: String, // Valeur du cookie de tracking
    userUuid: Option[Mogopay.Document], // Uuid du l'utilisateur connecté
    boCartUuid: Option[String] = None, // uuid du boCart correspondant (quand le BOCart a été créé)
    transactionUuid: Option[String] = None, // Identifiant de la BOTransaction (quand la transaction est validée)
    externalOrderId: Option[String] = None, // Identifiant d'une commande issue d'un système externe (ex: MIRAKL)
    cartItems: List[StoreCartItem] = List(), // liste des items du panier
    coupons: List[StoreCoupon] = List(), // liste des coupons du panier
    validate: Boolean = false,           // Indique si le panier a été validé, c'est à dire si les stocks ont été décrémenté
    validateUuid: Option[String] = None, // Identifiant qui change à chaque validation. Permet au client de déterminer
    // s'il doit mettre à jour ces stocks (utilisé par Jahia par exemple pour flusher les caches)
    @JsonSerialize(using = classOf[JodaDateTimeSerializer]) @JsonDeserialize(using = classOf[JodaDateTimeDeserializer]) expireDate: DateTime =
      DateTime.now.plusSeconds(60 * Settings.Cart.Lifetime), // Date d'expiration du panier
    var dateCreated: Date = Calendar.getInstance().getTime, // Date de création du panier
    var lastUpdated: Date = Calendar.getInstance().getTime, // Date de dernière modification du panier
    countryCode: Option[String] = None,
    stateCode: Option[String] = None,
    rate: Option[Currency] = None) {

  val uuid: String = dataUuid + "--" + userUuid.getOrElse("None")

}

case class StoreCartWithPrice(storeCart: StoreCart,
                              cartItems: List[StoreCartItemWithPrice] = Nil,
                              coupons: List[CouponWithData] = Nil,
                              price: Long = 0,
                              endPrice: Long = 0,
                              reduction: Long = 0,
                              finalPrice: Long = 0)

case class StoreCartItem(indexEs: String,
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
                         price: Long,
                         salePrice: Long,
                         @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer]) @JsonDeserialize(
                             using = classOf[JodaDateTimeOptionDeserializer]) startDate: Option[DateTime],
                         @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer]) @JsonDeserialize(
                             using = classOf[JodaDateTimeOptionDeserializer]) endDate: Option[DateTime],
                         registeredCartItems: List[RegisteredCartItem],
                         shipping: Option[Shipping],
                         boCartItemUuid: Option[String],
                         downloadableLink: Option[String],
                         externalCodes: List[ExternalCode])

case class StoreCartItemWithPrice(cartItem: StoreCartItem,
                                  quantity: Int,
                                  price: Long,
                                  endPrice: Option[Long],
                                  tax: Option[Float],
                                  totalPrice: Long,
                                  totalEndPrice: Option[Long],
                                  salePrice: Long,
                                  saleEndPrice: Option[Long],
                                  saleTotalPrice: Long,
                                  saleTotalEndPrice: Option[Long],
                                  reduction: Long)

case class StoreCoupon(id: Long, code: String)

case class CouponWithData(coupon: Mogobiz.Coupon, active: Boolean, reduction: Long, promotion: Boolean)
