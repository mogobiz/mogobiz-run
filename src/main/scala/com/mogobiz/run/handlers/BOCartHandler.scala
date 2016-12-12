package com.mogobiz.run.handlers

import java.util.{Date, UUID}

import com.fasterxml.jackson.databind.annotation.{JsonDeserialize, JsonSerialize}
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import com.mogobiz.es.EsClient
import com.mogobiz.json.JacksonConverter
import com.mogobiz.pay.common.ExternalCode
import com.mogobiz.pay.model.AccountAddress
import com.mogobiz.pay.sql.BOTransactionDAO
import com.mogobiz.run.es._
import com.mogobiz.run.json.{JodaDateTimeDeserializer, JodaDateTimeOptionDeserializer, JodaDateTimeOptionSerializer, JodaDateTimeSerializer}
import com.mogobiz.run.model.Mogobiz._
import com.mogobiz.run.model.Render.RegisteredCartItem
import com.mogobiz.run.model._
import com.mogobiz.run.utils.Utils
import com.sksamuel.elastic4s.ElasticDsl.{search => essearch, insert => _, _}
import org.joda.time.DateTime
import scalikejdbc._

/**
  * Created by yoannbaudy on 01/12/2016.
  */
class BOCartHandler extends BoService {

  def buildIndex(storeCode: String) = s"${storeCode}_bo"

  def find(storeCode: String, uuid: String): Option[BOCart] = {
    EsClient.load[BOCart](buildIndex(storeCode), uuid)
  }

  def findByTransactionUuid(storeCode: String, transactionUuid: String): Option[BOCart] = {
    val query = essearch in buildIndex(storeCode) types "BOTransaction" postFilter termsFilter("transactionUuid", transactionUuid)
    EsClient.search[BOCart](query)
  }

  def esSave(storeCode: String, boCart: BOCart, refresh: Boolean = false): Unit = {
    val upsert = true
    EsClient.update[BOCart](buildIndex(storeCode), boCart, upsert, refresh)
  }

  def esDelete(storeCode: String, uuid: String): Unit = {
    val refresh = false
    EsClient.delete[BOCart](buildIndex(storeCode), uuid, refresh)
  }

  def create(boCart: BOCart)(implicit session: DBSession) : BOCart = {
    BOCartDao.create(boCart)
    boCart
  }

  def update(boCart: BOCart)(implicit session: DBSession): BOCart = {
    BOCartDao.update(boCart)
    boCart
  }

  def delete(boCart: BOCart)(implicit session: DBSession) = {
    BOCartDao.delete(boCart)
  }

  def initRegisteredCartItem(uuid: String,
                             sku: Mogobiz.Sku,
                             cartItem: StoreCartItemWithPrices,
                             registeredCartItem: RegisteredCartItem,
                             shortCode: Option[String],
                             qrcode: Option[String],
                             qrcodeContent: Option[String]) : BORegisteredCartItem = {
    BORegisteredCartItem(age = Utils.computeAge(registeredCartItem.birthdate),
                          quantity = 1, // Un seul ticket par bénéficiaire
                          price = cartItem.saleTotalEndPrice.getOrElse(cartItem.saleTotalPrice),
                          ticketType = sku.name,
                          firstname = registeredCartItem.firstname,
                          lastname = registeredCartItem.lastname,
                          email = registeredCartItem.email,
                          phone = registeredCartItem.phone,
                          birthdate = registeredCartItem.birthdate,
                          shortCode = shortCode,
                          qrcode = qrcode,
                          qrcodeContent = qrcodeContent,
                          startDate = cartItem.startDate,
                          endDate = cartItem.endDate,
                          uuid = uuid)
  }

  def initBOProduct(uuid: String,
                    cartItem: StoreCartItemWithPrices,
                    product: Mogobiz.Product,
                    principal: Boolean,
                    registeredCartItems: List[BORegisteredCartItem]) : BOProduct = {
    val acquittement = false
    val price = cartItem.saleTotalEndPrice.getOrElse(cartItem.saleTotalPrice)
    BOProduct(acquittement,
      principal,
      price,
      product,
      registeredCartItems,
      uuid)
  }

  def initBODelivery(shippingAddress: String) : BODelivery = {
    BODelivery(DeliveryStatus.NOT_STARTED,
      None,
      shippingAddress,
      UUID.randomUUID().toString)
  }

  def initBOCartItem(uuid: String,
                     cartItem: StoreCartItemWithPrices,
                     sku: Mogobiz.Sku,
                     boProduct: BOProduct,
                     bODelivery: Option[BODelivery]) : BOCartItem= {
    val hidden = false
    val tax : Double = cartItem.tax.map {_.toDouble}.getOrElse(0)
    BOCartItem(cartItem.salePrice,
      tax,
      cartItem.saleEndPrice.getOrElse(cartItem.salePrice),
      cartItem.saleTotalPrice,
      cartItem.saleTotalEndPrice.getOrElse(cartItem.saleTotalPrice),
      hidden,
      cartItem.quantity,
      cartItem.startDate,
      cartItem.endDate,
      sku,
      boProduct,
      Nil,
      bODelivery,
      Nil,
      cartItem.externalCode,
      uuid,
      cartItem.productUrl,
      new DateTime)
  }

  def initBOShopCart(shopCart : StoreShopCartWithPrices, rate: Currency, cartItems: List[BOCartItem]) : BOShopCart = {
    BOShopCart(UUID.randomUUID().toString,
                shopCart.shopTransactionUuid,
      shopCart.shopId,
      shopCart.totalFinalPrice,
      rate.code,
      rate.rate,
      cartItems)
  }

  def initBOCart(companyFk: Long, uuid: String, buyer: String, cart : StoreCartWithPrices, shippingAddress: AccountAddress, shopCarts: List[BOShopCart]) : BOCart = {
    BOCart(uuid,
      cart.transactionUuid,
      buyer,
      DateTime.now,
      cart.totalFinalPrice,
      TransactionStatus.PENDING,
      cart.rate.get.code,
      cart.rate.get.rate,
      shippingAddress,
      shopCarts,
      None,
      companyFk,
      new Date,
      new Date)
  }
}

case class BOCartSql(id: Long,
                     uuid: String,
                     transactionUuid: Option[String],
                     companyFk: Long,
                     extra: String,
                     dateCreated: Date,
                     lastUpdated: Date)

case class BOCart(uuid: String,
                  transactionUuid: Option[String],
                  buyer: String,
                  @JsonSerialize(using = classOf[JodaDateTimeSerializer])
                  @JsonDeserialize(using = classOf[JodaDateTimeDeserializer])
                  xdate: DateTime,
                  price: Long,
                  @JsonScalaEnumeration(classOf[TransactionStatusRef]) status: TransactionStatus.TransactionStatus,
                  currencyCode: String,
                  currencyRate: Double,
                  shippingAddress: AccountAddress,
                  shopCarts: List[BOShopCart],
                  externalOrderId: Option[String] = None,
                  companyFk: Long,
                  var lastUpdated: Date,
                  var dateCreated: Date)

case class BOShopCart(uuid: String,
                      transactionUuid: Option[String],
                      shopId: String,
                      price: Long,
                      currencyCode: String,
                      currencyRate: Double,
                      cartItems: List[BOCartItem])

case class BOCartItem(price: Long,
                      tax: Double,
                      endPrice: Long,
                      totalPrice: Long,
                      totalEndPrice: Long,
                      hidden: Boolean,
                      quantity: Int,
                      @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                      @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                      startDate: Option[DateTime],
                      @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                      @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                      endDate: Option[DateTime],
                      sku: Mogobiz.Sku,
                      principal: BOProduct,
                      secondary: List[BOProduct],
                      bODelivery: Option[BODelivery],
                      bOReturnedItems: List[BOReturnedItem],
                      externalCode : Option[ExternalCode],
                      uuid: String,
                      url: String,
                      @JsonSerialize(using = classOf[JodaDateTimeSerializer])
                      @JsonDeserialize(using = classOf[JodaDateTimeDeserializer])
                      dateCreated: DateTime)

case class BODelivery(@JsonScalaEnumeration(classOf[DeliveryStatusRef])
                      status: DeliveryStatus.DeliveryStatus,
                      tracking: Option[String] = None,
                      extra: String,
                      uuid: String)

case class BOReturnedItem(quantity: Int,
                          refunded: Long,
                          totalRefunded: Long,
                          @JsonScalaEnumeration(classOf[ReturnedItemStatusRef])
                          status: ReturnedItemStatus.ReturnedItemStatus,
                          boReturns: List[BOReturn],
                          uuid: String)

case class BOReturn(motivation: Option[String],
                    @JsonScalaEnumeration(classOf[ReturnStatusRef])
                    status: ReturnStatus.ReturnStatus,
                    uuid: String,
                    dateCreated: DateTime)

case class BOProduct(acquittement: Boolean,
                     principal: Boolean,
                     price: Long,
                     product: Product,
                     registeredCartItems: List[BORegisteredCartItem],
                     uuid: String)

case class BORegisteredCartItem(age: Int,
                                quantity: Int,
                                price: Long,
                                ticketType: String,
                                firstname: Option[String],
                                lastname: Option[String],
                                email: String,
                                phone: Option[String],
                                @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                                @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                                birthdate: Option[DateTime],
                                shortCode: Option[String],
                                qrcode: Option[String],
                                qrcodeContent: Option[String],
                                @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                                @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                                startDate: Option[DateTime],
                                @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])
                                @JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer])
                                endDate: Option[DateTime],
                                uuid: String)

object BOCartDao extends SQLSyntaxSupport[BOCartSql] with BoService {

  override val tableName = "b_o_cart"

  def create(boCart: BOCart)(implicit session: DBSession): BOCartSql = {
    val newBoCart = BOCartSql(newId(),
      boCart.uuid,
      boCart.transactionUuid,
      boCart.companyFk,
      JacksonConverter.serialize(boCart),
      new Date,
      new Date)

    applyUpdate {
      insert
        .into(BOCartDao)
        .namedValues(
          BOCartDao.column.id               -> newBoCart.id,
          BOCartDao.column.uuid             -> newBoCart.uuid,
          BOCartDao.column.transactionUuid  -> newBoCart.transactionUuid,
          BOCartDao.column.companyFk        -> newBoCart.companyFk,
          BOCartDao.column.extra            -> newBoCart.extra,
          BOCartDao.column.dateCreated      -> newBoCart.dateCreated,
          BOCartDao.column.lastUpdated      -> newBoCart.lastUpdated
        )
    }

    newBoCart
  }

  def update(boCart: BOCart)(implicit session: DBSession): Int = {
    applyUpdate {
      QueryDSL
        .update(BOCartDao)
        .set(
          BOCartDao.column.extra       -> JacksonConverter.serialize(boCart),
          BOCartDao.column.lastUpdated -> new Date
        )
        .where
        .eq(BOTransactionDAO.column.uuid, boCart.uuid)
    }
  }

  def delete(boCart: BOCart)(implicit session: DBSession) = {
    withSQL {
      deleteFrom(BOCartDao).where.eq(BOCartDao.column.uuid, boCart.uuid)
    }.update.apply()
  }
}
