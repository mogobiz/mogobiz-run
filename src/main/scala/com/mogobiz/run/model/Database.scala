package com.mogobiz.run.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.annotation.{JsonDeserialize, JsonSerialize}
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import com.mogobiz.run.cart.LinearUnit.LinearUnitType
import com.mogobiz.run.cart.ProductCalendar._
import com.mogobiz.run.cart.ProductType._
import com.mogobiz.run.cart.WeightUnit.WeightUnitType
import com.mogobiz.run.cart.{ProductCalendarRef, ProductTypeRef, LinearUnitRef, WeightUnitRef}
import com.mogobiz.run.json.{JodaDateTimeOptionSerializer, JodaDateTimeOptionDeserializer, JodaDateTimeDeserializer, JodaDateTimeSerializer}
import org.joda.time.DateTime

/**
 * Created by yoannbaudy on 19/11/2014.
 */
object Mogobiz {

  case class LocalTaxRate(id:Long,
                          rate:Float,
                          countryCode:String,
                          stateCode:String)

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

  case class DatePeriod(@JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                            @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                            startDate:DateTime,
                            @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                            @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                            endDate:DateTime)

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
                 stopDate:Option[DateTime]=None)

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
                     skus:List[Sku],
                     intraDayPeriods:Option[List[IntraDayPeriod]],
                     datePeriods:Option[List[DatePeriod]])

}
