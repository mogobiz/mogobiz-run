package com.mogobiz.cart

import org.specs2.mutable.Specification
import scalikejdbc.config.DBs

/**
 *
 * Created by Christophe on 09/05/2014.
 */
class ProductBoServiceSpec  extends Specification {

  DBs.setupAll()

  val service = ProductBoService

  "increment stock by 1" in {

    val ttid = 63
    val ticketType = TicketType.get(ttid)
    //id:Long,product:Product,stock:Option[Stock],name:String,price:Long
    ticketType.id must be_==(ttid)
    //ticketType.stock.get.stock must be_==(100)

    val quantity = 1
    val date = None
    service.increment(ticketType , quantity, date)

    val ticketType2 = TicketType.get(ttid)
    ticketType2.id must be_==(ttid)
    //ticketType2.stock.get.stock must be_==(101)
  }

  "decrement stock by 1" in {

    val ttid = 72
    val ticketType = TicketType.get(ttid)
    //id:Long,product:Product,stock:Option[Stock],name:String,price:Long
    ticketType.id must be_==(ttid)
    //ticketType.stock.get.stock must be_==(100)

    val quantity = 1
    val date = None
    service.decrement(ticketType , quantity, date)

    val ticketType2 = TicketType.get(ttid)
    ticketType2.id must be_==(ttid)
    //ticketType2.stock.get.stock must be_==(101)
  }
}
