package com.mogobiz.run.externals.mirakl

import java.util.{Locale, UUID}

import com.mogobiz.run.externals.mirakl.Mirakl._
import com.mogobiz.run.externals.mirakl.MiraklClientMain.OperatorShippingZone._
import com.typesafe.scalalogging.StrictLogging
import org.json4s.JsonAST._
import org.json4s.native.JsonParser
import org.specs2.mutable.Specification


class MiraklClientSpec extends Specification  with StrictLogging {
  import org.json4s.{ DefaultFormats, Formats }
  implicit def json4sFormats: Formats = DefaultFormats

  object OperatorShippingZone extends Enumeration {
    type OperatorShippingZone = Value

    val FRANCE_METRO = Value("FR1")
    val DOM_TOM = Value("FR2")
    val EUROPE = Value("EUROPE")
    val WORLDWIDE = Value("WORLDWIDE")
  }

  "MiraclClient " should {

    "return all shops information" in {
      val response = MiraklClient.getAllShops()
      logger.debug(response)
      response must not beNull
    }

    "return offers of a shop" in {
      val shopId = 2001 //tof_shop
      val response = MiraklClient.getShopOffers(shopId)
      logger.debug(response)
      response must not beNull
      val jsonResponse = JsonParser.parse(response)
      jsonResponse \ "total_count" must be_==(JInt(40))
    }

    "return shipping fees for an offer in FRANCE" in {
      val offerId:Long = 2040
      val qty:Int = 1
      val response = MiraklClient.getShippingFees(OperatorShippingZone.FRANCE_METRO.toString, List((offerId,qty)))
      logger.debug(response)
      response must not beNull
    }

    "create an order and return order id" in {
//      skipped("en attente de finition")
      val offerId:Long = 2040 //red woman footbal tshirt
      val offerQty:Int = 1
      val shippingZoneCode = OperatorShippingZone.FRANCE_METRO

      //get shipping fees
      val shippingFees = MiraklClient.getShippingFees(shippingZoneCode.toString, List((offerId, offerQty)))
      //logger.debug(shippingFees)
      val jsonFees = JsonParser.parse(shippingFees)
//      jsonFees \ "offers_not_found" must be_==(JArray(_))
      val offerPrice = (jsonFees \ "shops" \ "offers" \ "line_price").extract[Double]
      logger.debug("offerPrice={}", offerPrice)
      val shippingPrice = (jsonFees \ "shops" \ "offers" \ "line_shipping_price").extract[Double]
      logger.debug("shippingPrice={}", shippingPrice)
      val shippingTypeCode = (jsonFees \ "shops" \ "offers" \ "selected_shipping_type" \ "code").extract[String]
      logger.debug("shippingTypeCode={}", shippingTypeCode)

      val address = Address(city = "PARIS", civility = Some("mr"), company = Some("MonsterInc"), country = "FRANCE", country_iso_code = "FRA", firstname = Some("Pierre"), lastname = "DUPONT", phone = Some("0102030405"), phone_secondary = Some("0612345678"), state = None, street_1 = "Avenue de la République", street_2 = Some("apt 123"), zip_code = Some("75001"))
      val shippingAddress = ShippingAddress(city = "PARIS", civility = Some("mr"), company = Some("MonsterInc"), country = "FRANCE", country_iso_code = "FRA", firstname = Some("Pierre"), lastname = "DUPONT", phone = Some("0102030405"), phone_secondary = Some("0612345678"), state = None, street_1 = "Avenue de la République", street_2 = Some("apt 123"), zip_code = Some("75001"))

      val locale = Locale.getDefault
      val paymentInfo = PaymentInfo(Some("123656465"), Some("visa"))
      val paymentWorkflow = PaymentWorkflow.PAY_ON_ACCEPTANCE


      val customer = Customer(
        UUID.randomUUID().toString, Some("mr"), "Pierre", "DUPONT", "pierre.dupont@gmail.com",
        Some(locale.toString), billing_address = address, shipping_address = shippingAddress)

      val offers = Array(
        createOffer(offerId, offerPrice, 1,shippingPrice,shippingTypeCode)
      )

      val order: OrderBean = OrderBean(
        channel_code = None, //Some("CHANNEL_CODE") not working coz it should exists
        commercial_id = "TEST-ORDER_000000004",
        customer = customer,
        offers = offers,
        order_additional_fields = Array(),
        payment_info = Some(paymentInfo),
        payment_workflow = Some(paymentWorkflow.toString),
        scored = false,
        shipping_zone_code = shippingZoneCode.toString)

      val orderId = MiraklClient.createOrder(order)
      logger.debug("orderId={}", orderId)
      orderId must not beNull
    }

    "return list of orders " in {
      val response = MiraklClient.listOrders()
      logger.debug(response)
      response must not beNull
      val jsonResponse = JsonParser.parse(response)
      jsonResponse \ "total_count" must be_==(JInt(4))
    }
  }


  private def createOffer(offerId: Long, unitPrice: BigDecimal, quantity: Int, shippingPrice: BigDecimal, shippingTypeCode: String):Offer = {
    Offer(
      currency_iso_code = "EUR",
      leadtime_to_ship = None,
      offer_id = offerId,
      offer_price = unitPrice,
      order_line_additional_fields = Array(),
      order_line_id = None,
      price = unitPrice * quantity,
      quantity = quantity,
      shipping_price = shippingPrice,
      shipping_type_code = shippingTypeCode
    )
  }



}
