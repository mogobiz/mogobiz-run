/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.model

import java.util.Date

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.annotation.{JsonDeserialize, JsonSerialize}
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import com.mogobiz.run.json.{JodaDateTimeOptionSerializer, JodaDateTimeOptionDeserializer, JodaDateTimeDeserializer, JodaDateTimeSerializer}
import com.mogobiz.run.model.Mogobiz.LinearUnit.LinearUnitType
import com.mogobiz.run.model.Mogobiz.DeliveryStatus.DeliveryStatus
import com.mogobiz.run.model.Mogobiz.ProductCalendar.ProductCalendar
import com.mogobiz.run.model.Mogobiz.ProductType.ProductType
import com.mogobiz.run.model.Mogobiz.ReductionRuleType.ReductionRuleType
import com.mogobiz.run.model.Mogobiz.TransactionStatus.TransactionStatus
import com.mogobiz.run.model.Mogobiz.ReturnStatus.ReturnStatus
import com.mogobiz.run.model.Mogobiz.ReturnedItemStatus.ReturnedItemStatus
import com.mogobiz.run.model.Mogobiz.WeightUnit.WeightUnitType
import org.joda.time.DateTime

/**
 * Created by yoannbaudy on 19/11/2014.
 */
object Mogobiz {

  @JsonIgnoreProperties(ignoreUnknown=true)
  case class Country(code: String,
                     name: String)

  @JsonIgnoreProperties(ignoreUnknown=true)
  case class Location(latitude: Double,
                      longitude: Double,
                      postalCode: Option[String],
                      road1: Option[String],
                      road2: Option[String],
                      road3: Option[String],
                      roadNum: Option[String],
                      city: Option[String],
                      country: Option[Country],
                      state: Option[String])

  @JsonIgnoreProperties(ignoreUnknown=true)
  case class Poi(description: String,
                 name: String,
                 picture: String,
                 xtype: String,
                 location: Location)

  @JsonIgnoreProperties(ignoreUnknown=true)
  case class LocalTaxRate(id:Long,
                          rate:Float,
                          countryCode:String,
                          stateCode:String)

  @JsonIgnoreProperties(ignoreUnknown=true)
  case class TaxRate(id:Long,
                     name:String,
                     localTaxRates: List[LocalTaxRate])

  @JsonIgnoreProperties(ignoreUnknown=true)
  case class Shipping(id: Long,
                      weight: Long,
                      @JsonScalaEnumeration(classOf[WeightUnitRef])
                      weightUnit: WeightUnitType,
                      width: Long,
                      height: Long,
                      depth: Long,
                      @JsonScalaEnumeration(classOf[LinearUnitRef])
                      linearUnit: LinearUnitType,
                      amount: Long,
                      free: Boolean)

  @JsonIgnoreProperties(ignoreUnknown=true)
  case class DatePeriod(@JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                            @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                            startDate:DateTime,
                            @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                            @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                            endDate:DateTime)

  @JsonIgnoreProperties(ignoreUnknown=true)
  case class IntraDayPeriod(id:Long,
                            @JsonSerialize(using = classOf[JodaDateTimeSerializer])
                            @JsonDeserialize(using = classOf[JodaDateTimeDeserializer])
                            startDate:DateTime,
                            @JsonSerialize(using = classOf[JodaDateTimeSerializer])
                            @JsonDeserialize(using = classOf[JodaDateTimeDeserializer])
                            endDate:DateTime,
                            weekday1: Boolean,
                            weekday2: Boolean,
                            weekday3: Boolean,
                            weekday4: Boolean,
                            weekday5: Boolean,
                            weekday6: Boolean,
                            weekday7: Boolean)

  @JsonIgnoreProperties(ignoreUnknown=true)
  case class Sku(id:Long,
                 uuid:String,
                 sku:String,
                 name:String,
                 price:Long,
                 salePrice:Long,
                 minOrder:Long=0,
                 maxOrder:Long=0,
                 @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                 @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                 availabilityDate:Option[DateTime]=None,
                 @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                 @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                 startDate:Option[DateTime]=None,
                 @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                 @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                 stopDate:Option[DateTime]=None,
                 coupons: List[InnerCoupon],
                 nbSales: Long)

  @JsonIgnoreProperties(ignoreUnknown=true)
  case class InnerCoupon(id:Long)

  @JsonIgnoreProperties(ignoreUnknown=true)
  case class Product(id:Long,
                     uuid:String,
                     name:String,
                     @JsonScalaEnumeration(classOf[ProductTypeRef])
                     xtype:ProductType,
                     @JsonScalaEnumeration(classOf[ProductCalendarRef])
                     calendarType:ProductCalendar,
                     taxRate: Option[TaxRate],
                     shipping:Option[Shipping],
                     stockDisplay: Boolean,
                     @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                     @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                     startDate:Option[DateTime],
                     @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                     @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                     stopDate:Option[DateTime],
                     @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                     @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                     availabilityDate:Option[DateTime],
                     skus:List[Sku],
                     intraDayPeriods:Option[List[IntraDayPeriod]],
                     datePeriods:Option[List[DatePeriod]],
                     poi: Option[Poi],
                     nbSales: Long,
                     downloadMaxTimes: Long,
                     downloadMaxDelay: Long,
                     var lastUpdated: Date,
                     var dateCreated: Date)

  @JsonIgnoreProperties(ignoreUnknown=true)
  case class ReductionRule(id:Long,
                           @JsonScalaEnumeration(classOf[ReductionRuleRef])
                           xtype: ReductionRuleType,
                           @JsonDeserialize(contentAs = classOf[java.lang.Long])
                           quantityMin:Option[Long],
                           @JsonDeserialize(contentAs = classOf[java.lang.Long])
                           quantityMax:Option[Long],
                           discount:Option[String], //discount (or percent) if type is DISCOUNT (example : -1000 or 10%)
                           @JsonDeserialize(contentAs = classOf[java.lang.Long])
                           xPurchased:Option[Long],
                           @JsonDeserialize(contentAs = classOf[java.lang.Long])
                           yOffered:Option[Long])

  @JsonIgnoreProperties(ignoreUnknown=true)
  case class Coupon(id: Long,
                    name: String,
                    code: String,
                    @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                    @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                    startDate: Option[DateTime],
                    @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                    @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                    endDate: Option[DateTime],
                    @JsonDeserialize(contentAs = classOf[java.lang.Long])
                    numberOfUses:Option[Long],
                    @JsonDeserialize(contentAs = classOf[java.lang.Long])
                    sold:Option[Long],
                    rules:List[ReductionRule],
                    active: Boolean,
                    anonymous:Boolean = false,
                    catalogWise:Boolean = false,
                    description: String,
                    pastille: String)

  @JsonIgnoreProperties(ignoreUnknown=true)
  case class Suggestion(id:Long,
                        parentId:Long,
                        productId:Long,
                        discount:String,
                        var lastUpdated: Date,
                        var dateCreated: Date)

  case class BOCart(id:Long,
                    buyer:String,
                    companyFk:Long,
                    currencyCode:String,
                    currencyRate:Double,
                    xdate:DateTime,
                    dateCreated:DateTime,
                    lastUpdated:DateTime,
                    price:Long,
                    status:TransactionStatus,
                    transactionUuid:Option[String],
                    uuid: String)

  case class BOProduct(id:Long,
                       acquittement:Boolean,
                       price:Long,
                       principal:Boolean,
                       productFk:Long,
                       dateCreated:DateTime,
                       lastUpdated:DateTime,
                       uuid : String)

  case class BOTicketType(id:Long,
                          quantity:Int,
                          price:Long,
                          shortCode:Option[String],
                          ticketType:Option[String],
                          firstname:Option[String],
                          lastname:Option[String],
                          email:Option[String],
                          phone:Option[String],
                          age:Int,
                          birthdate:Option[DateTime],
                          startDate:Option[DateTime],
                          endDate:Option[DateTime],
                          qrcode:Option[String],
                          qrcodeContent:Option[String],
                          bOProductFk : Long,
                          dateCreated:DateTime,
                          lastUpdated:DateTime,
                          uuid:String)

  case class BOCartItem(id:Long,
                        code:String,
                        price:Long,
                        tax:Double,
                        endPrice:Long,
                        totalPrice:Long,
                        totalEndPrice:Long,
                        hidden:Boolean,
                        quantity:Int,
                        startDate:Option[DateTime],
                        endDate:Option[DateTime],
                        ticketTypeFk:Long,
                        bOCartFk : Long,
                        @JsonDeserialize(contentAs = classOf[java.lang.Long])
                        bODeliveryFk : Option[Long],
                        dateCreated:DateTime,
                        lastUpdated:DateTime,
                        uuid : String,
                        url: String)

  case class BODelivery(id: Long,
                        bOCartFk: Long,
                        @JsonScalaEnumeration(classOf[DeliveryStatusRef])
                        status: DeliveryStatus,
                        tracking: Option[String] = None,
                        extra: Option[String] = None,
                        dateCreated: DateTime = DateTime.now,
                        lastUpdated: DateTime = DateTime.now,
                        uuid: String)

  case class BOReturn(id: Long,
                      bOReturnedItemFk: Long,
                      motivation: Option[String],
                      @JsonScalaEnumeration(classOf[ReturnStatusRef])
                      status: ReturnStatus,
                      dateCreated: DateTime = DateTime.now,
                      lastUpdated: DateTime = DateTime.now,
                      uuid: String)

  case class BOReturnedItem(id: Long,
                            bOCartItemFk: Long,
                            quantity: Int,
                            refunded: Long,
                            totalRefunded: Long,
                            @JsonScalaEnumeration(classOf[ReturnedItemStatusRef])
                            status: ReturnedItemStatus,
                            dateCreated: DateTime = DateTime.now,
                            lastUpdated: DateTime = DateTime.now,
                            uuid: String)

  @JsonIgnoreProperties(ignoreUnknown=true)
  case class Company(id: Long,
                      aesPassword: String,
                      name: String,
                      uuid: String,
                      code: String,
                      shipFrom : Option[ShipFromAddress],
                      phone : Option[String])

  @JsonIgnoreProperties(ignoreUnknown=true)
  case class ShipFromAddress(longitude: String,
                             latitude : String,
                             road1: String,
                             road2: String,
                             road3: String,
                             roadNum : String,
                             postalCode: String,
                             city : String,
                             state : String,
                             country : Country)

  case class Consumption(id: Long,
                         @JsonDeserialize(contentAs = classOf[java.lang.Long])
                         bOTicketTypeFk: Option[Long],
                         xdate: DateTime,
                         dateCreated: DateTime = DateTime.now,
                         lastUpdated: DateTime = DateTime.now,
                         uuid: String)

  case class BOProductConsumption(consumptionsFk: Long,
                                  consumptionId: Long)

  @JsonIgnoreProperties(ignoreUnknown=true)
  case class ShippingRule(id: Long,
                           uuid: String,
                           countryCode: String,
                           minAmount: Long,
                           maxAmount: Long,
                           price: Long,
                           var lastUpdated: Date,
                           var dateCreated: Date)

  object TransactionStatus extends Enumeration {
    class TransactionStatusType(s: String) extends Val(s)
    type TransactionStatus = TransactionStatusType
    val PENDING = new TransactionStatusType("PENDING")
    val PAYMENT_NOT_INITIATED = new TransactionStatusType("PAYMENT_NOT_INITIATED")
    val FAILED = new TransactionStatusType("FAILED")
    val COMPLETE = new TransactionStatusType("COMPLETE")

    def valueOf(str:String):TransactionStatus = str match {
      case "PENDING"=> PENDING
      case "PAYMENT_NOT_INITIATED"=> PAYMENT_NOT_INITIATED
      case "FAILED"=> FAILED
      case "COMPLETE"=> COMPLETE
    }

    override def toString = this match {
      case PENDING => "PENDING"
      case PAYMENT_NOT_INITIATED => "PAYMENT_NOT_INITIATED"
      case FAILED => "FAILED"
      case COMPLETE => "COMPLETE"
      case _ => "Invalid value"
    }
  }
  class TransactionStatusRef extends TypeReference[TransactionStatus.type]

  object ProductType extends Enumeration {
    class ProductTypeType(s: String) extends Val(s)
    type ProductType = ProductTypeType
    val SERVICE = new ProductTypeType("SERVICE")
    val PRODUCT = new ProductTypeType("PRODUCT")
    val DOWNLOADABLE = new ProductTypeType("DOWNLOADABLE")
    val PACKAGE = new ProductTypeType("PACKAGE")
    val OTHER = new ProductTypeType("OTHER")

    def valueOf(str: String): ProductType = str match {
      case "SERVICE" => SERVICE
      case "PRODUCT" => PRODUCT
      case "DOWNLOADABLE" => DOWNLOADABLE
      case "PACKAGE" => PACKAGE
      case _ => OTHER
    }

  }
  class ProductTypeRef extends TypeReference[ProductType.type]

  object ProductCalendar extends Enumeration {
    class ProductCalendarType(s: String) extends Val(s)
    type ProductCalendar = ProductCalendarType
    val NO_DATE = new ProductCalendarType("NO_DATE")
    val DATE_ONLY = new ProductCalendarType("DATE_ONLY")
    val DATE_TIME = new ProductCalendarType("DATE_TIME")

    def valueOf(str: String): ProductCalendar = str match {
      case "DATE_ONLY" => DATE_ONLY
      case "DATE_TIME" => DATE_TIME
      case _ => NO_DATE
    }
  }
  class ProductCalendarRef extends TypeReference[ProductCalendar.type]

  object ReductionRuleType extends Enumeration {
    class ReductionRuleTypeType(s: String) extends Val(s)
    type ReductionRuleType = ReductionRuleTypeType
    val DISCOUNT = new ReductionRuleTypeType("DISCOUNT")
    val X_PURCHASED_Y_OFFERED = new ReductionRuleTypeType("X_PURCHASED_Y_OFFERED")

    def apply(name:String) = name match{
      case "DISCOUNT" => DISCOUNT
      case "X_PURCHASED_Y_OFFERED" => X_PURCHASED_Y_OFFERED
      case _ => throw new Exception("Not expected ReductionRuleType")
    }
  }
  class ReductionRuleRef extends TypeReference[ReductionRuleType.type]

  object WeightUnit extends Enumeration {
    class WeightUnitType(s: String) extends Val(s)
    type WeightUnit = WeightUnitType
    val KG = new WeightUnitType("KG")
    val LB = new WeightUnitType("LB")
    val G = new WeightUnitType("G")

    def apply(str: String) = str match {
      case "KG" => KG
      case "LB" => LB
      case "G" => G
      case _ => throw new RuntimeException("unexpected WeightUnit value")
    }
  }
  class WeightUnitRef extends TypeReference[WeightUnit.type]

  object LinearUnit extends Enumeration {
    class LinearUnitType(s: String) extends Val(s)
    type LinearUnit = LinearUnitType
    val CM = new LinearUnitType("CM")
    val IN = new LinearUnitType("IN")

    def apply(str: String) = str match {
      case "CM" => CM
      case "IN" => IN
      case _ => throw new RuntimeException("unexpected LinearUnit value")
    }
  }
  class LinearUnitRef extends TypeReference[LinearUnit.type]

  class InsufficientStockException(message: String = null, cause: Throwable = null) extends java.lang.Exception

  class ConcurrentUpdateStockException(message: String = null, cause: Throwable = null) extends java.lang.Exception

  object DeliveryStatus extends Enumeration {
    class DeliveryStatusType(s: String) extends Val(s)
    type DeliveryStatus = DeliveryStatusType
    val NOT_STARTED = new DeliveryStatusType("NOT_STARTED")
    val IN_PROGRESS = new DeliveryStatusType("IN_PROGRESS")
    val DELIVERED = new DeliveryStatusType("DELIVERED")
    val RETURNED = new DeliveryStatusType("RETURNED")
    val CANCELED = new DeliveryStatusType("CANCELED")

    def apply(str: String) = str match {
      case "NOT_STARTED" => NOT_STARTED
      case "IN_PROGRESS" => IN_PROGRESS
      case "DELIVERED" => DELIVERED
      case "RETURNED" => RETURNED
      case "CANCELED" => CANCELED
      case _ => throw new RuntimeException("unexpected DeliveryStatus value")
    }
  }
  class DeliveryStatusRef extends TypeReference[DeliveryStatus.type]

  object ReturnStatus extends Enumeration {
    class ReturnStatusType(s: String) extends Val(s)
    type ReturnStatus = ReturnStatusType
    val RETURN_SUBMITTED = new ReturnStatusType("RETURN_SUBMITTED")
    val RETURN_TO_BE_RECEIVED = new ReturnStatusType("RETURN_TO_BE_RECEIVED")
    val RETURN_RECEIVED = new ReturnStatusType("RETURN_RECEIVED")
    val RETURN_REFUSED = new ReturnStatusType("RETURN_REFUSED")
    val RETURN_ACCEPTED = new ReturnStatusType("RETURN_ACCEPTED")

    def apply(str: String) = str match {
      case "RETURN_SUBMITTED" => RETURN_SUBMITTED
      case "RETURN_TO_BE_RECEIVED" => RETURN_TO_BE_RECEIVED
      case "RETURN_RECEIVED" => RETURN_RECEIVED
      case "RETURN_REFUSED" => RETURN_REFUSED
      case "RETURN_ACCEPTED" => RETURN_ACCEPTED
      case _ => throw new RuntimeException("unexpected ReturnStatus value")
    }
  }
  class ReturnStatusRef extends TypeReference[ReturnStatus.type]

  object ReturnedItemStatus extends Enumeration {
    class ReturnedItemStatusType(s: String) extends Val(s)
    type ReturnedItemStatus = ReturnedItemStatusType
    val UNDEFINED = new ReturnedItemStatusType("UNDEFINED")
    val NOT_AVAILABLE = new ReturnedItemStatusType("NOT_AVAILABLE")
    val BACK_TO_STOCK = new ReturnedItemStatusType("BACK_TO_STOCK")
    val DISCARDED = new ReturnedItemStatusType("DISCARDED")

    def apply(str: String) = str match {
      case "UNDEFINED" => UNDEFINED
      case "NOT_AVAILABLE" => NOT_AVAILABLE
      case "BACK_TO_STOCK" => BACK_TO_STOCK
      case "DISCARDED" => DISCARDED
      case _ => throw new RuntimeException("unexpected ReturnedItemStatus value")
    }
  }
  class ReturnedItemStatusRef extends TypeReference[ReturnedItemStatus.type]
}
