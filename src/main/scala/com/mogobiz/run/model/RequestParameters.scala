package com.mogobiz.run.model

import com.mogobiz.run.utils.PagingParams

object RequestParameters {

  case class Prefs(productsNumber: Integer)

  case class PromotionRequest(  override val maxItemPerPage: Option[Int]
                              , override val pageOffset: Option[Int]
                              , orderBy:Option[String]
                              , orderDirection: Option[String]
                              , categoryPath: Option[String]
                              , lang:String)  extends PagingParams
}
