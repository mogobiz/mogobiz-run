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


case class ProductRequest(
                           maxItemPerPage: Option[Int]
                           , pageOffset: Option[Int]
                           , xtype: Option[String]
                           , name: Option[String]
                           , code: Option[String]
                           , categoryId: Option[Int]
                           , brandId: Option[Int]
                           , tagName: Option[String]
                           , priceMin: Option[Long]
                           , priceMax: Option[Long]
                           , creationDateMin: Option[String]
                           , orderBy: Option[String]
                           , orderDirection: Option[String]
                           , lang: String
//                           , storeCode: String
                           , currencyCode: String
                           , countryCode: String)

case class ProductDetailsRequest(
                                  historize: Boolean = false
                                  //, productId: Int
                                  , visitorId: Option[Long]
                                  //, storeCode: String
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

