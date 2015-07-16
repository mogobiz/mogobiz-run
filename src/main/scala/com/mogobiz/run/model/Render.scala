/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.model

import com.fasterxml.jackson.databind.annotation.{JsonDeserialize, JsonSerialize}
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import com.mogobiz.run.json.{JodaDateTimeOptionDeserializer, JodaDateTimeOptionSerializer}
import com.mogobiz.run.model.Mogobiz.LinearUnit.LinearUnit
import com.mogobiz.run.model.Mogobiz.ProductCalendar.ProductCalendar
import com.mogobiz.run.model.Mogobiz.ProductType.ProductType
import com.mogobiz.run.model.Mogobiz.{WeightUnitRef, LinearUnitRef, Shipping}
import com.mogobiz.run.model.Mogobiz.WeightUnit.WeightUnit
import org.joda.time.DateTime

/**
 * Created by yoannbaudy on 26/11/2014.
 */
object Render {

  case class Cart(price: Long = 0, endPrice: Long = 0, reduction: Long = 0, finalPrice: Long = 0, count: Int = 0,
                  cartItemVOs: Array[CartItem] = Array(), coupons: Array[Coupon] = Array())

  case class CartItem(id: String,
                      productId: Long,
                      productName: String,
                      xtype: ProductType,
                      calendarType: ProductCalendar,
                      skuId: Long,
                      skuName: String,
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
                      startDate: Option[DateTime],
                      endDate: Option[DateTime],
                      registeredCartItemVOs: Array[RegisteredCartItem],
                      shipping: Option[Shipping],
                      downloadableLink: String)

  case class Coupon(id: Long,
                    name: String,
                    code: String,
                    @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                    @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                    startDate: Option[DateTime] = None,
                    @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                    @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                    endDate: Option[DateTime] = None,
                    active: Boolean = false,
                    price: Long = 0)

  case class RegisteredCartItem(cartItemId: String = "",
                                  id: String,
                                  email: String,
                                  firstname: Option[String] = None,
                                  lastname: Option[String] = None,
                                  phone: Option[String] = None,
                                  @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                                  @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                                  birthdate: Option[DateTime] = None,
                                  qrCodeContent: Option[String] = None)

}
