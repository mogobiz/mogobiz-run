package com.mogobiz.run.cart

import com.mogobiz.run.model.Mogobiz.ProductCalendar.ProductCalendar
import com.mogobiz.run.model.Mogobiz.ProductType.ProductType
import com.mogobiz.run.model.Mogobiz.{ProductCalendar, ProductType, LinearUnit, WeightUnit}
import org.joda.time.DateTime
import scalikejdbc._
/**
 *
 * Created by Christophe on 09/05/2014.
 */
package object domain {

  trait DateAware {
    val dateCreated:DateTime
    val lastUpdated:DateTime
  }

  trait Entity {
    val uuid : String  = java.util.UUID.randomUUID().toString
  }

  case class Stock(stock:Long=0,stockUnlimited:Boolean = true,stockOutSelling:Boolean = false)
  object Stock  extends SQLSyntaxSupport[Stock]{
    def apply(rs: WrappedResultSet):Stock = new Stock(rs.longOpt("stock_stock").getOrElse(0),rs.boolean("stock_stock_unlimited"),rs.boolean("stock_stock_out_selling"))
    //  def apply(rs: WrappedResultSet):Stock = new Stock(rs.long("ss_on_t"),rs.boolean("ssu_on_t"),rs.boolean("ssos_on_t"))
    // t.stock_stock as ss_on_t, t.stock_stock_out_selling as ssos_on_t, t.stock_stock_unlimited as ssu_on_t,

    //def apply(rn: ResultName[TicketType])(rs:WrappedResultSet): Stock = new Stock(stock=rs.get(rn.stock),stockUnlimited=rs.get(rn.stockUnlimited),stockOutSelling=rs.get(rn.stockOutSelling))
  }

  case class StockCalendar(
                            id:Long,
                            uuid:String,
                            stock:Long,
                            sold:Long,
                            startDate:Option[DateTime],
                            product:Product,
                            ticketType:TicketType,
                            dateCreated:DateTime,
                            lastUpdated:DateTime) extends DateAware //Entity with

  object StockCalendar {

    def insert(s:StockCalendar) = {
      sql"""insert into stock_calendar(id, date_created, last_updated, product_fk, sold, start_date, stock, ticket_type_fk, uuid)
           values (${s.id},${s.dateCreated},${s.lastUpdated},${s.product.id},${s.sold},${s.startDate},${s.stock},${s.ticketType.id}, ${s.uuid})"""
    }
  }
  case class Product(id:Long, uuid:String, name:String,xtype:ProductType, calendarType:ProductCalendar,taxRateFk:Option[Long], taxRate: Option[TaxRate],
                     poiFk:Option[Long],shippingFk:Option[Long],companyFk:Long, stockDisplay: Boolean,
                     startDate:Option[DateTime],stopDate:Option[DateTime] ){

    def company = Company.get(companyFk)
  }

  object Product extends SQLSyntaxSupport[Product]{

    def apply(p: SyntaxProvider[Product])(rs:WrappedResultSet) : Product = apply(p.resultName)(rs)

    def apply(rn: ResultName[Product])(rs:WrappedResultSet): Product = new Product(
      id = rs.get(rn.id),
      uuid = rs.get(rn.uuid),
      name = rs.get(rn.name),
      xtype = ProductType.valueOf(rs.string("x_on_p")),//,rs.get(rn.xtype), //FIXME
      calendarType = ProductCalendar.valueOf(rs.string("ct_on_p")), //rs.get(rn.calendarType), //FIXME
      taxRateFk = rs.get(rn.taxRateFk),
      taxRate = None, //Some(TaxRate(rs)),
      companyFk = rs.get(rn.companyFk),
      poiFk = rs.get(rn.poiFk),
      shippingFk = rs.get(rn.shippingFk),
      stockDisplay = rs.get(rn.stockDisplay),
      startDate = rs.get(rn.startDate),
      stopDate = rs.get(rn.stopDate)
    )


    def apply(rs:WrappedResultSet): Product = Product(id = rs.long("id"), uuid = rs.string("uuid"), name = rs.string("name"),xtype = ProductType.valueOf(rs.string("xtype")),calendarType = ProductCalendar.valueOf(rs.string("calendar_type")),taxRateFk = rs.longOpt("tax_rate_fk"),taxRate = Some(TaxRate(rs)),companyFk = rs.long("company_fk"),poiFk= rs.longOpt("poi_fk"),shippingFk= rs.longOpt("shipping_fk"),stockDisplay = rs.get("stock_display"),startDate=rs.get("start_date"),stopDate=rs.get("stop_date"))
    def applyFk(rs:WrappedResultSet): Product = Product(id = rs.long("product_fk"),uuid = rs.string("uuid"),name = rs.string("name"),xtype = ProductType.valueOf(rs.string("xtype")),calendarType = ProductCalendar.valueOf(rs.string("calendar_type")),taxRateFk = rs.longOpt("tax_rate_fk"),taxRate = Some(TaxRate(rs)),companyFk = rs.long("company_fk"),poiFk= rs.longOpt("poi_fk"), shippingFk= rs.longOpt("shipping_fk"),stockDisplay = rs.get("stock_display"),startDate=rs.get("start_date"),stopDate=rs.get("stop_date"))

    def get(id:Long):Option[Product] = {

      val (p,tr) = (Product.syntax("p"), TaxRate.syntax("tr"))

      val res = DB readOnly { implicit session =>
        withSQL {
          select.from(Product as p).leftJoin(TaxRate as tr).on(p.taxRateFk, tr.id).where.eq(p.id, id)
        }
          .one(Product(p))
          .toOptionalOne(TaxRate.opt(tr))
          .map{
          (product, taxrate) => product.copy(taxRate = Some(taxrate))
        }
          /*.map { (product, taxrate) => taxrate match {
          case Some(txr) => product.copy(taxRate = txr)
          case None => product
        }}*/
          .single.apply()
        //.map(Product(p.resultName)).single().apply()
      }
      res
    }
  }

  case class TaxRate(id:Long,name:String,company_fk:Long)
  object TaxRate extends SQLSyntaxSupport[TaxRate] {
    def apply(p: SyntaxProvider[TaxRate])(rs:WrappedResultSet) : TaxRate = apply(p.resultName)(rs)
    def apply(tr: ResultName[TaxRate])(rs:WrappedResultSet) : TaxRate = new TaxRate(id=rs.get(tr.id),name=rs.get(tr.name),company_fk = rs.get(tr.company_fk))
    def apply(rs:WrappedResultSet) : TaxRate = new TaxRate(id=rs.long("tax_rate_fk"),name=rs.string("name"),company_fk = rs.long("company_fk"))

    def opt(tr: SyntaxProvider[TaxRate])(rs: WrappedResultSet): Option[TaxRate] =
      rs.longOpt(tr.resultName.id).map(_ => TaxRate(tr)(rs))
  }

  case class TicketType(
                         uuid:String,
                         sku:String,
                         id:Long,
                         name:String,
                         price:Long,
                         minOrder:Long=0,
                         maxOrder:Long=0,
                         stock:Option[Stock]=None,
                         product:Option[Product]=None,
                         availabilityDate:Option[DateTime]=None,
                         startDate:Option[DateTime]=None,
                         stopDate:Option[DateTime]=None){

  }

  object TicketType extends SQLSyntaxSupport[TicketType] {

    def apply(rs:WrappedResultSet):TicketType = TicketType(
      uuid = rs.string("uuid"),
      sku = rs.string("sku"),
      id=rs.long("id"),
      name=rs.string("name"),
      price=rs.long("price"),minOrder=rs.long("min_order"),maxOrder=rs.long("max_order"),
      stock=Some(Stock(rs)),
      availabilityDate = rs.get("availability_date"),
      startDate = rs.get("start_date"),
      stopDate = rs.get("stop_date"),
      product = Some(Product.applyFk(rs)))

    def apply(rn: ResultName[TicketType])(rs:WrappedResultSet): TicketType = new TicketType(
      uuid=rs.get(rn.uuid), sku = rs.get(rn.sku), availabilityDate = rs.get(rn.availabilityDate),
      id=rs.get(rn.id),name=rs.get(rn.name),price=rs.get(rn.price),
      minOrder=rs.get(rn.minOrder),maxOrder=rs.get(rn.maxOrder),
      stock=Some(Stock(rs)),product=Some(Product.applyFk(rs)))

    def get(id:Long):TicketType = {

      val res = DB readOnly {
        implicit session =>
          sql"select tt.*,p.* from ticket_type tt inner join product p on tt.product_fk=p.id where tt.id=${id}".map(rs => TicketType(rs)).single().apply()
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

  case class Company(id: Long, name: String, code: String,aesPassword:String,dateCreated:DateTime = DateTime.now,lastUpdated:DateTime = DateTime.now) extends DateAware {
  }

  object Company extends SQLSyntaxSupport[Company]{
    def apply(rn: ResultName[Company])(rs:WrappedResultSet): Company = Company(
      id=rs.get(rn.id),name = rs.get(rn.name), code=rs.get(rn.code),aesPassword = rs.get(rn.aesPassword),
      dateCreated = rs.get(rn.dateCreated),lastUpdated = rs.get(rn.lastUpdated))

    def get(id:Long):Option[Company] = {

      val c = Company.syntax("c")

      val res = DB readOnly { implicit session =>
        withSQL {
          select.from(Company as c).where.eq(c.id, id)
        }.map(Company(c.resultName)).single().apply()
      }
      res
    }

    def findByCode(code:String):Option[Company]={
      val c = Company.syntax("c")
      DB readOnly {
        implicit session =>
          withSQL {
            select.from(Company as c).where.eq(c.code, code)
          }.map(Company(c.resultName)).single().apply()
      }
    }

  }
}
