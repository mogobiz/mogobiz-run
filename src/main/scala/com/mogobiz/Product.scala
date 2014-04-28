package com.mogobiz

import spray.http.DateTime
import java.util.Date
/**
 * Created by Christophe on 17/02/14.
 */
case class Product(
                    code: String
                    , name: String
                    , xtype: String
                    , price: Long
                    /*plus tard
                                        ,state:Option[String],creationDate:DateTime,modificationDate:Option[DateTime]
                                        ,description:String, startDate:Option[DateTime],stopDate:Option[DateTime]
                                        ,startFeatureDate:Option[DateTime],stopFeatureDate:Option[DateTime]
                                        ,facebookCredit:Option[Long]
                                        ,nbSales:Long = 0
                                        ,picture:Option[String]
                                        ,stockDisplay:Boolean = true
                                        ,calendarType:Option[String]
                                        ,uuid:Option[String]
                                        ,hide:Boolean = false
                                        ,sanitazedName:String*/
                    ) {

  //  def this(code:String,name:String,xtype:String,price:Long) = this(code,name,xtype,price,None,new DateTime(),None,"",None,None,None,None,None,0,None,true,None,None,false,name)

  /**
   * Constructeur ultra light pour tester au plus vite
   * @param code
   * @param name
   * @return
   */
  def this(code: String, name: String) = this(code, name, "", 0)

}

//class LangParam(lang:String)

class CommonProductParameters(val lang:String,val currency:Option[String],val country:Option[String])
//,lang:String,currency:String,country:String
case class FulltextSearchProductParameters(
                                            _lang:String
                                            ,_currency:Option[String]
                                            ,_country:Option[String]
                                            , val query:String
                                            , val highlight:Boolean) extends CommonProductParameters(_lang,_currency,_country)

case class ProductRequest(
                           maxItemPerPage: Option[Int]
                           , pageOffset: Option[Int]
                           , xtype: Option[String]
                           , name: Option[String]
                           , code: Option[String]
                           , categoryId: Option[Int]
                           , brandId: Option[Int]
                           , path:Option[String]
                           , tagName: Option[String]
                           , priceMin: Option[Long]
                           , priceMax: Option[Long]
                           , orderBy: Option[String]
                           , orderDirection: Option[String]
                           , featured: Option[Boolean] // = false
                           , lang: String
                           , currencyCode: Option[String]
                           , countryCode: Option[String]){
  def this(lang:String, currencyCode:String, countryCode: String) = this(None,None,None,None,None,None,None,None,None,None,None,None,None,Some(false),lang,None,None)
}

case class ProductDetailsRequest(
                                  historize: Boolean// = false
                                  , visitorId: Option[Long]
                                  , currency: Option[String]
                                  , country: Option[String]
                                  , lang: String)

case class ProductDatesRequest(date:Option[String])

case class ProductTimesRequest(date: Option[String])

class DatePeriod(val startDate:Date,val endDate:Date)
case class EndPeriod(start:Date,end:Date) extends DatePeriod(start,end)
case class IntraDayPeriod(override val startDate:Date,override val endDate:Date,
                           val weekday1:Boolean,
                           val weekday2:Boolean,
                           val weekday3:Boolean,
                           val weekday4:Boolean,
                           val weekday5:Boolean,
                           val weekday6:Boolean,
                           val weekday7:Boolean
                           ) extends DatePeriod(startDate,endDate)


case class VisitorHistoryRequest(currency: Option[String], country: Option[String], lang: String)


