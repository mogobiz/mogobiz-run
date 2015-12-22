/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.dashboard

import java.text.SimpleDateFormat
import java.util.{ Calendar, Date }

import com.fasterxml.jackson.databind.annotation.{ JsonDeserialize, JsonSerialize }
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import com.mogobiz.es.EsClient
import com.mogobiz.pay.config.MogopayHandlers.handlers._
import com.mogobiz.run.config.Settings
import com.mogobiz.pay.model.Mogopay.Account
import com.mogobiz.run.json.{ JodaDateTimeDeserializer, JodaDateTimeOptionDeserializer, JodaDateTimeOptionSerializer, JodaDateTimeSerializer }
import com.mogobiz.run.model.ES.{ BOCart, BOCartItem, BOProduct }
import com.mogobiz.run.model.Mogobiz
import com.mogobiz.run.model.Mogobiz.TransactionStatus.TransactionStatus
import com.mogobiz.run.model.Mogobiz._
import com.sksamuel.elastic4s.CreateIndexDefinition
import com.sksamuel.elastic4s.mappings.FieldType._
import org.joda.time.DateTime
import com.sksamuel.elastic4s.ElasticDsl._
import org.joda.time.format.DateTimeFormat

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Dashboard {

  def indexExists(indexName: String): Boolean = {
    EsClient.exists(indexName)
  }

  def createIndexWithMapping(indexName: String) = {
    val request: CreateIndexDefinition = create index s"$indexName" mappings (
      "LinearCart" as (
        "boProductAcquittement" typed BooleanType,
        "boProductPrice" typed LongType,
        "boUUID" typed StringType index "not_analyzed",
        "buyerBirthdate" typed LongType,
        "buyerCivility" typed StringType index "not_analyzed",
        "buyerCountry" typed StringType index "not_analyzed",
        "buyerEmail" typed StringType index "not_analyzed",
        "buyerRoad" typed StringType index "not_analyzed",
        "buyerUUID" typed StringType index "not_analyzed",
        "buyerZipCode" typed StringType index "not_analyzed",
        "calendarType" typed StringType index "not_analyzed",
        "cartBuyer" typed StringType index "not_analyzed",
        "cartDateCreated" typed DateType,
        "cartLastUpdated" typed DateType,
        "cartStatus" typed StringType index "not_analyzed",
        "cartTotalPrice" typed LongType,
        "cartTransactionUUID" typed StringType index "not_analyzed",
        "deliveryStatus" typed StringType index "not_analyzed",
        "deliveryUUID" typed StringType index "not_analyzed",
        "deliveryExtra" typed StringType index "no",
        "cartUUID" typed StringType index "not_analyzed",
        "cartXDate" typed DateType,
        "currencyCode" typed StringType index "not_analyzed",
        "currencyRate" typed DoubleType,
        "dateCreated" typed DateType,
        "endPrice" typed LongType,
        "isItemHidden" typed BooleanType,
        "itemCode" typed StringType index "not_analyzed",
        "lastUpdated" typed DateType,
        "price" typed LongType,
        "productDateCreated" typed DateType,
        "productDownloadMaxDelay" typed LongType,
        "productDownloadMaxTimes" typed LongType,
        "productLastUpdated" typed LongType,
        "productName" typed StringType index "not_analyzed",
        "productCategoryPath" typed StringType index "not_analyzed",
        "productCategoryName" typed StringType index "not_analyzed",
        "productCategoryUUID" typed StringType index "not_analyzed",
        "productCategoryId" typed LongType,
        "productCategoryParentId" typed LongType,
        "productNbSales" typed LongType,
        "productStockDisplay" typed BooleanType,
        "productType" typed StringType index "not_analyzed",
        "productUUID" typed StringType index "not_analyzed",
        "quantity" typed LongType,
        "skuId" typed LongType,
        "skuMaxOrder" typed LongType,
        "skuMinOrder" typed LongType,
        "skuName" typed StringType index "not_analyzed",
        "skuNbSales" typed LongType,
        "skuPrice" typed LongType,
        "skuSalePrice" typed LongType,
        "skuSku" typed StringType index "not_analyzed",
        "skuUUID" typed StringType index "not_analyzed",
        "tax" typed DoubleType,
        "totalEndPrice" typed LongType,
        "totalPrice" typed LongType,
        "url" typed StringType index "not_analyzed",
        "uuid" typed StringType index "not_analyzed",
        "buyerGeoCoordinates" typed GeoPointType
      )
    )
    Await.result(EsClient().execute(request), Duration.Inf).isAcknowledged
  }

  case class LinearCart(itemCode: String,
    price: Long,
    tax: Double,
    endPrice: Long,
    totalPrice: Long,
    totalEndPrice: Long,
    isItemHidden: Boolean,
    quantity: Int,
    @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])@JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer]) itemStartDate: Option[DateTime],
    @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])@JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer]) itemEndDate: Option[DateTime],
    /* SKU */
    skuId: Long,
    skuUUID: String,
    skuSku: String,
    skuName: String,
    skuPrice: Long,
    skuSalePrice: Long,
    skuMinOrder: Long = 0,
    skuMaxOrder: Long = 0,
    @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])@JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer]) skuAvailabilityDate: Option[DateTime] = None,
    @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])@JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer]) skuStartDate: Option[DateTime] = None,
    @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])@JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer]) skuStopDate: Option[DateTime] = None,
    skuCoupons: List[InnerCoupon],
    skuNbSales: Long,
    /* End */
    // secondary: List[BOProduct],
    /* BOProduct */
    boProductAcquittement: Boolean,
    boProductPrice: Long,
    boUUID: String,
    // registeredCartItem: List[BORegisteredCartItem],
    /* End */
    /* BOProduct.Product */
    productUUID: String,
    productName: String,
    productType: String,
    calendarType: String,
    // productTaxRate: Option[TaxRate],
    // product: Option[Shipping],
    productStockDisplay: Boolean,
    @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])@JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer]) productStartDate: Option[DateTime],
    @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer])@JsonDeserialize(using = classOf[JodaDateTimeOptionDeserializer]) productStopDate: Option[DateTime],
    // productSKUs: List[Sk],
    // productIntraDayPeriods: Option[List[IntraDayPeriod]],
    // productPOI: Option[Poi],
    productNbSales: Long,
    productDownloadMaxTimes: Long,
    productDownloadMaxDelay: Long,
    productDateCreated: Date,
    productLastUpdated: Date,
    productCategoryPath: String,
    productCategoryName: String,
    productCategoryUUID: String,
    productCategoryId: Option[Long],
    productCategoryParentId: Option[Long],
    /* End */
    /* Delivery */
    deliveryStatus: Option[String],
    deliveryTracking: Option[String],
    deliveryExtra: Option[String],
    deliveryUUID: Option[String],
    /* End */
    // bOReturnedItems: List[BOReturnedItem],
    uuid: String,
    url: String,
    /* Cart */
    cartTransactionUUID: Option[String],
    cartBuyer: String,
    @JsonSerialize(using = classOf[JodaDateTimeSerializer])@JsonDeserialize(using = classOf[JodaDateTimeDeserializer]) cartXDate: DateTime,
    cartTotalPrice: Long, // previous name: price
    @JsonScalaEnumeration(classOf[TransactionStatusRef]) cartStatus: TransactionStatus,
    currencyCode: String,
    currencyRate: Double,
    cartDateCreated: Date,
    cartLastUpdated: Date,
    cartUUID: String,
    /* Buyer */
    buyerUUID: String,
    buyerEmail: String,
    buyerCivility: String,
    buyerBirthdate: Option[Date],
    buyerRoad: Option[String],
    buyerZipCode: Option[String],
    buyerCountry: Option[String],
    buyerGeoCoordinates: Option[String],
    /* End */
    var lastUpdated: Date = new Date,
    var dateCreated: Date = new Date)

  def indexCart(storeCode: String, cart: BOCart, buyerUUID: String) = {
    def esDashboard(store: String): String = s"${store}_dashboard"
    val linearizedCart = linearize(cart, buyerUUID)
    val targetIndex = indexName(esDashboard(storeCode))
    if (!EsClient.exists(targetIndex))
      createIndexWithMapping(targetIndex)
    linearizedCart.foreach(cart => EsClient.index(targetIndex, cart, refresh = false))
  }

  private def linearize(cart: BOCart, buyerUUID: String): Seq[LinearCart] = cart.cartItems.map { item =>
    val buyer: Account = accountHandler.find(buyerUUID).get

    LinearCart(
      itemCode = item.code,
      price = item.price,
      tax = item.tax,
      endPrice = item.endPrice,
      totalPrice = item.totalPrice,
      totalEndPrice = item.totalEndPrice,
      isItemHidden = item.hidden,
      quantity = item.quantity,
      itemStartDate = item.startDate,
      itemEndDate = item.endDate,

      skuId = item.sku.id,
      skuUUID = item.sku.uuid,
      skuSku = item.sku.sku,
      skuName = item.sku.name,
      skuPrice = item.sku.price,
      skuSalePrice = item.sku.salePrice,
      skuMinOrder = item.sku.minOrder,
      skuMaxOrder = item.sku.maxOrder,
      skuAvailabilityDate = item.sku.availabilityDate,
      skuStartDate = item.sku.startDate,
      skuStopDate = item.sku.stopDate,
      skuCoupons = item.sku.coupons,
      skuNbSales = item.sku.nbSales,

      boProductAcquittement = item.principal.acquittement,
      boProductPrice = item.principal.price,
      boUUID = item.principal.uuid,

      productUUID = item.principal.product.uuid,
      productName = item.principal.product.name,
      productType = item.principal.product.xtype.toString,
      calendarType = item.principal.product.calendarType.toString,
      productStockDisplay = item.principal.product.stockDisplay,
      productStartDate = item.principal.product.startDate,
      productStopDate = item.principal.product.stopDate,
      productNbSales = item.principal.product.nbSales,
      productDownloadMaxTimes = item.principal.product.downloadMaxTimes,
      productDownloadMaxDelay = item.principal.product.downloadMaxDelay,
      productDateCreated = item.principal.product.dateCreated,
      productLastUpdated = item.principal.product.lastUpdated,
      productCategoryPath = item.principal.product.category.path,
      productCategoryName = item.principal.product.category.name,
      productCategoryUUID = item.principal.product.category.uuid,
      productCategoryId = Some(item.principal.product.category.id),
      productCategoryParentId = item.principal.product.category.parentId,

      deliveryStatus = item.bODelivery.map(_.status.toString),
      deliveryTracking = item.bODelivery.flatMap(_.tracking),
      deliveryExtra = item.bODelivery.flatMap(_.extra),
      deliveryUUID = item.bODelivery.map(_.uuid),
      uuid = item.uuid,
      url = item.url,
      cartTransactionUUID = cart.transactionUuid,
      cartBuyer = cart.buyer,
      cartXDate = cart.xdate,
      cartTotalPrice = cart.price,
      cartStatus = cart.status,
      currencyCode = cart.currencyCode,
      currencyRate = cart.currencyRate,
      cartDateCreated = cart.dateCreated,
      cartLastUpdated = cart.lastUpdated,
      cartUUID = cart.uuid,

      buyerUUID = buyer.uuid,
      buyerEmail = buyer.email,
      buyerCivility = buyer.civility.map(_.toString).getOrElse("UNKNOWN"),
      buyerBirthdate = buyer.birthDate,
      buyerRoad = buyer.address.map(_.road),
      buyerZipCode = buyer.address.map(_.zipCode.getOrElse("")),
      buyerCountry = buyer.address.flatMap(_.country),
      buyerGeoCoordinates = buyer.address.flatMap(_.geoCoordinates)
    )
  }

  def main(args: Array[String]) = {

    val sku00: Sku = Sku(id = 0L, uuid = "00000000-0000-0000-0000-000000000000", sku = "sku00", name = "sku00", price = 500, salePrice = 500, coupons = Nil, nbSales = 0)
    val sku01: Sku = Sku(id = 1L, uuid = "11111111-1111-1111-1111-111111111111", sku = "sku11", name = "sku11", price = 2000, salePrice = 2000, coupons = Nil, nbSales = 0)

    val productA: BOProduct = BOProduct(
      acquittement = true, principal = true, price = 500, registeredCartItem = Nil, uuid = "00000000-0000-0000-0000-000000000000",
      product = Product(id = 0L, uuid = "00000000-0000-0000-0000-000000000000", name = "A", ProductType.PRODUCT, ProductCalendar.NO_DATE, None, None, true,
        None, None, None, Nil, None, None, None, 0, 0, 0, null, new Date, new Date)
    )
    val productB: BOProduct = BOProduct(
      acquittement = true, principal = true, price = 2000, registeredCartItem = Nil, uuid = "00000000-0000-0000-0000-000000000000",
      product = Product(id = 1L, uuid = "11111111-1111-1111-1111-111111111111", name = "B", ProductType.PRODUCT, ProductCalendar.NO_DATE, None, None, true,
        None, None, None, Nil, None, None, None, 0, 0, 0, null, new Date, new Date)
    )

    val item00 = BOCartItem( // 1 A
      code = "00", price = 500, tax = 0.0, endPrice = 500, totalPrice = 1000, totalEndPrice = 1000, hidden = false,
      quantity = 2, startDate = None, endDate = None, sku = sku00, secondary = Nil, principal = productA,
      bOReturnedItems = Nil, bODelivery = None, uuid = "00000000-0000-0000-0000-000000000000", url = "http://url00/")
    val item01 = BOCartItem( // 2 B
      code = "01", price = 4000, tax = 0.0, endPrice = 4000, totalPrice = 4000, totalEndPrice = 4000, hidden = false,
      quantity = 2, startDate = None, endDate = None, sku = sku01, secondary = Nil, principal = productB,
      bOReturnedItems = Nil, bODelivery = None, uuid = "11111111-1111-1111-1111-111111111111", url = "http://url01/")
    val item10 = BOCartItem( // 1 A
      code = "10", price = 500, tax = 0.0, endPrice = 500, totalPrice = 500, totalEndPrice = 500, hidden = false,
      quantity = 1, startDate = None, endDate = None, sku = sku00, secondary = Nil, principal = productA,
      bOReturnedItems = Nil, bODelivery = None, uuid = "22222222-2222-2222-2222-222222222222", url = "http://url10/")
    val item20 = BOCartItem( // 2 B
      code = "01", price = 4000, tax = 0.0, endPrice = 4000, totalPrice = 4000, totalEndPrice = 4000, hidden = false,
      quantity = 2, startDate = None, endDate = None, sku = sku01, secondary = Nil, principal = productB,
      bOReturnedItems = Nil, bODelivery = None, uuid = "33333333-3333-3333-3333-333333333333", url = "http://url20/")

    val c0 = linearize(BOCart(transactionUuid = Some("00000000-0000-0000-0000-000000000000"),
      buyer = "buyer0", xdate = DateTime.parse("2015-04-01T12:00"), price = 45000, status = Mogobiz.TransactionStatus.COMPLETE,
      currencyCode = "EUR", currencyRate = 1.0, dateCreated = new Date, lastUpdated = new Date,
      uuid = "00000000-0000-0000-0000-000000000000",
      cartItems = List(item00, item01)
    ), "eeeeeeee-29b4-465a-aedd-1784ee6e3929")
    val c1 = linearize(BOCart(transactionUuid = Some("11111111-1111-1111-1111-111111111111"),
      buyer = "buyer1", xdate = DateTime.parse("2015-04-01T14:00"), price = 500, status = Mogobiz.TransactionStatus.COMPLETE,
      currencyCode = "EUR", currencyRate = 1.0, dateCreated = new Date, lastUpdated = new Date,
      uuid = "11111111-1111-1111-1111-111111111111",
      cartItems = List(item10)
    ), "ffffffff-29b4-465a-aedd-1784ee6e3929")
    val c2 = linearize(BOCart(transactionUuid = Some("22222222-2222-2222-2222-222222222222"),
      buyer = "buyer2", xdate = DateTime.parse("2015-04-03T12:00"), price = 4000, status = Mogobiz.TransactionStatus.COMPLETE,
      currencyCode = "EUR", currencyRate = 1.0, dateCreated = new Date, lastUpdated = new Date,
      uuid = "22222222-2222-2222-2222-222222222222",
      cartItems = List(item20)
    ), "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")

    c0.foreach(cart => EsClient.index("mogodashboard", cart, refresh = true))
    c1.foreach(cart => EsClient.index("mogodashboard", cart, refresh = true))
    c2.foreach(cart => EsClient.index("mogodashboard", cart, refresh = true))
  }

  val esIndexRotate = Settings.Dashboard.Rotate

  private def indexName(esIndex: String): String = {
    def dateFormat(date: Calendar, dateFormat: String) = {
      val dtf = DateTimeFormat.forPattern(dateFormat)
      val str = dtf.print(date.getTimeInMillis)
      str
    }
    val format = esIndexRotate match {
      case "DAILY" => Some("yyyy-MM-dd")
      case "MONTHLY" => Some("yyyy-MM")
      case "YEARLY" => Some("yyyy")
      case _ => None
    }
    val suffix = format map ("_" + dateFormat(Calendar.getInstance(), _)) getOrElse ("")
    esIndex + suffix
  }
}

object X extends App {
  val dtf = DateTimeFormat.forPattern("yyyy-MM-dd HHmmss")
  val str = dtf.print(Calendar.getInstance().getTimeInMillis)
  println(str)

  println(new SimpleDateFormat("yyyy-MM-dd  HHmmss").format(new Date))

  println(DateTimeFormat.forPattern("yyyy-MM-dd  HHmmss").print(new Date().getTime))
}