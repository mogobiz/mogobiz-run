package com.mogobiz.cart

import java.util.Calendar
import com.mogobiz.cart.domain._
import scalikejdbc._
import org.joda.time.DateTime

/**
 *
 * Created by Christophe on 06/05/2014.
 */
object ProductBoService extends BoService {

  /**
   * Increment the stock of the ticketType of the ticket for the given date and decrement the number of sale
   */
  def increment(ticketType:TicketType , quantity:Long, date:Option[DateTime]):Unit = {
    val product = ticketType.product.get
    ticketType.stock match {
      case Some(stock) =>
        // Search the corresponding StockCalendar
        val stockCalendar = retrieveStockCalendar(product, ticketType, date, stock)

        // sale decrement
        val now = new DateTime()
        DB localTx { implicit session =>
          updateProductSales(product.id, -quantity, now)
          updateTicketTypeSales(ticketType.id, -quantity, now)
          updateStockCalendarSales(stockCalendar.id, -quantity, now)
        }
        //TODO ES upsertProduct(product)
      case None => throw new UnavailableStockException("increment stock error : stock does not exist")
    }
  }
  /*
    private def updateSales(quantity:Long,now:DateTime,ids:Tuple3[Long]) = {
      DB localTx {
        implicit session =>
          val nbSales1 = sql"select nb_sales from product where id=${ids._1}".map(rs => rs.long("nb_sales")).single().apply().get
          sql"update product set nb_sales = ${nbSales1+quantity},lastUpdated = ${now} where id=${ids._1}".update().apply()

          val nbSales2 = sql"select nb_sales from ticket_type where id=${ids._2}".map(rs => rs.long("nb_sales")).single().apply().get
          sql"update ticket_type set nb_sales = ${nbSales2+quantity},lastUpdated = ${now}  where id=${ids._2}".update().apply()

          val sold = sql"select sold from stock_calendar where id=${ids._3}".map(rs => rs.long("sold")).single().apply().get
          sql"update stock_calendar  set sold = ${sold+quantity},lastUpdated = ${now}  where id=${ids._3}".update().apply()

      }
    }
    */

  private def updateProductSales(id:Long,quantity:Long,now:DateTime)(implicit session:DBSession) = {
        val nbSales = sql"select nb_sales from product where id=$id".map(rs => rs.long("nb_sales")).single().apply().get
        sql"update product set nb_sales = ${nbSales+quantity},last_updated = $now where id=$id".update().apply()
  }

  private def updateTicketTypeSales(id:Long,quantity:Long,now:DateTime)(implicit session:DBSession) = {
      val nbSales = sql"select nb_sales from ticket_type where id=$id".map(rs => rs.long("nb_sales")).single().apply().get
      sql"update ticket_type set nb_sales = ${nbSales+quantity},last_updated = $now where id=$id".update().apply()
  }


  private def updateStockCalendarSales(id:Long,quantity:Long,now:DateTime)(implicit session:DBSession) = {
      val sold = sql"select sold from stock_calendar where id=$id".map(rs => rs.long("sold")).single().apply().get
      sql"update stock_calendar  set sold = ${sold+quantity},last_updated = $now where id=$id".update().apply()
  }


  /**
   * Decrement the stock of the ticketType of the ticket for the given date and increment the number of sale
   * If stock is insufficient, Exception is thrown
   * @param ticketType
   * @param quantity
   * @param date
   * @throws InsufficientStockException
   */
  def decrement(ticketType: TicketType, quantity: Long , date:Option[DateTime] ):Unit = {
    val product = ticketType.product.get
    ticketType.stock match {
      case Some(stock) =>
        // Search the corresponding StockCalendar
        val stockCalendar = retrieveStockCalendar(product, ticketType, date, stock)

        // stock vérification
        if (!stock.stockUnlimited && !stock.stockOutSelling && stockCalendar.stock < (quantity + stockCalendar.sold)) {
          throw new InsufficientStockException("The available stock is insufficient for the quantity required")
        }

        // sale increment
        val now = new DateTime()
        DB localTx {
          implicit session =>
            updateProductSales(product.id, quantity, now)
            updateTicketTypeSales(ticketType.id, quantity, now)
            updateStockCalendarSales(stockCalendar.id, quantity, now)
        }

        //TODO update ES
        //upsertProduct(product)
      case None => throw new UnavailableStockException("decrement stock error : stock does not exist")
    }
  }

  def conv(dt: DateTime):Calendar = {
    val cal = Calendar.getInstance()
    cal.setTime(dt.toDate)
    cal
  }


  /**
   * retrieve the StockCalendar of the TicketType for the given date.
   * If the StockCalendar does not exist, it is be created
   * @param product
   * @param ticketType
   * @param date
   * @return
   */
  //private
  def retrieveStockCalendarOld(product:Product , ticketType:TicketType , date:Option[DateTime] , stock:Stock ) : StockCalendar ={

    val stockCal = DB readOnly {
      implicit session =>
        val str = product.calendarType match {
          case ProductCalendar.NO_DATE => sql"select * from stock_calendar where ticket_type_fk=${ticketType.id}"
          case _ =>
            sql"select * from stock_calendar where ticket_type_fk=${ticketType.id} and start_date=$date"
        }

        val cal:Calendar = Calendar.getInstance()
        str.map(rs => new StockCalendar(rs.long("id"), rs.long("stock"), rs.long("sold"), rs.get("start_date"), product,ticketType, rs.get("date_created"),rs.get("last_updated"))).single().apply()
    }


    stockCal.getOrElse{
      val now = new DateTime()

      DB localTx { implicit session =>
        val newid = newId()
        val defaultStockCal = StockCalendar(newid, stock.stock, 0, date, product, ticketType, now, now)

        StockCalendar.insert(defaultStockCal).update().apply() //TODO find a way to include .update().apply() into the insert method
        defaultStockCal
      }
    }
  }

  def retrieveStockCalendar(product:Product , ticketType:TicketType , date:Option[DateTime] , stock:Stock ) : StockCalendar ={

    DB localTx { implicit session =>
      val str = product.calendarType match {
        case ProductCalendar.NO_DATE => sql"select * from stock_calendar where ticket_type_fk=${ticketType.id}"
          case _ =>
            sql"select * from stock_calendar where ticket_type_fk=${ticketType.id} and start_date=$date"
      }

      val cal:Calendar = Calendar.getInstance()
      val stockCal = str.map(rs => new StockCalendar(rs.long("id"), rs.long("stock"), rs.long("sold"), rs.get("start_date"), product,ticketType, rs.get("date_created"),rs.get("last_updated"))).single().apply()

      stockCal match {
        case Some(s) => s
        case None =>
          val now = new DateTime()
          val newid = newId()
          val defaultStockCal = StockCalendar(newid, stock.stock, 0, date, product, ticketType, now, now)

          StockCalendar.insert(defaultStockCal).update().apply() //TODO find a way to include .update().apply() into the insert method
          defaultStockCal
        }
      }
    }

}

class InsufficientStockException(message: String = null, cause: Throwable = null) extends java.lang.Exception
class UnavailableStockException(message: String = null, cause: Throwable = null) extends java.lang.Exception
