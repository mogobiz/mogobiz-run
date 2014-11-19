package com.mogobiz.run.model

import org.joda.time.DateTime

/**
 * Created by yoannbaudy on 19/11/2014.
 */
object Mogobiz {

  case class Sku(id:Long,
                 uuid:String,
                 sku:String,
                 name:String,
                 price:Long,
                 minOrder:Long=0,
                 maxOrder:Long=0,
                 availabilityDate:Option[DateTime]=None,
                 startDate:Option[DateTime]=None,
                 stopDate:Option[DateTime]=None)

}
