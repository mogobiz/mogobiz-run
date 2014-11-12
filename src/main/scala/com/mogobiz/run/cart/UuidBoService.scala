package com.mogobiz.run.cart

import com.mogobiz.pay.model.Mogopay
import com.mogobiz.run.config.Settings
import com.mogobiz.run.implicits.Json4sProtocol
import com.mogobiz.run.model.StoreCart
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
  private def createAndSave(uuid: String, userUuid: Option[Mogopay.Document], payload: String, xtype: String): Unit = {
    val lifetime = 60 * Settings.cart.lifetime
    val expireDate = DateTime.now.plusSeconds(lifetime)

    val uuidData = UuidDataDao.findByUuidAndXtype(uuid, userUuid, xtype)
    if (uuidData.isDefined) {
      // update the existing UuidData
      UuidDataDao.save(new UuidData(uuidData.get.id, uuid, userUuid, xtype, payload, uuidData.get.createdDate, expireDate))
    }
    else {
      // create a new UuidData
      UuidDataDao.save(new UuidData(None, uuid, userUuid, xtype, payload, DateTime.now, expireDate))
    }
  }

  def getCart(uuid: String, userUuid: Option[Mogopay.Document]): Option[StoreCart] = {
    import Json4sProtocol._
    import org.json4s.native.JsonMethods._

    UuidDataDao.findByUuidAndXtype(uuid, userUuid, QUEUE_XTYPE_CART) match {
      case Some(data) =>
        val parsed = parse(data.payload)
        val cart = parsed.extract[StoreCart]
        Some(cart)
      case _ => None
    }
  }

  def setCart(cart: StoreCart): Unit = {
    import Json4sProtocol._
    import org.json4s.native.Serialization.write

    val payload = write(cart)
    createAndSave(cart.uuid, cart.userUuid, payload, QUEUE_XTYPE_CART)
  }

  def removeCart(cart: StoreCart): Unit = {
    UuidDataDao.delete(cart.uuid, cart.userUuid)
  }

  def getExpired: List[StoreCart] = {
    import Json4sProtocol._
    import org.json4s.native.JsonMethods._

    UuidDataDao.getExpired.map {
      d => parse(d.payload).extract[StoreCart]
    }
  }
}

case class UuidData(id: Option[Int], uuid: String, userUuid: Option[String], xtype: String, payload: String, createdDate: DateTime, expireDate: DateTime)

object UuidDataDao extends SQLSyntaxSupport[UuidData] {

  def apply(rs: WrappedResultSet): UuidData = {
    new UuidData(id = Some(rs.int("id")),
      uuid = rs.string("uuid"),
      userUuid = Option(rs.string("user_uuid")),
      xtype = rs.string("xtype"),
      payload = rs.string("payload"),
      createdDate = rs.get("date_created"),
      expireDate = rs.get("expire_date"))
  }

  def findByUuidAndXtype(uuid: String, userUuid: Option[Mogopay.Document], xtype: String): Option[UuidData] = {
    DB readOnly { implicit session =>
      userUuid match {
        case Some(userUuid) =>
          sql"""select * from uuid_data where uuid=$uuid  and user_uuid=$userUuid and xtype=$xtype""".map(rs => UuidDataDao(rs)).single().apply()
        case None =>
          sql"""select * from uuid_data where uuid=$uuid and user_uuid is null and xtype=$xtype""".map(rs => UuidDataDao(rs)).single().apply()

      }
    }
  }

  def save(entity: UuidData): Int = {
    DB localTx { implicit session =>
      if (entity.id.isEmpty) {
        sql"""insert into uuid_data(id,date_created, expire_date, last_updated, payload, uuid, xtype, user_uuid)
           values (${UuidBoService.newId()},${DateTime.now},${entity.expireDate},${DateTime.now},${entity.payload},${entity.uuid},${entity.xtype}, ${entity.userUuid.orNull})""".
          update().apply()
      }
      else {
        sql"""update uuid_data set expire_date=${entity.expireDate},last_updated=${DateTime.now},payload=${entity.payload} where id=${entity.id}""".
          update().apply()
      }
    }
  }

  def delete(uuid: String, userUuid: Option[String]): Unit = {
    DB localTx { implicit session =>
      userUuid match {
        case Some(userUuid) =>
          sql""" delete from uuid_data where uuid=${uuid} and user_uuid=${userUuid}""".update().apply()
        case None =>
          sql""" delete from uuid_data where uuid=${uuid} and user_uuid is null""".update().apply()

      }

    }
  }

  def getExpired: List[UuidData] = {
    DB readOnly { implicit session =>
      sql"""select * from uuid_data where expire_date < ${DateTime.now} """.map(rs => UuidDataDao(rs)).list().apply()
    }
  }
}