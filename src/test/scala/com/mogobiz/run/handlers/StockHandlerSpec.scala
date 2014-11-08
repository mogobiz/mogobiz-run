package com.mogobiz.run.handlers

import com.mogobiz.run.cart.ProductBoService
import com.mogobiz.run.cart.domain.{StockCalendar, TicketType}
import com.mogobiz.run.config.MogobizDBsWithEnv
import org.joda.time.DateTime
import org.specs2.mutable.Specification
import scalikejdbc.config.DBs


class StockHandlerSpec extends Specification{

  val stockHandler = new StockHandler

  val storeCode = "mogobiz"


  MogobizDBsWithEnv("test").setupAll()

  "update method" should {
    "find the stock calendar and update the stock" in {

      val ttid = 146 //les tonton flingueurs
      val ticketType = TicketType.get(ttid)
      val product = ticketType.product.get
      //
      val date = DateTime.now.withHourOfDay(15).withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0)
      val stockCal = ProductBoService.retrieveStockCalendar(product, ticketType, Some(date), ticketType.stock.get)

      val res = stockHandler.update(storeCode, ticketType, stockCal.copy(sold=5))

      res must_==true

    }
  }
}
