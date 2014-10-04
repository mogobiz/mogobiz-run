package com.mogobiz.cart

import com.mogobiz.cart.transaction.TransactionStatus.TransactionStatus
import com.mogobiz.cart.domain._
import org.joda.time.DateTime
import scalikejdbc._


/**
 * Created by Christophe on 22/08/2014.
 */
package object transaction {
  object TransactionStatus extends Enumeration {
    class TransactionStatusType(s: String) extends Val(s)
    type TransactionStatus = TransactionStatusType
    val PENDING = new TransactionStatusType("PENDING")
    val PAYMENT_NOT_INITIATED = new TransactionStatusType("PAYMENT_NOT_INITIATED")
    val FAILED = new TransactionStatusType("FAILED")
    val COMPLETE = new TransactionStatusType("COMPLETE")

    def valueOf(str:String):TransactionStatus = str match {
      case "PENDING"=> PENDING
      case "PAYMENT_NOT_INITIATED"=> PAYMENT_NOT_INITIATED
      case "FAILED"=> FAILED
      case "COMPLETE"=> COMPLETE
    }

    override def toString = this match {
      case PENDING => "PENDING"
      case PAYMENT_NOT_INITIATED => "PAYMENT_NOT_INITIATED"
      case FAILED => "FAILED"
      case COMPLETE => "COMPLETE"
      case _ => "Invalid value"
    }
  }

  case class BOProduct(id:Long,acquittement:Boolean=false,price:Long=0,principal:Boolean=false,productFk:Long,
                       dateCreated:DateTime = DateTime.now,lastUpdated:DateTime = DateTime.now
                       , override val uuid : String = java.util.UUID.randomUUID().toString()
                        ) extends Entity with DateAware {

    def product : Product = {
      Product.get(this.productFk).get
    }
    def delete()(implicit session: DBSession) {
      BOProduct.delete(this.id)
    }
  }
  object BOProduct extends SQLSyntaxSupport[BOProduct] with BoService {

    override val tableName = "b_o_product"

    def apply(rn: ResultName[BOProduct])(rs:WrappedResultSet): BOProduct = new BOProduct(id=rs.get(rn.id),acquittement=rs.get(rn.acquittement),price=rs.get(rn.price),principal=rs.get(rn.principal),productFk=rs.get(rn.productFk),dateCreated = rs.get(rn.dateCreated),lastUpdated = rs.get(rn.lastUpdated))

    def insert2(boProduct: BOProduct)(implicit session: DBSession) : BOProduct = {
      var boProductId = 0
      applyUpdate {
        boProductId = newId()

        val b = BOProduct.column
        insert.into(BOProduct).namedValues(
          b.uuid -> boProduct.uuid,
          b.id -> boProductId,
          b.acquittement -> boProduct.acquittement,
          b.price -> boProduct.price,
          b.principal -> boProduct.principal,
          b.productFk -> boProduct.productFk,
          b.dateCreated -> boProduct.dateCreated,
          b.lastUpdated -> boProduct.lastUpdated)
      }

      boProduct.copy(id = boProductId)

    }

    def delete(id:Long)(implicit session: DBSession) {
      withSQL {
        deleteFrom(BOProduct).where.eq(BOProduct.column.id,  id)
      }.update.apply()
    }
  }
  case class BOTicketType(id:Long,quantity : Int = 1, price:Long,shortCode:Option[String],
                          ticketType : Option[String],firstname : Option[String], lastname : Option[String],
                          email : Option[String],phone : Option[String],age:Int,
                          birthdate : Option[DateTime],startDate : Option[DateTime],endDate : Option[DateTime],
                          qrcode:Option[String]=None, qrcodeContent:Option[String]=None,
                          //bOProduct : BOProduct,
                          bOProductFk : Long,
                          dateCreated:DateTime,lastUpdated:DateTime
                          , override val uuid : String = java.util.UUID.randomUUID().toString() //TODO
                           ) extends Entity with  DateAware {

    /* TODO if possible
    def insert2(boTicketType : BOTicketType)(implicit session: DBSession) : BOTicketType = {

    }*/

    def delete()(implicit session: DBSession){
      BOTicketType.delete(this.id)
    }
  }

  object BOTicketType extends SQLSyntaxSupport[BOTicketType]{

    override val tableName = "b_o_ticket_type"

    def apply(rn: ResultName[BOTicketType])(rs:WrappedResultSet): BOTicketType = new BOTicketType(id=rs.get(rn.id),quantity=rs.get(rn.quantity),price=rs.get(rn.price),
      shortCode = rs.get(rn.shortCode),ticketType=rs.get(rn.ticketType),firstname=rs.get(rn.firstname),lastname=rs.get(rn.lastname),email=rs.get(rn.email),phone=rs.get(rn.phone),
      age = rs.get(rn.age), birthdate=rs.get(rn.birthdate),startDate=rs.get(rn.endDate),endDate=rs.get(rn.endDate),bOProductFk=rs.get(rn.bOProductFk),
      qrcode = rs.get(rn.qrcode), qrcodeContent = rs.get(rn.qrcodeContent),
      dateCreated = rs.get(rn.dateCreated),lastUpdated = rs.get(rn.lastUpdated))

    def findByBOProduct(boProductId:Long):List[BOTicketType]={
      val t = BOTicketType.syntax("t")
      DB readOnly {
        implicit session =>
          withSQL {
            select.from(BOTicketType as t).where.eq(t.bOProductFk, boProductId)
          }.map(BOTicketType(t.resultName)).list().apply()
      }
    }


    def delete(id:Long)(implicit session: DBSession) {
      //sql"delete from b_o_ticket_type where b_o_product_fk=${boProductId}"
      withSQL {
        deleteFrom(BOTicketType).where.eq(BOTicketType.column.id,  id)
      }.update.apply()
    }
  }

  case class BOCart(id:Long, transactionUuid:String,xdate:DateTime, price:Long,status : TransactionStatus, currencyCode:String,currencyRate:Double,companyFk:Long,buyer:String,
                    dateCreated:DateTime = DateTime.now,lastUpdated:DateTime = DateTime.now
                    , override val uuid : String = java.util.UUID.randomUUID().toString() //TODO
                     ) extends Entity with DateAware {

    def delete()(implicit session: DBSession){
      BOCart.delete(this.id)
    }
  }

  object BOCart extends SQLSyntaxSupport[BOCart] with BoService {

    override val tableName = "b_o_cart"

    def apply(rn: ResultName[BOCart])(rs:WrappedResultSet): BOCart = BOCart(
      id=rs.get(rn.id),transactionUuid=rs.get(rn.transactionUuid),price=rs.get(rn.price), buyer = rs.get(rn.buyer),
      xdate=rs.get(rn.xdate),status=TransactionStatus.valueOf(rs.string("s_on_t")), //FIXME
      currencyCode = rs.get(rn.currencyCode),currencyRate = rs.get(rn.currencyRate),
      companyFk=rs.get(rn.companyFk),dateCreated=rs.get(rn.dateCreated),lastUpdated=rs.get(rn.lastUpdated))

    def findByTransactionUuidAndStatus(uuid:String, status:TransactionStatus):Option[BOCart] = {

      val t = BOCart.syntax("t")
      val res: Option[BOCart] = DB readOnly {
        implicit session =>
          withSQL {
            select.from(BOCart as t).where.eq(t.transactionUuid, uuid).and.eq(t.status, status.toString) //"PENDING"
          }.map(BOCart(t.resultName)).single().apply()
      }
      res
    }

    def findByTransactionUuid(uuid:String):Option[BOCart] = {
      val t = BOCart.syntax("t")
      DB readOnly {
        implicit session =>
          withSQL {
            select.from(BOCart as t).where.eq(t.transactionUuid, uuid)
          }.map(BOCart(t.resultName)).single().apply()
      }
    }

    def insertTo(boCart: BOCart)(implicit session: DBSession):BOCart = {
      var boCartId = 0
      applyUpdate {
        boCartId = newId()
        val b = BOCart.column
        insert.into(BOCart).namedValues(
          b.uuid -> boCart.uuid,
          b.id -> boCartId,
          b.buyer -> boCart.buyer,
          b.companyFk -> boCart.companyFk,
          b.currencyCode -> boCart.currencyCode,
          b.currencyRate -> boCart.currencyRate,
          b.xdate -> DateTime.now,
          b.dateCreated -> DateTime.now,
          b.lastUpdated -> DateTime.now,
          b.price -> boCart.price,
          b.status -> TransactionStatus.PENDING.toString,
          b.transactionUuid -> boCart.transactionUuid
        )
      } //.update.apply()

      boCart.copy(id = boCartId)
    }

    def delete(id:Long)(implicit session: DBSession) {
      withSQL {
        deleteFrom(BOCart).where.eq(BOCart.column.id,  id)
      }.update.apply()
    }
  }


  //"SALE_" + boCart.id + "_" + boProduct.id
  case class BOCartItem(id:Long,code : String,
                        price: Long,
                        tax: Double,
                        endPrice: Long,
                        totalPrice: Long,
                        totalEndPrice: Long,
                        hidden : Boolean = false,
                        quantity : Int = 1,
                        startDate : Option[DateTime],
                        endDate : Option[DateTime],
                        //bOCart : BOCart,
                        ticketTypeFk : Long,
                        bOCartFk : Long,
                        dateCreated:DateTime = DateTime.now,lastUpdated:DateTime = DateTime.now
                        , override val uuid : String = java.util.UUID.randomUUID().toString() //TODO
                         ) extends Entity with  DateAware {

    def delete()(implicit session: DBSession){
      BOCartItem.delete(this.id)
    }
  }

  object BOCartItem extends SQLSyntaxSupport[BOCartItem]{

    override val tableName = "b_o_cart_item"

    def apply(rn: ResultName[BOCartItem])(rs:WrappedResultSet): BOCartItem = new BOCartItem(id=rs.get(rn.id),code=rs.get(rn.code),price=rs.get(rn.price),tax=rs.get(rn.tax),
      endPrice=rs.get(rn.endPrice),totalPrice=rs.get(rn.totalPrice),totalEndPrice=rs.get(rn.totalEndPrice),quantity=rs.get(rn.quantity),hidden=rs.get(rn.hidden),
      startDate = rs.get(rn.startDate),endDate = rs.get(rn.endDate),dateCreated = rs.get(rn.dateCreated),lastUpdated = rs.get(rn.lastUpdated),bOCartFk=rs.get(rn.bOCartFk),ticketTypeFk=rs.get(rn.ticketTypeFk))

    def findByBOCart(boCart:BOCart):List[BOCartItem] = {

      val t = BOCartItem.syntax("t")
      val res: List[BOCartItem] = DB readOnly {
        implicit session =>
          withSQL {
            select.from(BOCartItem as t).where.eq(t.bOCartFk, boCart.id)
          }.map(BOCartItem(t.resultName)).list().apply()
      }
      res
    }

    def bOProducts(boCartItem: BOCartItem) : List[BOProduct] = {

      DB readOnly {
        implicit session =>
          sql"select p.* from b_o_cart_item_b_o_product ass inner join b_o_product p on ass.boproduct_id=p.id where b_o_products_fk=${boCartItem.id}"
            .map(rs => new BOProduct(id=rs.long("id"),acquittement=rs.boolean("acquittement"),price=rs.long("price"),principal=rs.boolean("principal"),productFk=rs.long("product_fk"),dateCreated = rs.get("date_created"),lastUpdated = rs.get("last_updated"))).list().apply()
      }
    }

    def delete(id:Long)(implicit session: DBSession) {
      withSQL {
        deleteFrom(BOCartItem).where.eq(BOCartItem.column.id,  id)
      }.update.apply()
    }
  }
}
