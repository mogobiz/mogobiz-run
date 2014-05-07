package com.mogobiz.cart

import com.mogobiz.cart.UuidBoService.VAR1024
import com.mogobiz.cart.ProductCalendar.ProductCalendar
import java.util.Calendar
import org.json4s.{FieldSerializer, DefaultFormats, Formats}
import com.mogobiz.vo.Comment
import scalikejdbc._, SQLInterpolation._

import org.joda.time.DateTime

/**
 * Created by Christophe on 05/05/2014.
 */
object UuidBoService extends BoService {

  private val QUEUE_XTYPE_CART = "Cart"
  val store : Map[String,UuidData] = Map()

  type VAR1024 = String

  def createAndSave(uuid:VAR1024, payload:String, xtype:VAR1024) : UuidData = {
    val createdDate = DateTime.now
    val lifetime = 60 * 15 //min //TODO externalize as config param
    //expireDate.add(Calendar.SECOND, lifetime);
    val expireDate = DateTime.now.plusSeconds(lifetime)

    //TODO SQL save
//id, date_created, expire_date, last_updated, payload, uuid, xtype FROM uuid_data;
    val id = DB localTx { implicit session =>
        val newid = newId()
      sql"""insert into uuid_data(id,date_created, expire_date, last_updated, payload, uuid, xtype)
           values (${newid},${createdDate},${expireDate},${createdDate},${payload},${uuid},${xtype})""".
        update().apply()
      newid
        //updateAndReturnGeneratedKey().apply()
    }
    new UuidData(Some(id),uuid,payload,xtype,expireDate)
  }

  def get(uuid:String): Option[UuidData] = {
    DB readOnly{ implicit session =>
      sql"""select * from uuid_data where uuid=${uuid}""".map(rs => new UuidData(Some(rs.int("id")),rs.string("uuid"),rs.string("payload"),rs.string("xtype"),rs.dateTime("expire_date"))).single().apply()
    }
  }

  def set(cart: CartVO) = {
    import org.json4s.native.Serialization.{write}
    //CartItemVO
    implicit def json4sFormats: Formats = DefaultFormats + FieldSerializer[CartVO]()

    val payload = write(cart)
    createAndSave(cart.uuid,payload,QUEUE_XTYPE_CART)
  }
}


case class UuidData(id:Option[Int],uuid:VAR1024, payload:String, xtype:VAR1024, expireDate: DateTime);
