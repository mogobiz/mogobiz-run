package com.mogobiz.cart

import java.util.Calendar
import akka.actor.Props
import com.mogobiz.actors.EsUpdateActor.{SalesUpdateRequest, StockCalendarUpdateRequest, StockUpdateRequest}
import com.mogobiz.actors.{EsUpdateActor, CartActor, ActorSystemLocator}
import com.mogobiz.cart.domain._
import com.mogobiz.handlers.StockHandler
import com.mogobiz.utils.GlobalUtil._
import scalikejdbc._
import org.joda.time.DateTime

object ProductBoService extends BoService {

  /**
   * Increment the stock of the ticketType of the ticket for the given date and decrement the number of sale
   */
  def incrementStock(ticketType:TicketType , quantity:Long, date:Option[DateTime]):Unit = {
    val product = ticketType.product.get
    val storeCode = product.company.get.code
    ticketType.stock match {
      case Some(stock) =>
        // Search the corresponding StockCalendar
        val stockCalendar = retrieveStockCalendar(product, ticketType, date, stock)

        // stock increment
        val now = new DateTime()
        DB localTx { implicit session =>
//          updateProductSales(product.id, -quantity, now)
//          updateTicketTypeSales(ticketType.id, -quantity, now)
          val newSold = updateStockCalendarSales(stockCalendar.id, -quantity, now)

            product.calendarType match {
              case ProductCalendar.NO_DATE => updateEsStock(storeCode, ticketType.uuid, newSold)
              case _ => updateEsStockByDateTime(storeCode, ticketType, stockCalendar.copy(sold = newSold))

            }
        }

      case None => throw new UnavailableStockException("increment stock error : stock does not exist")
    }
  }

  /**
   * Decrement the stock of the ticketType of the ticket for the given date and increment the number of sale
   * If stock is insufficient, Exception is thrown
   * @param ticketType
   * @param quantity
   * @param date
   * @throws InsufficientStockException
   */
  def decrementStock(ticketType: TicketType, quantity: Long , date:Option[DateTime] ):Unit = {
    val product = ticketType.product.get
    val storeCode = product.company.get.code

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
//            updateProductSales(product.id, quantity, now)
//            updateTicketTypeSales(ticketType.id, quantity, now)
            val newSold = updateStockCalendarSales(stockCalendar.id, quantity, now)

            product.calendarType match {
              case ProductCalendar.NO_DATE => updateEsStock(storeCode, ticketType.uuid, newSold)
              case _ => updateEsStockByDateTime(storeCode, ticketType, stockCalendar.copy(sold = newSold))

            }
        }
      case None => throw new UnavailableStockException("decrement stock error : stock does not exist")
    }
  }

  /**
   * Retrieve the stock information, if none, create one from ticketType info
   * @param product
   * @param ticketType
   * @param date
   * @param stock
   * @return
   */
  def retrieveStockCalendar(product:Product , ticketType:TicketType , date:Option[DateTime] , stock:Stock ) : StockCalendar = {

    DB localTx { implicit session =>
      val str = product.calendarType match {
        case ProductCalendar.NO_DATE => sql"select * from stock_calendar where ticket_type_fk=${ticketType.id}"
          case _ =>
            sql"select * from stock_calendar where ticket_type_fk=${ticketType.id} and start_date=$date"
      }

      val stockCal = str.map(rs => new StockCalendar(rs.long("id"), rs.string("uuid"), rs.long("stock"), rs.long("sold"), rs.get("start_date"), product,ticketType, rs.get("date_created"),rs.get("last_updated"))).single().apply()

      stockCal match {
        case Some(s) => s
        case None =>
          val now = new DateTime()
          val newid = newId()
          val defaultStockCal = StockCalendar(newid, newUUID, stock.stock, 0, date, product, ticketType, now, now)

          StockCalendar.insert(defaultStockCal).update().apply() //TODO find a way to include .update().apply() into the insert method
          defaultStockCal
        }
      }
    }

  /**
   * Increment product & ticketType's number of sale
   * @param ticketType
   * @param quantity
   */
  def incrementSales(ticketType: TicketType, quantity: Long):Unit = {

    val product = ticketType.product.get
    val productId = product.id
    val ticketTypeId = ticketType.id

    val storeCode = product.company.get.code

    val now = new DateTime()
    DB localTx {
      implicit session =>
        val psales = updateProductSales(productId, quantity, now)
        val ssales = updateTicketTypeSales(ticketTypeId, quantity, now)

        updateSales(storeCode, productId, psales, ticketTypeId, ssales)

    }
  }

  def updateEsStockByDateTime(storeCode: String,  ticketType: TicketType, stockCalendar: StockCalendar) = {
    val system = ActorSystemLocator.get
    val stockActor = system.actorOf(Props[EsUpdateActor])
    stockActor ! StockCalendarUpdateRequest(storeCode, ticketType, stockCalendar)
  }

  def updateEsStock(storeCode: String, uuid:String, sold:Long) = {
    val system = ActorSystemLocator.get
    val stockActor = system.actorOf(Props[EsUpdateActor])
    stockActor ! StockUpdateRequest(storeCode, uuid, sold)
  }

  def updateSales(storeCode: String, productId: Long, nbSalesProduct : Long, idSku: Long, nbSalesSku: Long) = {
    val system = ActorSystemLocator.get
    val stockActor = system.actorOf(Props[EsUpdateActor])
    stockActor ! SalesUpdateRequest(storeCode, productId, nbSalesProduct, idSku, nbSalesSku)
  }

  private def updateProductSales(id:Long,quantity:Long,now:DateTime)(implicit session:DBSession) : Long = {
    val nbSales = sql"select nb_sales from product where id=$id".map(rs => rs.long("nb_sales")).single().apply().get
    val newNbSales = nbSales+quantity
    sql"update product set nb_sales = ${newNbSales},last_updated = $now where id=$id".update().apply()
    newNbSales
  }

  private def updateTicketTypeSales(id:Long,quantity:Long,now:DateTime)(implicit session:DBSession) : Long = {
    val nbSales = sql"select nb_sales from ticket_type where id=$id".map(rs => rs.long("nb_sales")).single().apply().get
    val newNbSales = nbSales+quantity
    sql"update ticket_type set nb_sales = ${newNbSales},last_updated = $now where id=$id".update().apply()
    newNbSales
  }


  private def updateStockCalendarSales(id:Long,quantity:Long,now:DateTime)(implicit session:DBSession) : Long = {
    val sold = sql"select sold from stock_calendar where id=$id".map(rs => rs.long("sold")).single().apply().get
    val newSold = sold+quantity
    sql"update stock_calendar  set sold = ${newSold},last_updated = $now where id=$id".update().apply()
    newSold
  }


  private def conv(dt: DateTime):Calendar = {
    val cal = Calendar.getInstance()
    cal.setTime(dt.toDate)
    cal
  }

}

class InsufficientStockException(message: String = null, cause: Throwable = null) extends java.lang.Exception
class UnavailableStockException(message: String = null, cause: Throwable = null) extends java.lang.Exception
