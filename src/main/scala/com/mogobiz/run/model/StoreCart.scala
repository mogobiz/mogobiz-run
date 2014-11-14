package com.mogobiz.run.model

import java.util.{Calendar, Date}

import com.fasterxml.jackson.databind.annotation.{JsonDeserialize, JsonSerialize}
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import com.mogobiz.pay.model.Mogopay
import com.mogobiz.run.cart.ProductCalendar.ProductCalendar
import com.mogobiz.run.cart.ProductType.ProductType
import com.mogobiz.run.cart.{ProductCalendarRef, ProductTypeRef, RegisteredCartItemVO, ShippingVO}
import com.mogobiz.run.config.Settings
import com.mogobiz.run.json.{JodaDateTimeOptionDeserializer, JodaDateTimeOptionSerializer, JodaDateTimeDeserializer, JodaDateTimeSerializer}
import org.joda.time.DateTime

case class StoreCart(uuid: String,
                     dataUuid: String,
                     userUuid: Option[Mogopay.Document],
                     cartItems: List[StoreCartItem] = List(),
                     coupons: List[StoreCoupon] = List(),
                     validate: Boolean = false,
                     inTransaction: Boolean = false,
                     @JsonSerialize(using = classOf[JodaDateTimeSerializer])
                     @JsonDeserialize(using = classOf[JodaDateTimeDeserializer])
                     expireDate: DateTime = DateTime.now.plusSeconds(60 * Settings.cart.lifetime),
                      //expireDate : Date = Calendar.getInstance().getTime,
                     var dateCreated: Date = Calendar.getInstance().getTime,
                     var lastUpdated: Date = Calendar.getInstance().getTime)

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
                         @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                         @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                         startDate: Option[DateTime],
                         @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                         @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                         endDate: Option[DateTime],
                         registeredCartItems: List[RegisteredCartItemVO],
                         shipping: Option[ShippingVO])

case class StoreCoupon(id: Long, code : String, companyCode: String)

