package com.mogobiz.model

import java.util.Date

import com.mogobiz.vo.PagingParams

/**
 *
 * Created by Christophe on 17/02/14.
 */
/*
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
*/
//class LangParam(lang:String)

class CommonProductParameters(val lang:String,val currency:Option[String],val country:Option[String])

//case class CartParameters(val currency:Option[String],val country:Option[String],val lang:String)

case class CartParameters(_currency:Option[String],_country:Option[String],_lang:String) extends CommonProductParameters(_lang,_currency,_country)

case class CouponParameters(_currency:Option[String],_country:Option[String],_lang:String) extends CommonProductParameters(_lang,_currency,_country)

case class PrepareTransactionParameters(_currency:Option[String],_country:Option[String],state:Option[String],_lang:String, buyer:String) extends CommonProductParameters(_lang,_currency,_country)

case class CommitTransactionParameters(transactionUuid:String)

case class CancelTransactionParameters(_currency:Option[String],_country:Option[String],_lang:String) extends CommonProductParameters(_lang,_currency,_country)

case class FullTextSearchProductParameters(
                                            _lang: String
                                            , _currency: Option[String]
                                            , _country: Option[String]
                                            , query: String
                                            , highlight: Boolean
                                            , categoryPath: Option[String]) extends CommonProductParameters(_lang, _currency, _country)

case class CompareProductParameters(
                                     _lang: String
                                     , _currency: Option[String]
                                     , _country: Option[String]
                                     , ids: String) extends CommonProductParameters(_lang, _currency, _country)
case class FeatureValue(value: String)
case class Feature(indicator: String, label: String, values: List[FeatureValue])
case class ComparisonResult(ids: List[String],result:List[Feature])

case class FacetRequest(
                           priceInterval:Long
                         , lang: String
                         , name: Option[String]
                         //, brandId: Option[Int]
                         //, categoryPath: Option[String]
                         , brandName: Option[String]
                         , categoryName: Option[String]
                         , priceMin: Option[Long]
                         , priceMax: Option[Long]
                         , feature : Option[String]
                         ) {
 // def this(priceInterval: Long, lang:String) = this(priceInterval, lang, None, None,None,None,None)
}

case class ProductRequest(
                             override val maxItemPerPage: Option[Int]
                           , override val pageOffset: Option[Int]
                           , xtype: Option[String]
                           , name: Option[String]
                           , code: Option[String]
                           , categoryPath: Option[String]
                           , brandId: Option[Int]
                           , tagName: Option[String]
                           , priceMin: Option[Long]
                           , priceMax: Option[Long]
                           , creationDateMin: Option[String]
                           , featured: Option[Boolean]
                           , orderBy: Option[String]
                           , orderDirection: Option[String]
                           , lang: String
                           , currencyCode: Option[String]
                           , countryCode: Option[String]
                           , promotionId: Option[String]
                           , property : Option[String]
                           , feature : Option[String]) extends PagingParams {
  def this(lang:String, currencyCode:String, countryCode: String) = this(None,None,None,None,None,None,None,None,None,None,None,Some(false),None,None,lang,None,None, None, None, None)
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
                          weekday1:Boolean,
                          weekday2:Boolean,
                          weekday3:Boolean,
                          weekday4:Boolean,
                          weekday5:Boolean,
                          weekday6:Boolean,
                          weekday7:Boolean
                           ) extends DatePeriod(startDate,endDate)


case class VisitorHistoryRequest(currency: Option[String], country: Option[String], lang: String)

/*
case class Comment(val id:Option[String], val userId:String,val surname:String,val notation:Int,val subject:String,val comment:String, val created:Date, val productId:Long,val useful:Int=0,val notuseful:Int=0)
case class CommentRequest(val userId:String, val surname: String,val notation:Int, val subject:String,val comment:String, val created:Date = new Date)
*/
