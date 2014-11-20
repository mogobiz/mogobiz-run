package com.mogobiz.run.model

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
                           , lang: String
                           , name: Option[String]
                           , brandId: Option[Long]
                           , categoryPath: Option[String]
                           , brandName: Option[String]
                           , categoryName: Option[String]
                           , tags: Option[String]
                           , notations: Option[String]
                           , priceMin: Option[Long]
                           , priceMax: Option[Long]
                           , features : Option[String]
                           , variations : Option[String]
                           ) {
    def this(priceInterval: Long, lang:String) = this(priceInterval, lang, None, None, None, None, None, None, None, None, None, None, None)
  }
}
