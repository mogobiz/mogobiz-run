package com.mogobiz.run.externals.mirakl

import java.util.{Date, Locale, UUID}

import akka.actor.ActorSystem
import akka.event.Logging
import com.mogobiz.pay.model.Mogopay.IdGenerator
import com.mogobiz.run.config.Settings
import com.mogobiz.run.externals.mirakl.Mirakl._
import com.mogobiz.run.externals.mirakl.Mirakl.PaymentStatus.PaymentStatus
import com.mogobiz.run.externals.mirakl.Mirakl.PaymentWorkflow.PaymentWorkflow
import com.mogobiz.run.externals.mirakl.MiraklApi.ApiCode.ApiCode
import com.mogobiz.run.externals.mirakl.exception._
import com.mogobiz.run.utils.Paging
import com.mogobiz.system.ActorSystemLocator
import org.json4s.native.JsonParser
import org.slf4j.LoggerFactory
import spray.client.pipelining._
import spray.http.{HttpRequest, _}

import scala.collection.immutable.HashMap
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

object MiraklClient {
  import org.json4s.JsonAST.JString
  import org.json4s.jackson.JsonMethods._
  import Mirakl.JsonApiTemplate._

  val TIMEOUT = 60.seconds

  val frontApiKey = Settings.Externals.Mirakl.frontApiKey
  val url = Settings.Externals.Mirakl.url

  implicit val system = ActorSystemLocator.apply()
  import system.dispatcher
  val log = Logging(system, getClass)

  /*
  val pipeline: HttpRequest => Future[OrderConfirmation] = (
  addHeader("X-My-Special-Header", "fancy-value")
  ~> addCredentials(BasicHttpCredentials("bob", "secret"))
  ~> encode(Gzip)
  ~> sendReceive
  ~> decode(Deflate)
  ~> unmarshal[OrderConfirmation]
)
 */

  private def defaultHeaders() = {
    (addHeader("Accept", "application/json") ~> addHeader("Authorization", frontApiKey))
  }

  private def defaultPipeline: HttpRequest => Future[String] = defaultHeaders ~> sendReceive ~> unmarshal[String]
  private def basicPipeline: HttpRequest => Future[HttpResponse] = defaultHeaders ~> sendReceive //~> unmarshal[HttpResponse]
  //  private def pipeline[T]: HttpRequest => Future[T] = defaultHeaders ~> sendReceive ~> unmarshal[T]
  /**
   * For checking purpose, not use in the app
   */
  def getAllShops() = {
    val pipeline = defaultPipeline

    val responseFuture = pipeline {
      Get(MiraklApi.listShops())
    }

    Await.result(responseFuture, TIMEOUT)
    /*
    responseFuture onComplete {
      case Success(s) =>
        log.info("Success: {}", s)

      case Failure(error) =>
        log.error(error, "Couldn't get elevation")
    }
    */
  }

  def getShopOffers(shopId: Long, max: Option[Int] = None, offset: Option[Int] = None) = {
    val pipeline = defaultPipeline

    val responseFuture = pipeline {
      Get(MiraklApi.shopOffersUrl(shopId, max, offset))
    }
    Await.result(responseFuture, TIMEOUT)
  }

  def getProductsOffers(productIds: List[String]) = {
    val responseFuture = defaultPipeline {
      Get(MiraklApi.productsOffers(productIds))
    }

    Await.result(responseFuture, TIMEOUT)
    /*
    responseFuture onComplete {
      case Success(s) =>
        log.info("Success: {}", s)

      case Failure(error) =>
        log.error(error, "Couldn't get elevation")
    }*/
  }

  /**
   * Renvoie l'orderId de la commande créée
   */
  def createOrder(order: OrderBean): String = {
    import com.mogobiz.run.implicits.JsonSupport._
    //    val pipe : HttpRequest => Future[OrderCreatedDTO] = defaultHeaders ~> sendReceive ~> unmarshal[OrderCreatedDTO]
    val pipe = defaultPipeline

    val responseFuture = pipe { //pipeline {
      Post(MiraklApi.createOrderUrl, order)
    }
    val response = Await.result(responseFuture, TIMEOUT)
    println(response)
    log.debug("order response = {}", response)
    val json = parse(response)
    (json \ "orders" \ "order_id") match {
      case (JString(orderId)) => orderId
      case _ => throw new MiraklCreateOrderException("Could not retrieve orderId")
    }
  }

  def listOrders() = {
    val responseFuture = defaultPipeline {
      Get(MiraklApi.listOrdersUrl)
    }
    Await.result(responseFuture, TIMEOUT)

  }

  /**
   * return shipping fees response as raw json string
   */
  def getShippingFeesRaw(shippingZoneCode: String, offerIdsAndQuantity: List[(Long, Int)]) = {

    val responseFuture = defaultPipeline {
      Get(MiraklApi.getShippingFeesUrl(shippingZoneCode, offerIdsAndQuantity))
    }

    /*
    responseFuture onComplete {
      case Success(response) =>
        val shops = (response \ "shops")
        //log.info("Success: {}", s)

      case Failure(error) =>
        log.error(error, "Couldn't get elevation")
    }*/

    Await.result(responseFuture, TIMEOUT)
  }

  def getShippingFeesByOffersAndShippingType(shippingZoneCode: String, offerIdsAndQuantity: List[(Long, Int)]): List[ShippingFeeFlattenOrderLine] = {
    import org.json4s.{ DefaultFormats, Formats }
    implicit def json4sFormats: Formats = DefaultFormats

    val response = getShippingFeesRaw(shippingZoneCode, offerIdsAndQuantity)
    //println(response)
    val jsonFees = JsonParser.parse(response)

    //      jsonFees \ "offers_not_found" must be_==(JArray(_))
    val fees = (jsonFees \ "shops").children.map { shop =>
      val shopId = (shop \ "shop_id").extract[Long]
      val shopCurrencyIsoCode = (shop \ "currency_iso_code").extract[String]
      (shop \ "offers").children.map { offer =>
        val offerId = (offer \ "offer_id").extract[Long]
        val offerQty = (offer \ "line_quantity").extract[Integer]
        //val linePrice = (offer \ "line_price").extract[BigDecimal]
        val offerPrice = (offer \ "offer_price").extract[BigDecimal]
        //val selectedShippingPrice = (offer \ "line_shipping_price").extract[Double]
        //val selectedShippingTypeCode = (offer \ "selected_shipping_type" \ "code").extract[String]

        (offer \ "shipping_types").children.map { shippingType =>
          val shippingTypeCode = (shippingType \ "code").extract[String]
          val shippingTypeLabel = (shippingType \ "label").extract[String]

          val lineTotalPrice = (shippingType \ "line_total_price").extract[BigDecimal]

          ShippingFeeFlattenOrderLine(shopId, shopCurrencyIsoCode,
            offerId, offerPrice, offerQty,
            shippingTypeCode, shippingTypeLabel, lineTotalPrice
          )
        }
      }
    }

//    println(fees)
//    println(fees.flatten.flatten)
    fees.flatten.flatten
  }

  def validateOrder(amount: Long, currencyCode: String, orderId: String, customerId: String, transactionDate: Option[Date], transactionNumber: Option[String]): Boolean = {
    import com.mogobiz.run.implicits.JsonSupport._
    val pipeline = basicPipeline

    //val body = TPL_PA01(amount, currencyCode, orderId, customerId, PaymentStatus.OK)
    val body = OrderPaymentsDto(Array(OrderPayment(orderId, customerId, PaymentStatus.OK, Some(amount), Some(currencyCode), transactionDate, transactionNumber)))
    val responseFuture = pipeline {
      Put(MiraklApi.paymentDebitUrl, body)
    }
    val response = Await.result(responseFuture, TIMEOUT)
    response.status == StatusCodes.NoContent
  }

  def cancelOrder(orderId: String, customerId: String, amount: Option[BigDecimal] = None, currencyCode: Option[String] = None): Boolean = {
    import com.mogobiz.run.implicits.JsonSupport._
    val pipeline = basicPipeline

    //val body = TPL_PA01(amount, currencyCode, orderId, customerId, PaymentStatus.REFUSED)
    val body = OrderPaymentsDto(Array(OrderPayment(orderId, customerId, PaymentStatus.REFUSED, amount, currencyCode)))
    val responseFuture = pipeline {
      Put(MiraklApi.paymentDebitUrl, body)
    }
    val response = Await.result(responseFuture, TIMEOUT)
    response.status == StatusCodes.NoContent
  }

  def refund(amount: Long, currencyCode: String, refundId: String, paymentStatus: PaymentStatus) = {
    val pipeline = basicPipeline

    val body = TPL_PA02(amount, currencyCode, refundId, paymentStatus)
    val responseFuture = pipeline {
      Put(MiraklApi.paymentRefundUrl, body)
    }
    val response = Await.result(responseFuture, TIMEOUT)
    response.status == StatusCodes.NoContent
  }
}

object Mirakl {

  object PaymentStatus extends Enumeration {
    type PaymentStatus = Value

    val OK = Value("OK")
    val REFUSED = Value("REFUSED")
  }

  object PaymentWorkflow extends Enumeration {
    type PaymentWorkflow = Value

    val PAY_ON_ACCEPTANCE = Value("PAY_ON_ACCEPTANCE")
    val PAY_ON_DELIVERY = Value("PAY_ON_DELIVERY")
  }

  case class OfferNotShippable(errorCode: String, offerId: Long)
  case class Channel(code: String, label: String)
  case class OrderLine(
    canOpenIncident: Boolean,
    canRefund: Boolean //TODO ,
    //TODO                        cancelations
    )
  case class OrderCreated(
    acceptanceDecisionDate: Date,
    canCancel: Boolean,
    canEvaluate: Boolean,
    channel: Option[Channel],
    commercialId: String,
    createdDate: Date,
    currencyIsoCode: String,
    customer: Customer,
    customerDebitedDate: Option[Date],
    hasCustomerMessage: Boolean,
    hasIncident: Boolean,
    hasInvoice: Boolean,
    imprintNumber: String,
    lastUpdatedDate: Date,
    leadtimeToShip: Integer,
    orderAdditionalFields: Array[OrderAdditionalField],
    orderId: String,
    orderLines: Array[OrderLine] //, TODO suite ?
    )
  case class OrderCreatedDTO(
    offersNotShippable: Array[OfferNotShippable],
    orders: Array[OrderCreated] //, TODO suite ?
    )

  case class OrderBean(
      channel_code: Option[String],
      commercial_id: String,
      customer: Customer,
      offers: Array[Offer],
      order_additional_fields: Array[OrderAdditionalField],
      payment_info: Option[PaymentInfo],
      payment_workflow: Option[String],
      scored: Boolean,
      shipping_zone_code: String) {

    def this(commercialId: String, customer: Customer, offers: Array[Offer], shippingZoneCode: String) {
      this(None, commercialId, customer, offers, Array(), None, Some(PaymentWorkflow.PAY_ON_ACCEPTANCE.toString), false, shippingZoneCode)
    }
  }

  case class PaymentInfo(imprint_number: Option[String], payment_type: Option[String])

  case class OfferLineAdditionalField(code: String, value: String)
  case class OrderAdditionalField(code: String, `type`: Option[String], value: String)
  case class Offer(
    currency_iso_code: String,
    leadtime_to_ship: Option[Integer] = None,
    offer_id: Long,
    offer_price: BigDecimal,
    order_line_additional_fields: Array[OfferLineAdditionalField] = Array(),
    order_line_id: Option[String], // should be unique
    price: BigDecimal, // = offer_price * quantity
    quantity: Integer,
    shipping_price: BigDecimal,
    shipping_type_code: String,
    shipping_taxes: Array[ShippingTax] = Array(),
    taxes: Array[Tax] = Array())

  case class Tax(amount: BigDecimal, code: String)
  case class ShippingTax(amount: BigDecimal, code: String)
  case class Address(city: String, civility: Option[String], company: Option[String], country: String, country_iso_code: String, firstname: Option[String], lastname: String, phone: Option[String], phone_secondary: Option[String], state: Option[String], street_1: String, street_2: Option[String], zip_code: Option[String])
  case class ShippingAddress(city: String, civility: Option[String], company: Option[String], country: String, country_iso_code: String, firstname: Option[String], lastname: String, phone: Option[String], phone_secondary: Option[String], state: Option[String], street_1: String, street_2: Option[String], zip_code: Option[String], additional_info: Option[String] = None, internal_additional_info: Option[String] = None)

  case class Customer(customer_id: String, civility: Option[String], firstname: String, lastname: String, email: String, locale: Option[String], billing_address: Address, shipping_address: ShippingAddress)

  case class OrderPayment(order_id: String, customer_id: String, payment_status: PaymentStatus, amount: Option[BigDecimal] = None, currency_iso_code: Option[String] = None, transaction_date: Option[Date] = None, transaction_number: Option[String] = None)
  case class OrderPaymentsDto(orders: Array[OrderPayment])

  case class ShippingFeeFlattenOrderLine(
    shopId: Long, shopCurrencyIsoCode: String,
    offerId: Long, offerPrice: BigDecimal, offerQty: Integer,
    shippingTypeCode: String, shippingTypeLabel: String, totalPrice: BigDecimal)

  object JsonApiTemplate {

    def TPL_PA01(amount: Long, currencyCode: String, orderId: String, customerId: String, paymentStatus: PaymentStatus) =
      s"""
       | {
       |  "orders": [{
       |    "amount": $amount,
       |    "currency_iso_code": "$currencyCode",
       |    "customer_id": "$customerId",
       |    "order_id": "$orderId",
       |    "payment_status": "$paymentStatus"
       |  }]
       | }
    """.stripMargin

    def TPL_PA02(amount: Long, currencyCode: String, refundId: String, paymentStatus: PaymentStatus) =
      s"""
      | {
      |  "refunds": [{
      |    "amount": $amount,
      |    "currency_iso_code": "$currencyCode",
      |    "payment_status": "$paymentStatus",
      |    "refund_id": $refundId
      |  }]
      | }
    """.stripMargin
  }
}

//https://developer.mirakl.net/api/front/current/index.html#pagination-sort
object MiraklApi {

  val log = LoggerFactory.getLogger(getClass)

  object ApiCode extends Enumeration {
    type ApiCode = Value

    val S03 = Value("S03")
    val S04 = Value("S04")
    val S20 = Value("S20")
    val SH21 = Value("SH21")
    val SH01 = Value("SH01")
    val SH02 = Value("SH02")
    val OR01 = Value("OR01")
    val OR11 = Value("OR11")
    val P11 = Value("P11")
    val PA01 = Value("PA01")
    val PA02 = Value("PA02")
  }

  val apiEndpoints = HashMap[ApiCode, String](
    (ApiCode.S03 -> "/api/shops/SHOP_ID/evaluations"),
    (ApiCode.S04 -> "/api/shops/SHOP_ID/offers"),
    (ApiCode.S20 -> "/api/shops"),
    (ApiCode.P11 -> "/api/products/offers?product_ids=PRODUCT_SKU"), //&offer_state_codes=11
    (ApiCode.SH21 -> "/api/shipping/carriers"),
    (ApiCode.SH01 -> "/api/shipping/fees?shipping_zone_code=SHIP_ZONE_CODE&offers=OFFER_IDS_AND_QUANTITY"),
    (ApiCode.SH02 -> "/api/shipping/rates"),
    (ApiCode.OR01 -> "/api/orders"),
    (ApiCode.OR11 -> "/api/orders"),
    (ApiCode.PA01 -> "/api/payment/debit"),
    (ApiCode.PA02 -> "/api/payment/refund")

  )

  private def getApiEndpointUrl(apiCode: ApiCode): String = {
    MiraklClient.url + apiEndpoints(apiCode)
  }

  def listShops() = MiraklApi.getApiEndpointUrl(ApiCode.S20)

  def getShopEvaluations(shopId: Long, max: Option[Int], offset: Option[Int]) = {
    val preparedUrl = MiraklApi.getApiEndpointUrl(ApiCode.S03)
    preparedUrl.replaceFirst("SHOP_ID", shopId.toString)
  }

  def shopOffersUrl(shopId: Long, max: Option[Int], offset: Option[Int]) = {

    val maxParams = max.map { mv => s"?max=$mv" }.getOrElse("?max=10")
    val offsetParams = offset.map { ov => s"&offset=$ov" }.getOrElse("&offset=0")

    val preparedUrl = MiraklApi.getApiEndpointUrl(ApiCode.S04) + maxParams + offsetParams
    val returnedUrl = preparedUrl.replaceFirst("SHOP_ID", shopId.toString)
    log.debug("shopOffersUrl returnedUrl={}", returnedUrl)
    returnedUrl
  }

  def productsOffers(productIds: List[String]) = {
    val preparedUrl = MiraklApi.getApiEndpointUrl(ApiCode.P11)
    preparedUrl.replaceFirst("PRODUCT_SKU", productIds.mkString(","))
  }

  def listCarriers = MiraklApi.getApiEndpointUrl(ApiCode.SH21)

  def getShippingFeesUrl(shippingZoneCode: String, offerIdsAndQuantity: List[(Long, Int)]) = {
    val preparedUrl = MiraklApi.getApiEndpointUrl(ApiCode.SH01)
    //    {{URL}}/api/shipping/fees?shipping_zone_code=FR1&offers=2040|1
    //    (ApiCode.SH01 -> "/api/shipping/fees?shipping_zone_code=SHIP_ZONE_CODE&offers=OFFER_IDS_AND_QUANTITY"),
    val params: List[String] = offerIdsAndQuantity.map({ case (id, qty) => (id.toString + "|" + qty.toString) })
    preparedUrl.replaceFirst("SHIP_ZONE_CODE", shippingZoneCode).replaceFirst("OFFER_IDS_AND_QUANTITY", params.mkString(","))
  }

  /**
   * https://developer.mirakl.net/api/front/current/api/OR01.html
   */
  def createOrderUrl = MiraklApi.getApiEndpointUrl(ApiCode.OR01)
  def listOrdersUrl = MiraklApi.getApiEndpointUrl(ApiCode.OR11)

  def paymentDebitUrl = MiraklApi.getApiEndpointUrl(ApiCode.PA01)
  def paymentRefundUrl = MiraklApi.getApiEndpointUrl(ApiCode.PA02)

}

object MiraklClientMain extends App {
  //  MiraklClient.getAllShops()

  val address = Address(city = "PARIS", civility = Some("mr"), company = Some("MonsterInc"), country = "FRANCE", country_iso_code = "FR", firstname = Some("Pierre"), lastname = "DUPONT", phone = Some("0102030405"), phone_secondary = Some("0612345678"), state = None, street_1 = "Avenue de la République", street_2 = Some("apt 123"), zip_code = Some("75001"))
  val shippingAddress = ShippingAddress(city = "PARIS", civility = Some("mr"), company = Some("MonsterInc"), country = "FRANCE", country_iso_code = "FR", firstname = Some("Pierre"), lastname = "DUPONT", phone = Some("0102030405"), phone_secondary = Some("0612345678"), state = None, street_1 = "Avenue de la République", street_2 = Some("apt 123"), zip_code = Some("75001"))
  val locale = Locale.getDefault
  val customer = Customer(
    UUID.randomUUID().toString, Some("mr"), "Pierre", "DUPONT", "pierre.dupont@gmail.com",
    Some(locale.toString), billing_address = address, shipping_address = shippingAddress)

  object OperatorShippingZone extends Enumeration {
    type OperatorShippingZone = Value

    val FRANCE_METRO = Value("FR1")
    val DOM_TOM = Value("FR2")
    val EUROPE = Value("EUROPE")
    val WORLDWIDE = Value("WORLDWIDE")
  }

}

