package com.mogobiz.run.cart

import com.mogobiz.run.config.Settings
import com.mogobiz.run.implicits.Json4sProtocol
import org.joda.time.DateTime
import scalikejdbc._

object UuidBoService extends BoService {

  private val QUEUE_XTYPE_CART = "Cart"
  val store: Map[String, UuidData] = Map()

  /**
   * Create or update a UuidDate for the given uuid and xtype
   * @param uuid identifier
   * @param payload content
   * @param xtype data type stored
   */
  def createAndSave(uuid: String, payload: String, xtype: String): Unit = {
    val lifetime = 60 * Settings.cart.lifetime
    val expireDate = DateTime.now.plusSeconds(lifetime)

    val uuidData = UuidDataDao.findByUuidAndXtype(uuid, xtype)
    if (uuidData.isDefined) {
      // update the existing UuidData
      UuidDataDao.save(new UuidData(uuidData.get.id, uuid, xtype, payload, uuidData.get.createdDate, expireDate))
    }
    else {
      // create a new UuidData
      UuidDataDao.save(new UuidData(None, uuid, xtype, payload, DateTime.now, expireDate))
    }
  }

  def getCart(uuid: String): Option[CartVO] = {
    import Json4sProtocol._
    import org.json4s.native.JsonMethods._

    UuidDataDao.findByUuidAndXtype(uuid, QUEUE_XTYPE_CART) match {
      case Some(data) =>
        val parsed = parse(data.payload)
        val cart = parsed.extract[CartVO]
        Some(cart)
      case _ => None
    }
  }

  def setCart(cart: CartVO): Unit = {
    import Json4sProtocol._
    import org.json4s.native.Serialization.write

    val payload = write(cart)
    createAndSave(cart.uuid, payload, QUEUE_XTYPE_CART)
  }

  def removeCart(cart: CartVO): Unit = {
    UuidDataDao.delete(cart.uuid)
  }

  def getExpired: List[CartVO] = {
    import Json4sProtocol._
    import org.json4s.native.JsonMethods._

    UuidDataDao.getExpired.map {
      d => parse(d.payload).extract[CartVO]
    }
  }
}

case class UuidData(id:Option[Int],uuid:String, xtype:String, payload:String, createdDate: DateTime, expireDate: DateTime)

object UuidDataDao extends SQLSyntaxSupport[UuidData] {

  def apply(rs:WrappedResultSet): UuidData = {
    new UuidData(id=Some(rs.int("id")),
      uuid=rs.string("uuid"),
      xtype = rs.string("xtype"),
      payload=rs.string("payload"),
      createdDate=rs.get("date_created"),
      expireDate=rs.get("expire_date"))
  }

  def findByUuidAndXtype(uuid: String, xtype: String): Option[UuidData] = {
    DB readOnly { implicit session =>
      sql"""select * from uuid_data where uuid=$uuid and xtype=$xtype""".map(rs => UuidDataDao(rs)).single().apply()
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

  def delete(uuid: String): Unit = {
    DB localTx { implicit session =>
      sql""" delete from uuid_data where uuid=${uuid} """.update().apply()
    }
  }

  def getExpired : List[UuidData] = {
    DB readOnly { implicit session =>
      sql"""select * from uuid_data where expire_date < ${DateTime.now} """.map(rs => UuidDataDao(rs)).list().apply()
    }
  }
}