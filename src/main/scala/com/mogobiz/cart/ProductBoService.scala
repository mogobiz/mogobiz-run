package com.mogobiz.cart

import java.util.Calendar
import com.mogobiz.cart.ProductType.ProductType
import com.mogobiz.cart.ProductCalendar.ProductCalendar
import scalikejdbc._, SQLInterpolation._
import org.joda.time.DateTime

/**
 * Created by Christophe on 06/05/2014.
 */
object ProductBoService extends BoService {

  /**
   * Increment the stock of the ticketType of the ticket for the given date and decrement the number of sale
   */
  def increment(ticketType:TicketType , quantity:Long, date:Option[DateTime]):Unit = {
    val product = ticketType.product.get
    ticketType.stock match {
      case Some(stock) => {
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
      }
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
        val nbSales = sql"select nb_sales from product where id=${id}".map(rs => rs.long("nb_sales")).single().apply().get
        sql"update product set nb_sales = ${nbSales+quantity},last_updated = ${now} where id=${id}".update().apply()
  }

  private def updateTicketTypeSales(id:Long,quantity:Long,now:DateTime)(implicit session:DBSession) = {
      val nbSales = sql"select nb_sales from ticket_type where id=${id}".map(rs => rs.long("nb_sales")).single().apply().get
      sql"update ticket_type set nb_sales = ${nbSales+quantity},last_updated = ${now}  where id=${id}".update().apply()
  }


  private def updateStockCalendarSales(id:Long,quantity:Long,now:DateTime)(implicit session:DBSession) = {
      val sold = sql"select sold from stock_calendar where id=${id}".map(rs => rs.long("sold")).single().apply().get
      sql"update stock_calendar  set sold = ${sold+quantity},last_updated = ${now}  where id=${id}".update().apply()
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
      case Some(stock) => {
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
    }
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
  def retrieveStockCalendar(product:Product , ticketType:TicketType , date:Option[DateTime] , stock:Stock ) : StockCalendar ={

    val stockCal = DB readOnly {
      implicit session =>
        val str = product.calendarType match {
          case ProductCalendar.NO_DATE => sql"select * from stock_calendar where ticket_type_fk=${ticketType.id}"
          case _ =>{
            sql"select * from stock_calendar where ticket_type_fk=${ticketType.id} and start_date=${date}"
          }
        }

        val cal:Calendar = Calendar.getInstance()
        str.map(rs => new StockCalendar(rs.long("id"), rs.long("stock"), rs.long("sold"), rs.dateTimeOpt("start_date"), product,ticketType, rs.dateTime("date_created"),rs.dateTime("last_updated"))).single().apply()
    }


    stockCal.getOrElse{
      val now = new DateTime()

      DB localTx { implicit session =>
        val newid = newId()
        val defaultStockCal = new StockCalendar(newid, stock.stock, 0, date, product, ticketType, now, now)

        sql"""insert into stock_calendar(id, date_created, last_updated, product_fk, sold, start_date, stock, ticket_type_fk)
           values (${newid},${now},${now},${defaultStockCal.product.id},${defaultStockCal.sold},${date},${defaultStockCal.stock},${defaultStockCal.ticketType.id})""".
          update().apply()
        defaultStockCal
      }
    }
  }

}

class InsufficientStockException(message: String = null, cause: Throwable = null) extends java.lang.Exception
class UnavailableStockException(message: String = null, cause: Throwable = null) extends java.lang.Exception

case class Stock(stock:Long=0,stockUnlimited:Boolean = true,stockOutSelling:Boolean = false)
object Stock  extends SQLSyntaxSupport[Stock]{
  def apply(rs: WrappedResultSet):Stock = new Stock(rs.long("stock_stock"),rs.boolean("stock_stock_unlimited"),rs.boolean("stock_stock_out_selling"))
//  def apply(rs: WrappedResultSet):Stock = new Stock(rs.long("ss_on_t"),rs.boolean("ssu_on_t"),rs.boolean("ssos_on_t"))
  // t.stock_stock as ss_on_t, t.stock_stock_out_selling as ssos_on_t, t.stock_stock_unlimited as ssu_on_t,

  //def apply(rn: ResultName[TicketType])(rs:WrappedResultSet): Stock = new Stock(stock=rs.get(rn.stock),stockUnlimited=rs.get(rn.stockUnlimited),stockOutSelling=rs.get(rn.stockOutSelling))
}

case class StockCalendar(
                          id:Long,stock:Long,sold:Long,startDate:Option[DateTime],product:Product,ticketType:TicketType,
                          dateCreated:DateTime,lastUpdated:DateTime) extends DateAware

case class Poi(road1:Option[String],road2:Option[String],city:Option[String],postalCode:Option[String],state:Option[String],countryCode:Option[String])

case class Product(id:Long,name:String,xtype:ProductType, calendarType:ProductCalendar,taxRate:Option[TaxRate],shippingFk:Option[Long],
                   startDate:Option[DateTime],stopDate:Option[DateTime] ){

  def shipping = shippingFk match {
    case Some(shippingId) => Product.getShipping(shippingId)
    case None => None
  }
}

object Product extends SQLSyntaxSupport[Product]{
  def apply(rn: ResultName[Product])(rs:WrappedResultSet): Product = Product(
    rs.get(rn.id),
    rs.get(rn.name),
    ProductType.valueOf(rs.string("xtype")),//rs.get(rn.xtype),
    ProductCalendar.valueOf(rs.string("calendar_type")), //rs.get(rn.calendarType),
    Some(TaxRate(rs)), //rs.get(rn.taxRate),
    rs.get(rn.shippingFk),
    rs.get(rn.startDate),
    rs.get(rn.stopDate))
  def apply(rs:WrappedResultSet): Product = Product(id = rs.long("id"),name = rs.string("name"),xtype = ProductType.valueOf(rs.string("xtype")),calendarType = ProductCalendar.valueOf(rs.string("calendar_type")),taxRate = Some(TaxRate(rs)),shippingFk= rs.longOpt("shipping_fk"),startDate=rs.dateTimeOpt("start_date"),stopDate=rs.dateTimeOpt("stop_date"))
  def applyFk(rs:WrappedResultSet): Product = Product(id = rs.long("product_fk"),name = rs.string("name"),xtype = ProductType.valueOf(rs.string("xtype")),calendarType = ProductCalendar.valueOf(rs.string("calendar_type")),taxRate = Some(TaxRate(rs)),shippingFk= rs.longOpt("shipping_fk"),startDate=rs.dateTimeOpt("start_date"),stopDate=rs.dateTimeOpt("stop_date"))

  def get(id:Long):Option[Product] = {
    val p = Product.syntax("p")
    val res = DB readOnly { implicit session =>
      withSQL {
        select.from(Product as p).where.eq(p.id, id)
      }.map(Product(p.resultName)).single().apply()
    }
    res
  }

  /*

  case class ShippingVO
(weight: Long, weightUnit: WeightUnit, width: Long, height: Long, depth: Long, linearUnit: LinearUnit, amount: Long, free: Boolean)

   */
  def getShipping(id:Long):Option[ShippingVO] = {
    DB readOnly { implicit session =>
        sql"select * from shipping where id=${id}".
          map(rs => ShippingVO(id = rs.long("id"),weight = rs.long("weight"), weightUnit = WeightUnit(rs.string("weight_unit")),linearUnit = LinearUnit(rs.string("linear_unit")),width = rs.long("width"), height = rs.long("height"), depth = rs.long("depth"), amount = rs.long("amount"), free = rs.boolean("free") )).single().apply()
    }
  }
}

import scalikejdbc._, SQLInterpolation._
case class TaxRate(id:Long,name:String,company_fk:Long)
object TaxRate extends SQLSyntaxSupport[TaxRate] {
  def apply(tr: ResultName[TaxRate])(rs:WrappedResultSet) : TaxRate = new TaxRate(id=rs.get(tr.id),name=rs.get(tr.name),company_fk = rs.get(tr.company_fk))
  def apply(rs:WrappedResultSet) : TaxRate = new TaxRate(id=rs.long("tax_rate_fk"),name=rs.string("name"),company_fk = rs.long("company_fk"))
}

case class TicketType(id:Long,name:String,price:Long,minOrder:Long=0,maxOrder:Long=0,stock:Option[Stock]=None,product:Option[Product]=None,startDate:Option[DateTime]=None,stopDate:Option[DateTime]=None){

}
object TicketType extends SQLSyntaxSupport[TicketType] {

  def apply(rs:WrappedResultSet):TicketType = TicketType(
    id=rs.long("id"),
    name=rs.string("name"),
    price=rs.long("price"),minOrder=rs.long("min_order"),maxOrder=rs.long("max_order"),
    stock=Some(Stock(rs)),
    product = Some(Product.applyFk(rs)))

  def apply(rn: ResultName[TicketType])(rs:WrappedResultSet): TicketType = new TicketType(
    id=rs.get(rn.id),name=rs.get(rn.name),price=rs.get(rn.price),
    minOrder=rs.get(rn.minOrder),maxOrder=rs.get(rn.maxOrder),
    stock=Some(Stock(rs)),product=Some(Product.applyFk(rs)))

  def get(id:Long):TicketType = {

    val res = DB readOnly {
      implicit session =>
        sql"select tt.*,p.* from ticket_type tt inner join product p on tt.product_fk=p.id where tt.id=${id}".map( rs => TicketType(rs)).single().apply()
    }

    /* standalone with product missing
    val t = TicketType.syntax("t")
    val res = DB readOnly {
      implicit session =>
        withSQL {
          select.from(TicketType as t).where.eq(t.id, id)
        }.map(TicketType(t.resultName)).single().apply()
    }*/
      /* try oneToOne
    val (t,p) = (TicketType.syntax,Product.syntax)
    val res = DB readOnly { implicit session =>

      withSQL{
        select.from(TicketType as t).innerJoin(Product as p).on(t.productId,p.id)
          .where.eq(t.id,id)
      }.one(TicketType(t.resultName)).toOne(Product(p.resultName))
        .map{
        (ticketType,product) => product.copy(product = Some(product))
      }.single().apply()
    }*/
    res.get
  }
}
trait DateAware {
  val dateCreated:DateTime
  val lastUpdated:DateTime
}