/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.model

import java.util.Date

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.annotation.{
  JsonDeserialize,
  JsonSerialize
}
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import com.mogobiz.pay.common.ExternalCode
import com.mogobiz.run.model.Mogobiz.LinearUnit.LinearUnit
import com.mogobiz.run.model.Mogobiz.ProductCalendar.ProductCalendar
import com.mogobiz.run.model.Mogobiz.ProductType.ProductType
import com.mogobiz.run.model.Mogobiz.ReductionRuleType.ReductionRuleType
import com.mogobiz.run.model.Mogobiz.WeightUnit.WeightUnit
import org.joda.time.DateTime

/**
  */
object Mogobiz {

  @JsonIgnoreProperties(ignoreUnknown = true)
  case class Country(code: String, name: String)

  @JsonIgnoreProperties(ignoreUnknown = true)
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

  @JsonIgnoreProperties(ignoreUnknown = true)
  case class Poi(description: String,
                 name: String,
                 picture: String,
                 xtype: String,
                 location: Location)

  @JsonIgnoreProperties(ignoreUnknown = true)
  case class LocalTaxRate(id: Long,
                          rate: Float,
                          countryCode: String,
                          stateCode: String)

  @JsonIgnoreProperties(ignoreUnknown = true)
  case class TaxRate(id: Long, name: String, localTaxRates: List[LocalTaxRate])

  @JsonIgnoreProperties(ignoreUnknown = true)
  case class Shipping(
      id: Long,
      weight: Long,
      @JsonScalaEnumeration(classOf[WeightUnitRef]) weightUnit: WeightUnit,
      width: Long,
      height: Long,
      depth: Long,
      @JsonScalaEnumeration(classOf[LinearUnitRef]) linearUnit: LinearUnit,
      amount: Long,
      free: Boolean)

  @JsonIgnoreProperties(ignoreUnknown = true)
  case class DatePeriod(startDate: DateTime, endDate: DateTime)

  @JsonIgnoreProperties(ignoreUnknown = true)
  case class IntraDayPeriod(id: Long,
                            startDate: DateTime,
                            endDate: DateTime,
                            weekday1: Boolean,
                            weekday2: Boolean,
                            weekday3: Boolean,
                            weekday4: Boolean,
                            weekday5: Boolean,
                            weekday6: Boolean,
                            weekday7: Boolean)

  @JsonIgnoreProperties(ignoreUnknown = true)
  case class Sku(id: Long,
                 uuid: String,
                 sku: String,
                 name: String,
                 externalCode: Option[String],
                 price: Long,
                 salePrice: Long,
                 minOrder: Long = 0,
                 maxOrder: Long = 0,
                 availabilityDate: Option[DateTime] = None,
                 startDate: Option[DateTime] = None,
                 stopDate: Option[DateTime] = None,
                 coupons: List[InnerCoupon],
                 nbSales: Long) {

    val computedExternalCode: Option[ExternalCode] =
      ExternalCode.fromString(externalCode)
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  case class InnerCoupon(id: Long)

  @JsonIgnoreProperties(ignoreUnknown = true)
  case class Product(
      id: Long,
      uuid: String,
      name: String,
      picture: String,
      @JsonScalaEnumeration(classOf[ProductTypeRef]) xtype: ProductType,
      @JsonScalaEnumeration(classOf[ProductCalendarRef]) calendarType: ProductCalendar,
      taxRate: Option[TaxRate],
      shipping: Option[Shipping],
      stockDisplay: Boolean,
      startDate: Option[DateTime],
      stopDate: Option[DateTime],
      availabilityDate: Option[DateTime],
      skus: List[Sku],
      intraDayPeriods: Option[List[IntraDayPeriod]],
      datePeriods: Option[List[DatePeriod]],
      poi: Option[Poi],
      nbSales: Long,
      downloadMaxTimes: Long,
      downloadMaxDelay: Long,
      category: Category,
      externalCode: Option[String],
      var lastUpdated: Date,
      var dateCreated: Date) {
    val shopId = externalCode.flatMap { externalCode =>
      val splitCode = externalCode.split("::")
      if (splitCode.length >= 3)
        Some(splitCode(0).toUpperCase + "::" + splitCode(2))
      else None
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  case class ReductionRule(
      id: Long,
      @JsonScalaEnumeration(classOf[ReductionRuleRef]) xtype: ReductionRuleType,
      @JsonDeserialize(contentAs = classOf[java.lang.Long]) quantityMin: Option[
        Long],
      @JsonDeserialize(contentAs = classOf[java.lang.Long]) quantityMax: Option[
        Long],
      discount: Option[String], //discount (or percent) if type is DISCOUNT (example : -1000 or 10%)
      @JsonDeserialize(contentAs = classOf[java.lang.Long]) xPurchased: Option[
        Long],
      @JsonDeserialize(contentAs = classOf[java.lang.Long]) yOffered: Option[
        Long])

  @JsonIgnoreProperties(ignoreUnknown = true)
  case class Category(id: Long,
                      parentId: Option[Long],
                      path: String,
                      name: String,
                      uuid: String)

  @JsonIgnoreProperties(ignoreUnknown = true)
  case class Suggestion(id: Long,
                        parentId: Long,
                        productId: Long,
                        discount: String,
                        var lastUpdated: Date,
                        var dateCreated: Date)

  @JsonIgnoreProperties(ignoreUnknown = true)
  case class Company(id: Long,
                     aesPassword: String,
                     name: String,
                     uuid: String,
                     code: String,
                     shipFrom: Option[ShipFromAddress],
                     phone: Option[String],
                     shippingInternational: Boolean)

  @JsonIgnoreProperties(ignoreUnknown = true)
  case class ShipFromAddress(longitude: String,
                             latitude: String,
                             road1: String,
                             road2: String,
                             road3: String,
                             roadNum: String,
                             postalCode: String,
                             city: String,
                             state: String,
                             country: Country)

  case class Consumption(id: Long,
                         boCartItemUuid: Option[String],
                         boProductUuid: String,
                         xdate: DateTime,
                         dateCreated: DateTime = DateTime.now,
                         lastUpdated: DateTime = DateTime.now,
                         uuid: String)

  case class BOProductConsumption(consumptionsFk: Long, consumptionId: Long)

  @JsonIgnoreProperties(ignoreUnknown = true)
  case class ShippingRule(id: Long,
                          uuid: String,
                          countryCode: String,
                          minAmount: Long,
                          maxAmount: Long,
                          price: Long,
                          var lastUpdated: Date,
                          var dateCreated: Date)

  object TransactionStatus extends Enumeration {
    type TransactionStatus = Value
    val PENDING = Value("PENDING")
    val PAYMENT_NOT_INITIATED = Value("PAYMENT_NOT_INITIATED")
    val FAILED = Value("FAILED")
    val COMPLETE = Value("COMPLETE")
  }
  class TransactionStatusRef extends TypeReference[TransactionStatus.type]

  object ProductType extends Enumeration {
    type ProductType = Value
    val SERVICE = Value("SERVICE")
    val PRODUCT = Value("PRODUCT")
    val DOWNLOADABLE = Value("DOWNLOADABLE")
    val PACKAGE = Value("PACKAGE")
    val OTHER = Value("OTHER")
  }

  class ProductTypeRef extends TypeReference[ProductType.type]

  object ProductCalendar extends Enumeration {
    type ProductCalendar = Value
    val NO_DATE = Value("NO_DATE")
    val DATE_ONLY = Value("DATE_ONLY")
    val DATE_TIME = Value("DATE_TIME")
  }

  class ProductCalendarRef extends TypeReference[ProductCalendar.type]

  object ReductionRuleType extends Enumeration {
    type ReductionRuleType = Value
    val DISCOUNT = Value("DISCOUNT")
    val X_PURCHASED_Y_OFFERED = Value("X_PURCHASED_Y_OFFERED")
    val CUSTOM = Value("CUSTOM")
  }

  class ReductionRuleRef extends TypeReference[ReductionRuleType.type]

  object WeightUnit extends Enumeration {
    type WeightUnit = Value
    val KG = Value("KG")
    val LB = Value("LB")
    val G = Value("G")
  }

  class WeightUnitRef extends TypeReference[WeightUnit.type]

  object LinearUnit extends Enumeration {
    type LinearUnit = Value
    val CM = Value("CM")
    val IN = Value("IN")
  }

  class LinearUnitRef extends TypeReference[LinearUnit.type]

  class InsufficientStockException(message: String = null,
                                   cause: Throwable = null)
      extends java.lang.Exception

  class ConcurrentUpdateStockException(message: String = null,
                                       cause: Throwable = null)
      extends java.lang.Exception

  object DeliveryStatus extends Enumeration {
    type DeliveryStatus = Value
    val NOT_STARTED = Value("NOT_STARTED") //  "unknown"
    val IN_PROGRESS = Value("IN_PROGRESS") // "pre_transit", "in_transit"
    val DELIVERED = Value("DELIVERED") //"delivered", "available_for_pickup"
    val RETURNED = Value("RETURNED") // "return_to_sender"
    val CANCELED = Value("CANCELED") // "cancelled"
    val ERROR = Value("ERROR") // "error", "failure"
  }

  class DeliveryStatusRef extends TypeReference[DeliveryStatus.type]

  object ReturnStatus extends Enumeration {
    type ReturnStatus = Value
    val RETURN_SUBMITTED = Value("RETURN_SUBMITTED")
    val RETURN_TO_BE_RECEIVED = Value("RETURN_TO_BE_RECEIVED")
    val RETURN_RECEIVED = Value("RETURN_RECEIVED")
    val RETURN_REFUSED = Value("RETURN_REFUSED")
    val RETURN_ACCEPTED = Value("RETURN_ACCEPTED")
  }

  class ReturnStatusRef extends TypeReference[ReturnStatus.type]

  object ReturnedItemStatus extends Enumeration {
    type ReturnedItemStatus = Value
    val UNDEFINED = Value("UNDEFINED")
    val NOT_AVAILABLE = Value("NOT_AVAILABLE")
    val BACK_TO_STOCK = Value("BACK_TO_STOCK")
    val DISCARDED = Value("DISCARDED")
  }

  class ReturnedItemStatusRef extends TypeReference[ReturnedItemStatus.type]
}
