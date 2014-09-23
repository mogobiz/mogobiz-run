package com.mogobiz.cart

import com.mogobiz.cart.ProductCalendar._
import com.mogobiz.cart.ProductType._
import com.mogobiz.cart.domain.ReductionRule
import com.mogobiz.cart.domain.ReductionRuleType.ReductionRuleType
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

  case class Poi(id:Long,road1:Option[String],road2:Option[String],city:Option[String],postalCode:Option[String],state:Option[String],countryCode:Option[String])
  object Poi extends SQLSyntaxSupport[Poi] {

    def apply(rn: ResultName[Poi])(rs: WrappedResultSet): Poi = Poi(
      id = rs.get(rn.id),
      road1 = rs.get(rn.road1),
      road2 = rs.get(rn.road2),
      city = rs.get(rn.city),
      postalCode = rs.get(rn.postalCode),
      state = rs.get(rn.state),
      countryCode = rs.get(rn.countryCode)
    )

    def get(id:Long):Option[Poi] = {

      val c = Poi.syntax("c")

      val res = DB readOnly { implicit session =>
        withSQL {
          select.from(Poi as c).where.eq(c.id, id)
        }.map(Poi(c.resultName)).single().apply()
      }
      res
    }

  }


  case class Stock(stock:Long=0,stockUnlimited:Boolean = true,stockOutSelling:Boolean = false)
  object Stock  extends SQLSyntaxSupport[Stock]{
    def apply(rs: WrappedResultSet):Stock = new Stock(rs.longOpt("stock_stock").getOrElse(0),rs.boolean("stock_stock_unlimited"),rs.boolean("stock_stock_out_selling"))
    //  def apply(rs: WrappedResultSet):Stock = new Stock(rs.long("ss_on_t"),rs.boolean("ssu_on_t"),rs.boolean("ssos_on_t"))
    // t.stock_stock as ss_on_t, t.stock_stock_out_selling as ssos_on_t, t.stock_stock_unlimited as ssu_on_t,

    //def apply(rn: ResultName[TicketType])(rs:WrappedResultSet): Stock = new Stock(stock=rs.get(rn.stock),stockUnlimited=rs.get(rn.stockUnlimited),stockOutSelling=rs.get(rn.stockOutSelling))
  }

  case class StockCalendar(
                            id:Long,stock:Long,sold:Long,startDate:Option[DateTime],product:Product,ticketType:TicketType,
                            dateCreated:DateTime,lastUpdated:DateTime) extends Entity with DateAware

  object StockCalendar {

    def insert(s:StockCalendar) = {
      sql"""insert into stock_calendar(id, date_created, last_updated, product_fk, sold, start_date, stock, ticket_type_fk, uuid)
           values (${s.id},${s.dateCreated},${s.lastUpdated},${s.product.id},${s.sold},${s.startDate},${s.stock},${s.ticketType.id}, ${s.uuid})"""
    }
  }
  case class Product(id:Long,name:String,xtype:ProductType, calendarType:ProductCalendar,taxRateFk:Option[Long], taxRate: Option[TaxRate],
                     poiFk:Option[Long],shippingFk:Option[Long],companyFk:Long,
                     startDate:Option[DateTime],stopDate:Option[DateTime] ){

    def company = Company.get(companyFk)

    def shipping = shippingFk match {
      case Some(shippingId) => Product.getShipping(shippingId)
      case None => None
    }

    def poi = poiFk match {
      case Some(id) => Poi.get(id)
      case None => None
    }
  }

  object Product extends SQLSyntaxSupport[Product]{

    def apply(p: SyntaxProvider[Product])(rs:WrappedResultSet) : Product = apply(p.resultName)(rs)

    def apply(rn: ResultName[Product])(rs:WrappedResultSet): Product = new Product(
      id = rs.get(rn.id),
      name = rs.get(rn.name),
      xtype = ProductType.valueOf(rs.string("x_on_p")),//,rs.get(rn.xtype), //FIXME
      calendarType = ProductCalendar.valueOf(rs.string("ct_on_p")), //rs.get(rn.calendarType), //FIXME
      taxRateFk = rs.get(rn.taxRateFk),
      taxRate = None, //Some(TaxRate(rs)),
      companyFk = rs.get(rn.companyFk),
      poiFk = rs.get(rn.poiFk),
      shippingFk = rs.get(rn.shippingFk),
      startDate = rs.get(rn.startDate),
      stopDate = rs.get(rn.stopDate)
    )


    def apply(rs:WrappedResultSet): Product = Product(id = rs.long("id"),name = rs.string("name"),xtype = ProductType.valueOf(rs.string("xtype")),calendarType = ProductCalendar.valueOf(rs.string("calendar_type")),taxRateFk = rs.longOpt("tax_rate_fk"),taxRate = Some(TaxRate(rs)),companyFk = rs.long("company_fk"),poiFk= rs.longOpt("poi_fk"),shippingFk= rs.longOpt("shipping_fk"),startDate=rs.get("start_date"),stopDate=rs.get("stop_date"))
    def applyFk(rs:WrappedResultSet): Product = Product(id = rs.long("product_fk"),name = rs.string("name"),xtype = ProductType.valueOf(rs.string("xtype")),calendarType = ProductCalendar.valueOf(rs.string("calendar_type")),taxRateFk = rs.longOpt("tax_rate_fk"),taxRate = Some(TaxRate(rs)),companyFk = rs.long("company_fk"),poiFk= rs.longOpt("poi_fk"), shippingFk= rs.longOpt("shipping_fk"),startDate=rs.get("start_date"),stopDate=rs.get("stop_date"))

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

  case class TaxRate(id:Long,name:String,company_fk:Long)
  object TaxRate extends SQLSyntaxSupport[TaxRate] {
    def apply(p: SyntaxProvider[TaxRate])(rs:WrappedResultSet) : TaxRate = apply(p.resultName)(rs)
    def apply(tr: ResultName[TaxRate])(rs:WrappedResultSet) : TaxRate = new TaxRate(id=rs.get(tr.id),name=rs.get(tr.name),company_fk = rs.get(tr.company_fk))
    def apply(rs:WrappedResultSet) : TaxRate = new TaxRate(id=rs.long("tax_rate_fk"),name=rs.string("name"),company_fk = rs.long("company_fk"))

    def opt(tr: SyntaxProvider[TaxRate])(rs: WrappedResultSet): Option[TaxRate] =
      rs.longOpt(tr.resultName.id).map(_ => TaxRate(tr)(rs))
  }

  case class TicketType(id:Long,name:String,price:Long,minOrder:Long=0,maxOrder:Long=0,stock:Option[Stock]=None,product:Option[Product]=None,startDate:Option[DateTime]=None,stopDate:Option[DateTime]=None){

  }
  object TicketType extends SQLSyntaxSupport[TicketType] {

    def apply(rs:WrappedResultSet):TicketType = TicketType(
      id=rs.long("id"),
      name=rs.string("name"),
      price=rs.long("price"),minOrder=rs.long("min_order"),maxOrder=rs.long("max_order"),
      stock=Some(Stock(rs)),
      startDate = rs.get("start_date"),
      stopDate = rs.get("stop_date"),
      product = Some(Product.applyFk(rs)))

    def apply(rn: ResultName[TicketType])(rs:WrappedResultSet): TicketType = new TicketType(
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

  case class Coupon
  (id: Long, name: String, code: String, companyFk: Long,startDate: Option[DateTime]=None, endDate: Option[DateTime]=None, //price: Long,
   numberOfUses:Option[Long]=None, reductionSoldFk:Option[Long]=None,active: Boolean = true, anonymous:Boolean = false, catalogWise:Boolean = false,
   dateCreated:DateTime = DateTime.now,lastUpdated:DateTime = DateTime.now) extends DateAware {

    def rules = Coupon.getRules(this.id)
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

  object Coupon extends SQLSyntaxSupport[Coupon]{
    def apply(rn: ResultName[Coupon])(rs:WrappedResultSet): Coupon = Coupon(
      id=rs.get(rn.id),name = rs.get(rn.name), code=rs.get(rn.code), startDate=rs.get(rn.startDate),endDate=rs.get(rn.endDate),
      numberOfUses = rs.get(rn.numberOfUses),companyFk = rs.get(rn.companyFk),reductionSoldFk=rs.get(rn.reductionSoldFk),
      active = rs.get(rn.active), anonymous = rs.get(rn.anonymous), catalogWise = rs.get(rn.catalogWise),
      dateCreated = rs.get(rn.dateCreated),lastUpdated = rs.get(rn.lastUpdated))

    def apply(rs: WrappedResultSet):Coupon = Coupon(
      id = rs.long("id"), name = rs.string("name"), code=rs.string("code"), startDate=rs.jodaDateTimeOpt("start_date"),endDate=rs.jodaDateTimeOpt("end_date"),
      numberOfUses = rs.longOpt("number_of_uses"),companyFk = rs.long("company_fk"),reductionSoldFk=rs.longOpt("reduction_sold_fk"),
      active = rs.boolean("active"), anonymous = rs.boolean("anonymous"), catalogWise = rs.boolean("catalog_wise"),
      dateCreated = rs.jodaDateTime("date_created"),lastUpdated = rs.jodaDateTime("last_updated")
    )


    def findByCode(companyCode:String, couponCode:String):Option[Coupon]={
      val compagny = Company.findByCode(companyCode)
      if (compagny.isEmpty) None
      else {
        val c = Coupon.syntax("c")
        DB readOnly {
          implicit session =>
            withSQL {
              select.from(Coupon as c).where.eq(c.code, couponCode).and.eq(c.companyFk,compagny.get.id)
            }.map(Coupon(c.resultName)).single().apply()
        }
      }
    }

    def findPromotionsThatOnlyApplyOnCart(companyCode:String):List[Coupon]={
      val compagny = Company.findByCode(companyCode)
      if (compagny.isEmpty) List()
      else {
        //val c = Coupon.syntax("c")
        val now = DateTime.now
        DB readOnly {
          implicit session =>
            sql""" select c.* from coupon c inner join coupon_reduction_rule crr on crr.rules_fk = c.id inner join reduction_rule rr on crr.reduction_rule_id = rr.id and rr.xtype = 'X_PURCHASED_Y_OFFERED' where company_fk=9 and start_date<=${now} and end_date>=${now} and anonymous=true """.map(rs => Coupon(rs)).list().apply()
            /*
            withSQL {
              select.from(Coupon as c).where.eq(c.companyFk,compagny.get.id).and.eq(c.anonymous, true).and.lt(c.startDate,now).and.gt(c.endDate)
            }.map(Coupon(c.resultName)).list().apply()
            */
        }
      }
    }

    def get(id: Long)/*(implicit session: DBSession)*/:Option[Coupon]={
      val c = Coupon.syntax("c")
      DB readOnly { implicit session =>
        withSQL {
          select.from(Coupon as c).where.eq(c.id, id)
        }.map(Coupon(c.resultName)).single().apply()
      }
    }

    def getRules(couponId:Long):List[ReductionRule]= DB readOnly {
      implicit session => {
        sql"""select rr.* from reduction_rule rr inner join coupon_reduction_rule crr on crr.reduction_rule_id = rr.id and rules_fk = ${couponId}"""
          .map(rs => ReductionRule(rs)).list().apply()
      }
    }

    /*
    def getRules(couponId:Long):List[ReductionRule]={
      val c = ReductionRule.syntax("c")
      DB readOnly { implicit session =>
        withSQL {
          select.from(ReductionRule as c).where.eq(c.id, couponId)
        }.map(ReductionRule(c.resultName)).list().apply()
      }
    }*/
  }

  object ReductionRuleType extends Enumeration {
    class ReductionRuleTypeType(s: String) extends Val(s)
    type ReductionRuleType = ReductionRuleTypeType
    val DISCOUNT = new ReductionRuleTypeType("DISCOUNT")
    val X_PURCHASED_Y_OFFERED = new ReductionRuleTypeType("X_PURCHASED_Y_OFFERED")

    def apply(name:String) = name match{
      case "DISCOUNT" => DISCOUNT
      case "X_PURCHASED_Y_OFFERED" => X_PURCHASED_Y_OFFERED
      case _ => throw new Exception("Not expected ReductionRuleType")
    }
  }

  case class ReductionRule(
                            id:Long,
                            xtype: ReductionRuleType,
                            quantityMin:Option[Long],
                            quantityMax:Option[Long],
                            discount:Option[String], //discount (or percent) if type is DISCOUNT (example : -1000 or * 10%)
                            xPurchased:Option[Long], yOffered:Option[Long],
                            dateCreated:DateTime = DateTime.now,lastUpdated:DateTime = DateTime.now) extends DateAware

  object ReductionRule extends SQLSyntaxSupport[ReductionRule] {
    def apply(rn: ResultName[ReductionRule])(rs: WrappedResultSet): ReductionRule = ReductionRule(
      id = rs.get(rn.id), xtype = ReductionRuleType(rs.string("xtype")), quantityMin = rs.get(rn.quantityMin), quantityMax = rs.get(rn.quantityMax),
      discount = rs.get(rn.discount), xPurchased = rs.get(rn.xPurchased), yOffered = rs.get(rn.yOffered),
      dateCreated = rs.get(rn.dateCreated), lastUpdated = rs.get(rn.lastUpdated))

    def apply(rs: WrappedResultSet):ReductionRule = ReductionRule(
      id = rs.long("id"), xtype = ReductionRuleType(rs.string("xtype")),quantityMin = rs.longOpt("quantity_min"), quantityMax = rs.longOpt("quantity_max"),
      discount = rs.stringOpt("discount"), xPurchased = rs.longOpt("x_purchased"), yOffered = rs.longOpt("y_offered"),
      dateCreated = rs.get("date_created"), lastUpdated = rs.get("last_updated")
    )
  }

}
