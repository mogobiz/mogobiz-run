package com.mogobiz.run.model

import com.mogobiz.pay.model.Mogopay
import com.mogobiz.run.cart.ProductCalendar.ProductCalendar
import com.mogobiz.run.cart.ProductType.ProductType
import com.mogobiz.run.cart.{RegisteredCartItemVO, ShippingVO}
import org.joda.time.DateTime

case class StoreCart(uuid: String, userUuid: Option[Mogopay.Document],
                     cartItems: List[StoreCartItem] = List(), coupons: List[StoreCoupon] = List(),
                     inTransaction: Boolean = false)

case class StoreCartItem(id: String, productId: Long, productName: String, xtype: ProductType,
                         calendarType: ProductCalendar, skuId: Long, skuName: String, quantity: Int, price: Long,
                         startDate: Option[DateTime], endDate: Option[DateTime],
                         registeredCartItems: List[RegisteredCartItemVO], shipping: Option[ShippingVO])

case class StoreCoupon(id: Long, code : String)

