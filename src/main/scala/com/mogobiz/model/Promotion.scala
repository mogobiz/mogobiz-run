package com.mogobiz.model

import com.mogobiz.vo.PagingParams

/**
 *
 * Created by smanciot on 20/09/14.
 */
object Promotion {

  case class PromotionRequest(override val maxItemPerPage: Option[Int]
                              , override val  pageOffset: Option[Int]
                              , orderBy:Option[String]
                              , orderDirection: Option[String]
                              , categoryPath: Option[String]
                              , lang:String)  extends PagingParams
}
