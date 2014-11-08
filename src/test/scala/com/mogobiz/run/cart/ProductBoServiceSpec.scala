package com.mogobiz.run.cart

import com.mogobiz.run.config.MogobizDBsWithEnv
import org.specs2.mutable.Specification
import scalikejdbc.config.DBsWithEnv
import com.mogobiz.run.cart.domain._


/**
 *
 * Created by Christophe on 09/05/2014.
 */
class ProductBoServiceSpec  extends Specification {

  MogobizDBsWithEnv("test").setupAll()

  val service = ProductBoService

  "increment stock by 1" in {

    val ttid = 63
    val ticketType = TicketType.get(ttid)
    //id:Long,product:Product,stock:Option[Stock],name:String,price:Long
    ticketType.id must be_==(ttid)
    //ticketType.stock.get.stock must be_==(100)

    val quantity = 1
    val date = None
    service.incrementStock(ticketType , quantity, date)

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
    service.decrementStock(ticketType , quantity, date)

    val ticketType2 = TicketType.get(ttid)
    ticketType2.id must be_==(ttid)
    //ticketType2.stock.get.stock must be_==(101)
  }
}
