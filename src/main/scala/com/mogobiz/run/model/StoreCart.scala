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

case class StoreCart(// Identifiant du panier (Sert à identifier l'entrée dans ES et l'unique BOTransaction correspondant).
                      // Il est constitué du dataUuid et du userUuid
                     storeCode: String,
                     dataUuid: String, // Valeur du cookie de tracking
                     userUuid: Option[Mogopay.Document],  // Uuid du l'utilisateur connecté
                     transactionUuid : String = UUID.randomUUID().toString, // Identifiant de la BOTransaction
                     cartItems: List[StoreCartItem] = List(), // liste des items du panier
                     coupons: List[StoreCoupon] = List(), // liste des coupons du panier
                     validate: Boolean = false, // Indique si le panier a été validé, c'est à dire si les stocks ont été décrémenté
                     inTransaction: Boolean = false, // Indique si le panier est associé à une BOTransaction
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

case class StoreCartItem(id: String,
                         productId: Long,
                         productName: String,
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
                         shipping: Option[Shipping])

case class StoreCoupon(id: Long, code : String)

