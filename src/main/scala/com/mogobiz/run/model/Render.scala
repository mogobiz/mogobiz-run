/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.model

import com.mogobiz.json.JacksonConverter
import com.mogobiz.pay.common.ExternalCode
import com.mogobiz.run.model.Mogobiz.ProductCalendar.ProductCalendar
import com.mogobiz.run.model.Mogobiz.ProductType.ProductType
import com.mogobiz.run.model.Mogobiz.Shipping
import org.joda.time.DateTime
import org.json4s.JField
import org.json4s.JsonAST.{JBool, JDouble, JObject}

/**
  */
object Render {

  case class Cart(validateUuid: Option[String],
                  price: Long = 0,
                  endPrice: Long = 0,
                  reduction: Long = 0,
                  finalPrice: Long = 0,
                  count: Int = 0,
                  cartItemVOs: Array[CartItem] = Array(),
                  coupons: Array[Coupon] = Array())

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
                      externalCode: Option[ExternalCode],
                      downloadableLink: String)

  class Coupon(private val elems: List[JField],
               val active: Boolean,
               val price: Long,
               val promotion: Boolean)
      extends JObject(JField("active", JBool(active)) :: JField(
        "promotion",
        JBool(promotion)) :: JField("price", JDouble(price)) :: elems
        .filterNot { jf: JField =>
          jf._1 == "active" || jf._1 == "price" || jf._1 == "promotion"
        }) {

    val code: String = values("code").asInstanceOf[String]
    val name: String = values("name").asInstanceOf[String]
    val startDate: Option[DateTime] = values
      .get("startDate")
      .flatMap { v: Any =>
        Option(JacksonConverter.deserialize[DateTime](v.asInstanceOf[String]))
      }

    val endDate: Option[DateTime] = values
      .get("endDate")
      .flatMap { v: Any =>
        Option(JacksonConverter.deserialize[DateTime](v.asInstanceOf[String]))
      }
  }

  case class RegisteredCartItem(cartItemId: String = "",
                                id: String,
                                email: String,
                                firstname: Option[String] = None,
                                lastname: Option[String] = None,
                                phone: Option[String] = None,
                                birthdate: Option[DateTime] = None,
                                qrCodeContent: Option[String] = None)

}
