package com.mogobiz.run.model

import java.util.Date

import com.fasterxml.jackson.databind.annotation.{JsonDeserialize, JsonSerialize}
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import com.mogobiz.run.json.{JodaDateTimeOptionDeserializer, JodaDateTimeOptionSerializer, JodaDateTimeDeserializer, JodaDateTimeSerializer}
import com.mogobiz.run.model.Mogobiz.DeliveryStatus.DeliveryStatus
import com.mogobiz.run.model.Mogobiz.ReturnStatus.ReturnStatus
import com.mogobiz.run.model.Mogobiz.ReturnedItemStatus.ReturnedItemStatus
import com.mogobiz.run.model.Mogobiz.TransactionStatus.TransactionStatus
import com.mogobiz.run.model.Mogobiz._
import org.joda.time.DateTime

/**
 * Created by yoannbaudy on 18/02/2015.
 */
object ES {

  sealed trait BOCartBase {
    def transactionUuid: Option[String]

    def buyer: String

    def xdate: DateTime

    def price: Long

    def status: TransactionStatus

    def currencyCode: String

    def currencyRate: Double

    def dateCreated: Date

    def lastUpdated: Date

    def uuid: String
  }

  case class BOCartEx(transactionUuid: Option[String],
                      buyer: String,
                      @JsonSerialize(using = classOf[JodaDateTimeSerializer])
                      @JsonDeserialize(using = classOf[JodaDateTimeDeserializer])
                      xdate: DateTime,
                      price: Long,
                      @JsonScalaEnumeration(classOf[TransactionStatusRef])
                      status: TransactionStatus,
                      currencyCode: String,
                      currencyRate: Double,
                      var dateCreated: Date,
                      var lastUpdated: Date,
                      uuid: String) extends BOCartBase

  case class BOCart(transactionUuid: Option[String],
                    buyer: String,
                    @JsonSerialize(using = classOf[JodaDateTimeSerializer])
                    @JsonDeserialize(using = classOf[JodaDateTimeDeserializer])
                    xdate: DateTime,
                    price: Long,
                    @JsonScalaEnumeration(classOf[TransactionStatusRef])
                    status: TransactionStatus,
                    currencyCode: String,
                    currencyRate: Double,
                    var dateCreated: Date,
                    var lastUpdated: Date,
                    uuid: String,
                    cartItems: List[BOCartItem]) extends BOCartBase

  sealed trait BOCartItemBase {
    def code: String

    def price: Long

    def tax: Double

    def endPrice: Long

    def totalPrice: Long

    def totalEndPrice: Long

    def hidden: Boolean

    def quantity: Int

    def startDate: Option[DateTime]

    def endDate: Option[DateTime]

    def sku: Sku

    def secondary: List[BOProduct]

    def principal: BOProduct

    def bODelivery: Option[BODelivery]

    def bOReturnedItems: List[BOReturnedItem]

    def uuid: String

    def url: String

  }

  case class BOCartItem(code: String,
                        price: Long,
                        tax: Double,
                        endPrice: Long,
                        totalPrice: Long,
                        totalEndPrice: Long,
                        hidden: Boolean,
                        quantity: Int,
                        @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                        @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                        startDate: Option[DateTime],
                        @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                        @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                        endDate: Option[DateTime],
                        sku: Sku,
                        secondary: List[BOProduct],
                        principal: BOProduct,
                        bODelivery: Option[BODelivery],
                        bOReturnedItems: List[BOReturnedItem],
                        uuid: String,
                        url: String) extends BOCartItemBase


  case class BOCartItemEx(code: String,
                          price: Long,
                          tax: Double,
                          endPrice: Long,
                          totalPrice: Long,
                          totalEndPrice: Long,
                          hidden: Boolean,
                          quantity: Int,
                          @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                          @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                          startDate: Option[DateTime],
                          @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                          @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                          endDate: Option[DateTime],
                          sku: Sku,
                          secondary: List[BOProduct],
                          principal: BOProduct,
                          bODelivery: Option[BODelivery],
                          bOReturnedItems: List[BOReturnedItem],
                          uuid: String,
                          url: String,
                          var dateCreated: Date = new Date(),
                          var lastUpdated: Date = new Date(),
                          boCart: BOCartEx) extends BOCartItemBase


  case class BODelivery(@JsonScalaEnumeration(classOf[DeliveryStatusRef])
                        status: DeliveryStatus,
                        tracking: Option[String] = None,
                        extra: Option[String] = None,
                        uuid: String)

  case class BOReturnedItem(quantity: Int,
                            refunded: Long,
                            totalRefunded: Long,
                            @JsonScalaEnumeration(classOf[ReturnedItemStatusRef])
                            status: ReturnedItemStatus,
                            boReturns: List[BOReturn],
                            dateCreated: Date,
                            lastUpdated: Date,
                            uuid: String)

  case class BOReturn(motivation: Option[String],
                      @JsonScalaEnumeration(classOf[ReturnStatusRef])
                      status: ReturnStatus,
                      dateCreated: Date,
                      lastUpdated: Date,
                      uuid: String)

  case class BOProduct(acquittement: Boolean,
                       principal: Boolean,
                       price: Long,
                       product: Product,
                       registeredCartItem: List[BORegisteredCartItem],
                       uuid: String)


  case class BORegisteredCartItem(age: Int,
                                  quantity: Int,
                                  price: Long,
                                  ticketType: Option[String],
                                  firstname: Option[String],
                                  lastname: Option[String],
                                  email: Option[String],
                                  phone: Option[String],
                                  @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                                  @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                                  birthdate: Option[DateTime],
                                  shortCode: Option[String],
                                  @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                                  @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                                  startDate: Option[DateTime],
                                  @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                                  @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                                  endDate: Option[DateTime],
                                  uuid: String)

}
