/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.model

import java.util.{UUID, Calendar, Date}

import com.fasterxml.jackson.databind.annotation.{JsonDeserialize, JsonSerialize}
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import com.mogobiz.pay.model.Mogopay
import com.mogobiz.run.config.Settings
import com.mogobiz.run.json.{JodaDateTimeOptionDeserializer, JodaDateTimeOptionSerializer, JodaDateTimeDeserializer, JodaDateTimeSerializer}
import com.mogobiz.run.model.Mogobiz.ProductCalendar.ProductCalendar
import com.mogobiz.run.model.Mogobiz.ProductType.ProductType
import com.mogobiz.run.model.Mogobiz.{ProductCalendarRef, ProductTypeRef, Shipping}
import com.mogobiz.run.model.Render.RegisteredCartItem
import org.joda.time.DateTime

case class StoreCart(storeCode: String,
                     dataUuid: String, // Valeur du cookie de tracking
                     userUuid: Option[Mogopay.Document],  // Uuid du l'utilisateur connecté
                     boCartUuid : Option[String] = None, // uuid du boCart correspondant (quand le BOCart a été créé)
                     transactionUuid : Option[String] = None, // Identifiant de la BOTransaction (quand la transaction est validée)
                     cartItems: List[StoreCartItem] = List(), // liste des items du panier
                     coupons: List[StoreCoupon] = List(), // liste des coupons du panier
                     validate: Boolean = false, // Indique si le panier a été validé, c'est à dire si les stocks ont été décrémenté
                     validateUuid: Option[String] = None, // Identifiant qui change à chaque validation. Permet au client de déterminer
                                                  // s'il doit mettre à jour ces stocks (utilisé par Jahia par exemple pour flusher les caches)
                     @JsonSerialize(using = classOf[JodaDateTimeSerializer])
                     @JsonDeserialize(using = classOf[JodaDateTimeDeserializer])
                     expireDate: DateTime = DateTime.now.plusSeconds(60 * Settings.cart.lifetime), // Date d'expiration du panier
                     var dateCreated: Date = Calendar.getInstance().getTime,  // Date de création du panier
                     var lastUpdated: Date = Calendar.getInstance().getTime,  // Date de dernière modification du panier
                     countryCode: Option[String] = None,
                     stateCode: Option[String] = None,
                     rate: Option[Currency] = None)  {

  val uuid: String = dataUuid + "--" + userUuid.getOrElse("None")

}

case class StoreCartItem(indexEs: String,
                         id: String,
                         productId: Long,
                         productName: String,
                         productUrl: String,
                         @JsonScalaEnumeration(classOf[ProductTypeRef])
                         xtype: ProductType,
                         @JsonScalaEnumeration(classOf[ProductCalendarRef])
                         calendarType: ProductCalendar,
                         skuId: Long,
                         skuName: String,
                         quantity: Int,
                         price: Long,
                         salePrice: Long,
                         @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                         @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                         startDate: Option[DateTime],
                         @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                         @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                         endDate: Option[DateTime],
                         registeredCartItems: List[RegisteredCartItem],
                         shipping: Option[Shipping],
                         boCartItemUuid: Option[String],
                         downloadableLink: Option[String])

case class StoreCoupon(id: Long, code : String)

