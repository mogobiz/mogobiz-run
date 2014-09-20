package com.mogobiz.model

import com.mogobiz.vo.PagingParams

/**
 *
 * Created by smanciot on 20/09/14.
 */
case class Promotion(id:Long, name:String, description:String)

case class PromotionRequest(override val maxItemPerPage: Option[Int]
                            , override val  pageOffset: Option[Int]
                            , orderBy:Option[String]
                            , categoryPath: Option[String]
                            , lang:String)  extends PagingParams
