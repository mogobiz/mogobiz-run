/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.model

import com.fasterxml.jackson.databind.annotation.{ JsonDeserialize, JsonSerialize }
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import com.mogobiz.run.json.{ JodaDateTimeOptionDeserializer, JodaDateTimeOptionSerializer }
import com.mogobiz.run.model.Mogobiz.LinearUnit.LinearUnit
import com.mogobiz.run.model.Mogobiz.ProductCalendar.ProductCalendar
import com.mogobiz.run.model.Mogobiz.ProductType.ProductType
import com.mogobiz.run.model.Mogobiz.{ WeightUnitRef, LinearUnitRef, Shipping }
import com.mogobiz.run.model.Mogobiz.WeightUnit.WeightUnit
import org.joda.time.DateTime
import org.joda.time.format.{ ISODateTimeFormat, DateTimeFormatter }
import org.json4s.JField
import org.json4s.JsonAST.{ JDouble, JBool, JObject, JValue }

import scala.collection.immutable.HashMap

/**
 */
object Render {

  case class Cart(validateUuid: Option[String], price: Long = 0, endPrice: Long = 0, reduction: Long = 0, finalPrice: Long = 0, count: Int = 0,
    cartItemVOs: Array[CartItem] = Array(), coupons: Array[Coupon] = Array())

  case class CartItem(id: String,
    productId: Long,
    productName: String,
    productPicture: String,
    productUrl: String,
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
    externalCodes: Option[String],
    downloadableLink: String)

  class Coupon(private val elems: List[JField], val active: Boolean, val price: Long, val promotion: Boolean)
      extends JObject(JField("active", JBool(active)) :: JField("promotion", JBool(promotion)) :: JField("price", JDouble(price)) :: elems.filterNot { jf: JField => jf._1 == "active" || jf._1 == "price" || jf._1 == "promotion" }) {

    val code: String = values("code").asInstanceOf[String]
    val name: String = values("name").asInstanceOf[String]
    val startDate: Option[DateTime] = values.get("startDate").map { v: Any =>
      JodaDateTimeOptionDeserializer.deserializeAsOption(v.asInstanceOf[String])
    }.getOrElse(None)
    val endDate: Option[DateTime] = values.get("endDate").map { v: Any =>
      JodaDateTimeOptionDeserializer.deserializeAsOption(v.asInstanceOf[String])
    }.getOrElse(None)
  }

  case class RegisteredCartItem(cartItemId: String = "",
    id: String,
    email: String,
    firstname: Option[String] = None,
    lastname: Option[String] = None,
    phone: Option[String] = None,
    @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])@JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer]) birthdate: Option[DateTime] = None,
    qrCodeContent: Option[String] = None)

}
