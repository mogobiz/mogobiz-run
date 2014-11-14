package com.mogobiz.run.handlers

import java.util.{UUID, Calendar, Date}
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.{SerializerProvider, JsonSerializer}
import com.mogobiz.pay.model.Mogopay
import com.mogobiz.run.boot.DBInitializer
import com.mogobiz.run.cart.ProductCalendar._
import com.mogobiz.run.cart.ProductType._
import com.mogobiz.run.cart._
import com.mogobiz.run.config.Settings
import com.mogobiz.run.implicits.Json4sProtocol
import com.mogobiz.run.model.{StoreCartItem, StoreCoupon, StoreCart}
import com.sksamuel.elastic4s.ElasticDsl._
import com.mogobiz.es.EsClient
import org.elasticsearch.common.joda.time.format.{ISODateTimeFormat, DateTimeFormatter}
import org.joda.time.{DateTimeZone, DateTime}

/**
 * Manage UuidData ES Index
 * Created by yoannbaudy on 12/11/2014.
 */
class UuidHandler {

  private val QUEUE_XTYPE_CART = "Cart"

  /**
   * Create or update a UuidDate for the given uuid and xtype
   * @param dataUuid identifier
   * @param payload content
   * @param xtype data type stored
   */
  private def createAndSave(dataUuid: String, userUuid: Option[Mogopay.Document], payload: String, xtype: String): Unit = {
    val lifetime = 60 * Settings.cart.lifetime
    val expireDate = DateTime.now.plusSeconds(lifetime).toDate

    val uuidData = UuidDataDao.findByUuidAndXtype(dataUuid, userUuid, xtype)
    if (uuidData.isDefined) {
      // update the existing UuidData
      UuidDataDao.save(uuidData.get.copy(payload = payload, expireDate = expireDate))
    }
    else {
      // create a new UuidData
      UuidDataDao.save(new UuidData(UUID.randomUUID().toString, dataUuid, userUuid, xtype, payload, expireDate))
    }
  }

  def getCart(dataUuid: String, userUuid: Option[Mogopay.Document]): Option[StoreCart] = {
    import Json4sProtocol._
    import org.json4s.native.JsonMethods._

    UuidDataDao.findByUuidAndXtype(dataUuid, userUuid, QUEUE_XTYPE_CART) match {
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
    createAndSave(cart.dataUuid, cart.userUuid, payload, QUEUE_XTYPE_CART)
  }

  def removeCart(cart: StoreCart): Unit = {
    UuidDataDao.delete(cart.dataUuid, cart.userUuid, QUEUE_XTYPE_CART)
  }

  def getExpiredCarts: List[StoreCart] = {
    import Json4sProtocol._
    import org.json4s.native.JsonMethods._

    UuidDataDao.getExpired(QUEUE_XTYPE_CART).map {
      d => parse(d.payload).extract[StoreCart]
    }
  }

}

case class UuidData(uuid: String,
                    dataUuid: String,
                    userUuid: Option[Mogopay.Document],
                    xtype: String,
                    payload: String,
                    expireDate: Date,
                    var dateCreated: Date = Calendar.getInstance().getTime,
                    var lastUpdated: Date = Calendar.getInstance().getTime)

object UuidDataDao {

  val index = Settings.cart.EsIndex

  def findByUuidAndXtype(dataUuid: String, userUuid: Option[Mogopay.Document], xtype: String): Option[UuidData] = {
    val uuidUserFilter = if (userUuid.isDefined) termFilter("userUuid", userUuid.get)
    else missingFilter("userUuid") existence true includeNull true

    val req = search in index -> "UuidData" filter and (
      termFilter("dataUuid", dataUuid),
      termFilter("xtype", xtype),
      uuidUserFilter
    )

    EsClient.search[UuidData](req)
  }

  def save(entity: UuidData): Boolean = {
    EsClient.update(index, entity, true, false)
  }

  def delete(dataUuid: String, userUuid: Option[String], xtype: String) : Unit = {
    val uuidData = findByUuidAndXtype(dataUuid, userUuid, xtype)
    if (uuidData.isDefined) EsClient.delete(index, uuidData.get.uuid, false)
  }

  def getExpired(xtype: String) : List[UuidData] = {
    val req = search in index -> "UuidData" filter and (
      termFilter("xtype", xtype),
      rangeFilter("expireDate") lt "now"
    )

    EsClient.searchAll[UuidData](req).toList
  }
}

object UuidDataMain extends App {

  DBInitializer()

  val coupons = List(StoreCoupon(1, "TEST1", "mogobiz"), StoreCoupon(2, "TEST2", "mogobiz"))

  val shipping1 = ShippingVO(1, 10, WeightUnit.KG, 11, 12, 13, LinearUnit.CM, 1000, true)
  val shipping2 = ShippingVO(2, 10, WeightUnit.KG, 11, 12, 13, LinearUnit.CM, 1000, true)

  val regCartItem1 = RegisteredCartItemVO("cartItemid1", "1", Some("yoann.baudy@ebiznext.com"), Some("yo"), Some("ba"), Some("0123456789"), None)
  val regCartItem2 = RegisteredCartItemVO("cartItemid1", "2", Some("yoann.baudy@ebiznext.com"), Some("yo"), Some("ba"), Some("0123456789"), None)
  val regCartItem3 = RegisteredCartItemVO("cartItemid2", "1", Some("yoann.baudy@ebiznext.com"), Some("yo"), Some("ba"), Some("0123456789"), None)
  val regCartItem4 = RegisteredCartItemVO("cartItemid2", "2", Some("yoann.baudy@ebiznext.com"), Some("yo"), Some("ba"), Some("0123456789"), None)

  val cartItem1 = StoreCartItem("cartItem1", 1, "product", ProductType.PRODUCT, ProductCalendar.NO_DATE, 2, "sku", 1, 1000, None, None, List(regCartItem1, regCartItem2), Some(shipping1))
  val cartItem2 = StoreCartItem("cartItem2", 1, "product", ProductType.PRODUCT, ProductCalendar.NO_DATE, 2, "sku", 1, 1000, None, None, List(regCartItem3, regCartItem4), Some(shipping2))

  val cart = StoreCart("uuid", "dateuuid", Some("useruuid"), List(cartItem1, cartItem2), coupons, true, true)

  EsClient.update(Settings.cart.EsIndex, cart, true, false)
}
