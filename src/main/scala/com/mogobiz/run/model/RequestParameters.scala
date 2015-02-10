package com.mogobiz.run.model

import java.util.Date

import com.mogobiz.run.exceptions.CommentException
import com.mogobiz.run.utils.PagingParams

object RequestParameters {

  case class PromotionRequest(  override val maxItemPerPage: Option[Int]
                              , override val pageOffset: Option[Int]
                              , orderBy:Option[String]
                              , orderDirection: Option[String]
                              , categoryPath: Option[String]
                              , lang:String)  extends PagingParams

  case class FacetRequest(
                           priceInterval:Long
                           , xtype: Option[String]
                           , name: Option[String]
                           , code: Option[String]
                           , categoryPath: Option[String]
                           , brandId: Option[String]
                           , tags: Option[String]
                           , notations: Option[String]
                           , priceRange: Option[String]
                           , creationDateMin: Option[String]
                           , featured: Option[Boolean]
                           , lang: String
                           , promotionId: Option[String]
                           , hasPromotion : Option[Boolean]
                           , property : Option[String]
                           , features : Option[String]
                           , variations : Option[String]
                           , brandName: Option[String]
                           , categoryName: Option[String]
                           , multiCategory : Option[Boolean]
                           , multiBrand : Option[Boolean]
                           , multiTag : Option[Boolean]
                           , multiFeatures : Option[Boolean]
                           , multiVariations : Option[Boolean]
                           , multiNotation : Option[Boolean]
                           , multiPrices : Option[Boolean]) {
    def this(priceInterval: Long, lang:String) = this(priceInterval, None, None, None, None, None, None, None, None, None, None, lang, None, None, None, None, None, None, None, None, None, None, None, None, None, None)
  }

  //--- Products

  class CommonProductParameters(val lang:String,val currency:Option[String],val country:Option[String])


  case class ProductRequest(
                             override val maxItemPerPage: Option[Int]
                             , override val pageOffset: Option[Int]
                             , id: Option[String]
                             , xtype: Option[String]
                             , name: Option[String]
                             , code: Option[String]
                             , categoryPath: Option[String]
                             , brandId: Option[String]
                             , tagName: Option[String]
                             , notations: Option[String]
                             , priceRange: Option[String]
                             , creationDateMin: Option[String]
                             , featured: Option[Boolean]
                             , orderBy: Option[String]
                             , orderDirection: Option[String]
                             , lang: String
                             , currencyCode: Option[String]
                             , countryCode: Option[String]
                             , promotionId: Option[String]
                             , hasPromotion : Option[Boolean]
                             , property : Option[String]
                             , feature : Option[String]
                             , variations : Option[String]) extends PagingParams {
    def this(lang:String, currencyCode:String, countryCode: String) = this(None,None,None,None,None,None,None,None,None,None,None,None,Some(false),None,None,lang,None,None, None,Some(false), None, None, None)
  }

  case class ProductDetailsRequest(
                                    historize: Boolean
                                    , visitorId: Option[Long]
                                    , currency: Option[String]
                                    , country: Option[String]
                                    , lang: String)


  case class FullTextSearchProductParameters(
                                              _lang: String
                                              , _currency: Option[String]
                                              , _country: Option[String]
                                              , query: String
                                              , highlight: Boolean
                                              , size: Int
                                              , categoryPath: Option[String]) extends CommonProductParameters(_lang, _currency, _country)

  case class CompareProductParameters(
                                       _lang: String
                                       , _currency: Option[String]
                                       , _country: Option[String]
                                       , ids: String) extends CommonProductParameters(_lang, _currency, _country)

  //--- Comment
  case class CommentRequest( notation:Int, subject:String, comment:String, externalCode: Option[String], created:Date = new Date){
    def validate() {
      if(!(notation>=0 && notation<=5)) throw CommentException(CommentException.BAD_NOTATION)
    }
  }

  case class CommentGetRequest(override val maxItemPerPage:Option[Int],override val pageOffset:Option[Int] ) extends PagingParams

  case class CommentPutRequest(note:Int)


  //--- Cart

  case class CartParameters(_currency:Option[String],_country:Option[String],state: Option[String], _lang:String) extends CommonProductParameters(_lang,_currency,_country)

  case class CouponParameters(_currency:Option[String],_country:Option[String],state: Option[String],_lang:String) extends CommonProductParameters(_lang,_currency,_country)

  case class PrepareTransactionParameters(_currency:Option[String],_country:Option[String],state:Option[String],_lang:String, buyer:String) extends CommonProductParameters(_lang,_currency,_country)

  case class CommitTransactionParameters(country:Option[String], lang:String, transactionUuid:String)

  case class CancelTransactionParameters(_currency:Option[String],_country:Option[String],state: Option[String],_lang:String) extends CommonProductParameters(_lang,_currency,_country)


}
