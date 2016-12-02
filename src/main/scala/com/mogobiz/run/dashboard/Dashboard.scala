/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.dashboard

import java.text.SimpleDateFormat
import java.util.{Calendar, Date}

import com.fasterxml.jackson.databind.annotation.{JsonDeserialize, JsonSerialize}
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import com.mogobiz.es.EsClient
import com.mogobiz.pay.config.MogopayHandlers.handlers._
import com.mogobiz.run.config.Settings
import com.mogobiz.pay.model.Account
import com.mogobiz.run.handlers.BOCart
import com.mogobiz.run.json.{JodaDateTimeDeserializer, JodaDateTimeOptionDeserializer, JodaDateTimeOptionSerializer, JodaDateTimeSerializer}
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
    import EsClient.secureRequest
    Await.result(EsClient().execute(secureRequest(request)), Duration.Inf).isAcknowledged
  }

  case class LinearCart(itemCode: String,
                        price: Long,
                        tax: Double,
                        endPrice: Long,
                        totalPrice: Long,
                        totalEndPrice: Long,
                        isItemHidden: Boolean,
                        quantity: Int,
                        @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer]) @JsonDeserialize(
                            using = classOf[JodaDateTimeOptionDeserializer]) itemStartDate: Option[DateTime],
                        @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer]) @JsonDeserialize(
                            using = classOf[JodaDateTimeOptionDeserializer]) itemEndDate: Option[DateTime],
                        /* SKU */
                        skuId: Long,
                        skuUUID: String,
                        skuSku: String,
                        skuName: String,
                        skuPrice: Long,
                        skuSalePrice: Long,
                        skuMinOrder: Long = 0,
                        skuMaxOrder: Long = 0,
                        @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer]) @JsonDeserialize(
                            using = classOf[JodaDateTimeOptionDeserializer]) skuAvailabilityDate: Option[DateTime] =
                          None,
                        @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer]) @JsonDeserialize(
                            using = classOf[JodaDateTimeOptionDeserializer]) skuStartDate: Option[DateTime] = None,
                        @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer]) @JsonDeserialize(
                            using = classOf[JodaDateTimeOptionDeserializer]) skuStopDate: Option[DateTime] = None,
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
                        @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer]) @JsonDeserialize(
                            using = classOf[JodaDateTimeOptionDeserializer]) productStartDate: Option[DateTime],
                        @JsonSerialize(using = classOf[JodaDateTimeOptionSerializer]) @JsonDeserialize(
                            using = classOf[JodaDateTimeOptionDeserializer]) productStopDate: Option[DateTime],
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
                        @JsonSerialize(using = classOf[JodaDateTimeSerializer]) @JsonDeserialize(
                            using = classOf[JodaDateTimeDeserializer]) cartXDate: DateTime,
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
    val targetIndex    = indexName(esDashboard(storeCode))
    if (!EsClient.exists(targetIndex))
      createIndexWithMapping(targetIndex)
    linearizedCart.foreach(cart => EsClient.index(targetIndex, cart, refresh = false))
  }

  private def linearize(cart: BOCart, buyerUUID: String): Seq[LinearCart] = cart.shopCarts.flatMap { shopCart =>
    shopCart.cartItems.map { item =>
      val buyer: Account = accountHandler.find(buyerUUID).get

      LinearCart(
        itemCode = item.uuid,
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
        deliveryExtra = item.bODelivery.map(_.extra),
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
  }

  val esIndexRotate = Settings.Dashboard.Rotate

  private def indexName(esIndex: String): String = {
    def dateFormat(date: Calendar, dateFormat: String) = {
      val dtf = DateTimeFormat.forPattern(dateFormat)
      val str = dtf.print(date.getTimeInMillis)
      str
    }
    val format = esIndexRotate match {
      case "DAILY"   => Some("yyyy-MM-dd")
      case "MONTHLY" => Some("yyyy-MM")
      case "YEARLY"  => Some("yyyy")
      case _         => None
    }
    val suffix = format map ("_" + dateFormat(Calendar.getInstance(), _)) getOrElse ""
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
