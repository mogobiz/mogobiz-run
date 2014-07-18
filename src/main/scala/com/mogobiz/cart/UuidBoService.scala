package com.mogobiz.cart

import org.json4s.{FieldSerializer, DefaultFormats, Formats}
import scalikejdbc._, SQLInterpolation._

import org.joda.time.DateTime
import scalikejdbc.WrappedResultSet
import scala.Some

/**
 * Created by Christophe on 05/05/2014.
 */
object UuidBoService extends BoService {

  private val QUEUE_XTYPE_CART = "Cart"
  val store : Map[String,UuidData] = Map()

  /**
   * Create or update a UuidDate for the given uuid and xtype
   * @param uuid
   * @param payload
   * @param xtype
   */
  def createAndSave(uuid:String, payload:String, xtype:String) : Unit = {
    val lifetime = 60 * 15 //min //TODO externalize as config param
    val expireDate = DateTime.now.plusSeconds(lifetime)

    val uuidData = UuidDataDao.findByUuidAndXtype(uuid, xtype)
    if (uuidData.isDefined) {
      // update the existing UuidData
      UuidDataDao.save(new UuidData(uuidData.get.id,uuid,xtype,payload,uuidData.get.createdDate,expireDate))
    }
    else {
      // create a new UuidData
      UuidDataDao.save(new UuidData(None,uuid,xtype,payload,DateTime.now,expireDate))
    }
  }

  def getCart(uuid:String): Option[CartVO] = {
    import org.json4s.native.JsonMethods._
    import com.mogobiz.Json4sProtocol._

    UuidDataDao.findByUuidAndXtype(uuid, QUEUE_XTYPE_CART) match {
      case Some(data) => {
        val parsed = parse(data.payload)
        val cart = parsed.extract[CartVO]
        Some(cart)
      }
      case _ => None
    }
  }

  def setCart(cart: CartVO): Unit = {
    import org.json4s.native.Serialization.{write}
    import com.mogobiz.Json4sProtocol._

    val payload = write(cart)
    createAndSave(cart.uuid,payload,QUEUE_XTYPE_CART)
  }
}

case class UuidData(id:Option[Int],uuid:String, xtype:String, payload:String, createdDate: DateTime, expireDate: DateTime);

object UuidDataDao extends SQLSyntaxSupport[UuidData] {

  def apply(rs:WrappedResultSet): UuidData = {
    new UuidData(id=Some(rs.int("id")),
      uuid=rs.string("uuid"),
      xtype = rs.string("xtype"),
      payload=rs.string("payload"),
      createdDate=rs.dateTime("date_created"),
      expireDate=rs.dateTime("expire_date"))
  }

  def findByUuidAndXtype(uuid: String, xtype: String): Option[UuidData] = {
    DB readOnly { implicit session =>
      sql"""select * from uuid_data where uuid=${uuid} and xtype=${xtype}""".map(rs => UuidDataDao(rs)).single().apply()
    }
  }

  def save(entity: UuidData): Int = {
    DB localTx { implicit session =>
      if (entity.id.isEmpty) {
        sql"""insert into uuid_data(id,date_created, expire_date, last_updated, payload, uuid, xtype)
           values (${UuidBoService.newId()},${DateTime.now},${entity.expireDate},${DateTime.now},${entity.payload},${entity.uuid},${entity.xtype})""".
          update().apply()
      }
    else {
        sql"""update uuid_data set expire_date=${entity.expireDate},last_updated=${DateTime.now},payload=${entity.payload} where id=${entity.id}""".
          update().apply()
      }
    }
  }
}