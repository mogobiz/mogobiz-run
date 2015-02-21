package com.mogobiz.run.model

import java.util.Date

import com.fasterxml.jackson.databind.annotation.{JsonDeserialize, JsonSerialize}
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import com.mogobiz.pay.model.Mogopay.TransactionStatusRef
import com.mogobiz.run.json.{JodaDateTimeOptionDeserializer, JodaDateTimeOptionSerializer, JodaDateTimeDeserializer, JodaDateTimeSerializer}
import com.mogobiz.run.model.Mogobiz.DeliveryStatus.DeliveryStatus
import com.mogobiz.run.model.Mogobiz.ReturnedItemStatus.ReturnedItemStatus
import com.mogobiz.run.model.Mogobiz.{Sku, ReturnedItemStatusRef, ReturnStatusRef, DeliveryStatusRef}
import com.mogobiz.run.model.Mogobiz.ReturnStatus.ReturnStatus
import com.mogobiz.run.model.Mogobiz.TransactionStatus.TransactionStatus
import com.mogobiz.run.model.Render.RegisteredCartItem
import org.joda.time.DateTime

/**
 * Created by yoannbaudy on 18/02/2015.
 */
object ES {

  case class BOCart(transactionUuid:Option[String],
                    buyer:String,
                    @JsonSerialize(using = classOf[JodaDateTimeSerializer])
                    @JsonDeserialize(using = classOf[JodaDateTimeDeserializer])
                    xdate:DateTime,
                    price:Long,
                    @JsonScalaEnumeration(classOf[TransactionStatusRef])
                    status:TransactionStatus,
                    currencyCode:String,
                    currencyRate:Double,
                    cartItems: List[BOCartItem],
                    var dateCreated:Date,
                    var lastUpdated:Date,
                    uuid : String)

  case class BOCartItem(code:String,
                        price:Long,
                        tax:Double,
                        endPrice:Long,
                        totalPrice:Long,
                        totalEndPrice:Long,
                        hidden:Boolean,
                        quantity:Int,
                        @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                        @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                        startDate:Option[DateTime],
                        @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                        @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                        endDate:Option[DateTime],
                        sku:Sku,
                        bOProducts: List[BOProduct],
                        bODelivery: Option[BODelivery],
                        bOReturnedItems: List[BOReturnedItem],
                        uuid : String)

  case class BODelivery(@JsonScalaEnumeration(classOf[DeliveryStatusRef])
                        status: DeliveryStatus,
                        tracking: Option[String] = None,
                        extra: Option[String] = None,
                        uuid: String)

  case class BOReturnedItem(motivation: Option[String],
                            @JsonScalaEnumeration(classOf[ReturnStatusRef])
                            returnStatus: ReturnStatus,
                            quantity: Int,
                            refunded: Long,
                            totalRefunded: Long,
                            @JsonScalaEnumeration(classOf[ReturnedItemStatusRef])
                            status: ReturnedItemStatus,
                            uuid: String)

  case class BOProduct(acquittement:Boolean,
                       principal:Boolean,
                       price:Long,
                       product:Product,
                       registeredCartItem: List[BORegisteredCartItem],
                       uuid : String)

  case class BORegisteredCartItem(age:Int,
                                  quantity:Int,
                                  price:Long,
                                  ticketType:Option[String],
                                  firstname:Option[String],
                                  lastname:Option[String],
                                  email:Option[String],
                                  phone:Option[String],
                                  @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                                  @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                                  birthdate:Option[DateTime],
                                  shortCode:Option[String],
                                  @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                                  @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                                  startDate:Option[DateTime],
                                  @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                                  @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                                  endDate:Option[DateTime],
                                  uuid:String)

}
