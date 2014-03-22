package com.mogobiz

import spray.http.DateTime

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

class CommonProductParameters(val lang:String,val currencyCode:String,val countryCode:String)
//,lang:String,currencyCode:String,countryCode:String
case class FulltextSearchProductParameters(
                                            _lang:String,_currencyCode:String,_countryCode:String,
                                            val query:String) extends CommonProductParameters(_lang,_currencyCode,_countryCode)

case class ProductRequest(
                           maxItemPerPage: Option[Int]
                           , pageOffset: Option[Int]
                           , xtype: Option[String]
                           , var name: Option[String]
                           , code: Option[String]
                           , categoryId: Option[Int]
                           , brandId: Option[Int]
                            , path:Option[String]
                           , tagName: Option[String]
                           , priceMin: Option[Long]
                           , priceMax: Option[Long]
                           , orderBy: Option[String]
                           , orderDirection: Option[String]
                           , lang: String
//                           , storeCode: String
                           , currencyCode: String
                           , countryCode: String){
  def this(lang:String, currencyCode:String, countryCode: String) = this(None,None,None,None,None,None,None,None,None,None,None,None,None,lang,currencyCode,countryCode)
}

case class ProductDetailsRequest(
                                  historize: Boolean// = false
                                  , visitorId: Option[Long]
                                  , currencyCode: String
                                  , countryCode: String
                                  , lang: String)


case class AddToVisitorHistoryRequest(
                                       productId: Int
                                       , visitorId: Int
                                       , storeCode: String
                                       , currencyCode: String
                                       , countryCode: String
                                       , lang: String)


case class VisitorHistoryRequest(
                                  visitorId: Int
                                  , storeCode: String
                                  , currencyCode: String
                                  , countryCode: String
                                  , lang: String)


case class ProductDatesRequest(
                                productId: Int
                                , startDate: String
                                , endDate: String
                                , storeCode: String
                                , lang: String)


case class ProductTimesRequest(
                                productId: Int
                                , date: String
                                , storeCode: String
                                , lang: String)

