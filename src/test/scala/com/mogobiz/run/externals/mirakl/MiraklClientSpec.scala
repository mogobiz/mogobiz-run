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

    "return offers of a shop with default paging" in {
      val EXPECTED_TOTAL_COUNT = 39 //40
      val EXPECTED_PAGINATED_OFFERS = 10

      val SHOP_ID = 2001 //tof_shop
      val response = MiraklClient.getShopOffers(SHOP_ID)
      logger.debug(response)
      response must not beNull
      val jsonResponse = JsonParser.parse(response)
      jsonResponse \ "total_count" must be_==(JInt(EXPECTED_TOTAL_COUNT))
      (jsonResponse \ "offers").children.size must be_==(EXPECTED_PAGINATED_OFFERS)
    }

    "return max offers of a shop with specific paging" in {
      val EXPECTED_TOTAL_COUNT = 39 //40
      val MAX_BY_PAGE = 100

      val SHOP_ID = 2001 //tof_shop
      val response = MiraklClient.getShopOffers(SHOP_ID, Some(MAX_BY_PAGE))
      logger.debug(response)
      response must not beNull
      val jsonResponse = JsonParser.parse(response)
      jsonResponse \ "total_count" must be_==(JInt(EXPECTED_TOTAL_COUNT))
      (jsonResponse \ "offers").children.size must be_==(EXPECTED_TOTAL_COUNT)
    }

    "return last 20 offers of a shop with paging" in {
      val EXPECTED_TOTAL_COUNT = 39 //40
      val MAX_BY_PAGE = 20
      val OFFSET = 20

      val SHOP_ID = 2001 //tof_shop
      val response = MiraklClient.getShopOffers(SHOP_ID, Some(MAX_BY_PAGE), Some(OFFSET))
      logger.debug(response)
      response must not beNull
      val jsonResponse = JsonParser.parse(response)
      jsonResponse \ "total_count" must be_==(JInt(EXPECTED_TOTAL_COUNT))
      (jsonResponse \ "offers").children.size must be_==(EXPECTED_TOTAL_COUNT - MAX_BY_PAGE)
    }



    "return one product offers" in {
      val productIds = List("fcd8a674-3d8e-4427-aff2-48377751d011")
      val response = MiraklClient.getProductsOffers(productIds)
      logger.debug(response)
      response must not beNull
      val jsonResponse = JsonParser.parse(response)
      jsonResponse \ "products" \ "total_count" must be_==(JInt(2))

    }

    "return many products offers" in {
      val productIds = List("fcd8a674-3d8e-4427-aff2-48377751d011", "eb1869b8-c6c3-4db3-9a07-d0784391bdc8", "7bd7bcec-c507-4f31-9550-5bf2a1a8a09e", "8bd7bcec-c507-4f31-9550-5bf2a1a8a09e")

      val EXPECTED_PRODUCT_NB = productIds.size

      val response = MiraklClient.getProductsOffers(productIds)
      logger.debug(response)
      response must not beNull
      val jsonResponse = JsonParser.parse(response)
      (jsonResponse \ "products").children.size must be_==(EXPECTED_PRODUCT_NB)
      val skus = (jsonResponse \ "products" \ "product_sku").extract[List[String]]
//      logger.debug("{}",skus)

      productIds.forall{
        pid => skus.contains(pid)
      } must beTrue

    }

    "return shipping fees for an offer in FRANCE" in {
      val offerId:Long = 2040
      val qty:Int = 1
      val response = MiraklClient.getShippingFeesRaw(OperatorShippingZone.FRANCE_METRO.toString, List((offerId,qty)))
      logger.debug(response)
      response must not beNull
      val jsonFees = JsonParser.parse(response)
      val shops = (jsonFees \ "shops")
      shops.children.size must be_==(1)
      shops \ "offers"
      response must not beNull
    }

    "test getShippingFeesByOffers in FRANCE" in {
      // épuisé val offerId:Long = 2040
      val shippingFees = MiraklClient.getShippingFeesByOffersAndShippingType(
        OperatorShippingZone.FRANCE_METRO.toString,
        List((2033L,3),(2036L,2)))

      //shippingFees must not beEmpty
      shippingFees.size must be_==(6)
      //List(ShippingFeeFlattenOrderLine(2001,EUR,2033,9.99,3,STD,Standard,34.97), ShippingFeeFlattenOrderLine(2001,EUR,2033,9.99,3,EXP,Express,39.97), ShippingFeeFlattenOrderLine(2001,EUR,2033,9.99,3,SUPEX,Super Express,49.97), ShippingFeeFlattenOrderLine(2001,EUR,2036,100.0,2,STD,Standard,204.0), ShippingFeeFlattenOrderLine(2001,EUR,2036,100.0,2,EXP,Express,208.0), ShippingFeeFlattenOrderLine(2001,EUR,2036,100.0,2,SUPEX,Super Express,216.0))
      shippingFees.forall(_.shopId == 2001) must beTrue
      shippingFees.filter(_.offerId==2033).size must be_==(3)
      shippingFees.filter(_.offerId==2036).size must be_==(3)

    }

    "create an order and return order id" in {
//      skipped("en attente de finition")
      val offerId:Long = 2040 //red woman footbal tshirt
      val offerQty:Int = 1
      val shippingZoneCode = OperatorShippingZone.FRANCE_METRO

      //get shipping fees
      val shippingFees = MiraklClient.getShippingFeesRaw(shippingZoneCode.toString, List((offerId, offerQty)))
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
