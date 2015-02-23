package com.mogobiz.run.handlers

import com.mogobiz.MogobizRouteTest
import com.mogobiz.run.model.RequestParameters.{BOListCustomersRequest, BOListOrdersRequest}
import org.json4s.JsonAST._

/**
 * Created by yoannbaudy on 19/02/2015.
 */
class BackofficeHandlerSpec extends MogobizRouteTest {

  val handler = new BackofficeHandler
  val storeCode = "acmesport"
  val merchantUuid = "d7b864c8-4567-4603-abd4-5f85e9ff56e6"

  "BackofficeHandler list orders" should {

    "by lastName with authenticate merchant using paging (maxItemPerPage = 1)" in {
      val req = BOListOrdersRequest(maxItemPerPage = Some(1), lastName = Some("TEST"))
      val result = handler.listOrders(storeCode, Some(merchantUuid), req)
      result.hasNext must beTrue
      result.hasPrevious must beFalse
      result.maxItemsPerPage mustEqual(1)
      result.pageCount mustEqual(3)
      result.pageOffset mustEqual(0)
      result.pageSize mustEqual(1)
      result.totalCount mustEqual(3)
      result.list must size(1)

      assertOrder1188000(result.list(0))
    }

    "by firstName with authenticate merchant (maxItemPerPage = 2)" in {
      val req = BOListOrdersRequest(maxItemPerPage = Some(2), lastName = Some("Client 1"))
      val result = handler.listOrders(storeCode, Some(merchantUuid), req)
      result.hasNext must beTrue
      result.hasPrevious must beFalse
      result.maxItemsPerPage mustEqual(2)
      result.pageCount mustEqual(2)
      result.pageOffset mustEqual(0)
      result.pageSize mustEqual(2)
      result.totalCount mustEqual(3)
      result.list must size(2)

      assertOrder1188000(result.list(0))
      assertOrder2560(result.list(1))
    }

    "by email with authenticate merchant" in {
      val req = BOListOrdersRequest(email = Some("client@merchant.com"))
      val result = handler.listOrders(storeCode, Some(merchantUuid), req)
      result.hasNext must beFalse
      result.hasPrevious must beFalse
      result.maxItemsPerPage mustEqual(100)
      result.pageCount mustEqual(1)
      result.pageOffset mustEqual(0)
      result.pageSize mustEqual(3)
      result.totalCount mustEqual(3)
      result.list must size(3)

      assertOrder1188000(result.list(0))
      assertOrder2560(result.list(1))
      assertOrder2080(result.list(2))
    }

    "by price with authenticate merchant" in {
      val req = BOListOrdersRequest(price = Some("2080"))
      val result = handler.listOrders(storeCode, Some(merchantUuid), req)
      result.hasNext must beFalse
      result.hasPrevious must beFalse
      result.maxItemsPerPage mustEqual(100)
      result.pageCount mustEqual(1)
      result.pageOffset mustEqual(0)
      result.pageSize mustEqual(1)
      result.totalCount mustEqual(1)
      result.list must size(1)

      assertOrder2080(result.list(0))
    }

    "by Delivery Status with authenticate merchant" in {
      val req = BOListOrdersRequest(deliveryStatus = Some("NOT_STARTED"))
      val result = handler.listOrders(storeCode, Some(merchantUuid), req)
      result.hasNext must beFalse
      result.hasPrevious must beFalse
      result.maxItemsPerPage mustEqual(100)
      result.pageCount mustEqual(1)
      result.pageOffset mustEqual(0)
      result.pageSize mustEqual(3)
      result.totalCount mustEqual(3)
      result.list must size(3)

      assertOrder1188000(result.list(0))
      assertOrder2560(result.list(1))
      assertOrder2080(result.list(2))
    }

    "by Transaction Status with authenticate merchant" in {
      val req = BOListOrdersRequest(transactionStatus = Some("PAYMENT_CONFIRMED"))
      val result = handler.listOrders(storeCode, Some(merchantUuid), req)
      result.hasNext must beFalse
      result.hasPrevious must beFalse
      result.maxItemsPerPage mustEqual(100)
      result.pageCount mustEqual(1)
      result.pageOffset mustEqual(0)
      result.pageSize mustEqual(3)
      result.totalCount mustEqual(3)
      result.list must size(3)

      assertOrder1188000(result.list(0))
      assertOrder2560(result.list(1))
      assertOrder2080(result.list(2))
    }

    "by Transaction and Delivery Status with authenticate merchant" in {
      val req = BOListOrdersRequest(transactionStatus = Some("PAYMENT_CONFIRMED"), deliveryStatus = Some("NOT_STARTED"))
      val result = handler.listOrders(storeCode, Some(merchantUuid), req)
      result.hasNext must beFalse
      result.hasPrevious must beFalse
      result.maxItemsPerPage mustEqual(100)
      result.pageCount mustEqual(1)
      result.pageOffset mustEqual(0)
      result.pageSize mustEqual(3)
      result.totalCount mustEqual(3)
      result.list must size(3)

      assertOrder1188000(result.list(0))
      assertOrder2560(result.list(1))
      assertOrder2080(result.list(2))
    }

    "by not found Delivery Status with authenticate merchant" in {
      val req = BOListOrdersRequest(deliveryStatus = Some("???NOT_FOUND???"))
      val result = handler.listOrders(storeCode, Some(merchantUuid), req)
      result.hasNext must beFalse
      result.hasPrevious must beFalse
      result.maxItemsPerPage mustEqual(100)
      result.pageCount mustEqual(0)
      result.pageOffset mustEqual(0)
      result.pageSize mustEqual(0)
      result.totalCount mustEqual(0)
      result.list must size(0)
    }

    "by startDate with authenticate merchant" in {
      val req = BOListOrdersRequest(startDate = Some("2015-01-30T00:00:00Z"))
      val result = handler.listOrders(storeCode, Some(merchantUuid), req)
      result.hasNext must beFalse
      result.hasPrevious must beFalse
      result.maxItemsPerPage mustEqual(100)
      result.pageCount mustEqual(1)
      result.pageOffset mustEqual(0)
      result.pageSize mustEqual(2)
      result.totalCount mustEqual(2)
      result.list must size(2)

      assertOrder1188000(result.list(0))
      assertOrder2560(result.list(1))
    }

    "by endDate with authenticate merchant" in {
      val req = BOListOrdersRequest(endDate = Some("2015-01-31T00:00:00Z"))
      val result = handler.listOrders(storeCode, Some(merchantUuid), req)
      result.hasNext must beFalse
      result.hasPrevious must beFalse
      result.maxItemsPerPage mustEqual(100)
      result.pageCount mustEqual(1)
      result.pageOffset mustEqual(0)
      result.pageSize mustEqual(2)
      result.totalCount mustEqual(2)
      result.list must size(2)

      assertOrder2560(result.list(0))
      assertOrder2080(result.list(1))
    }

    "by startDate and endDate with authenticate merchant" in {
      val req = BOListOrdersRequest(startDate = Some("2015-01-29T00:00:00Z"), endDate = Some("2015-01-29T23:59:59Z"))
      val result = handler.listOrders(storeCode, Some(merchantUuid), req)
      result.hasNext must beFalse
      result.hasPrevious must beFalse
      result.maxItemsPerPage mustEqual(100)
      result.pageCount mustEqual(1)
      result.pageOffset mustEqual(0)
      result.pageSize mustEqual(1)
      result.totalCount mustEqual(1)
      result.list must size(1)

      assertOrder2080(result.list(0))
    }
  }

  "BackofficeHandler list customers" should {
    "for merchant" in {
      val req = BOListCustomersRequest()
      val result = handler.listCustomers(storeCode, Some(merchantUuid), req)
      result.hasNext must beFalse
      result.hasPrevious must beFalse
      result.maxItemsPerPage mustEqual(100)
      result.pageCount mustEqual(1)
      result.pageOffset mustEqual(0)
      result.pageSize mustEqual(1)
      result.totalCount mustEqual(1)
      result.list must size(1)

      val customer = result.list(0)
      customer \ "uuid" must be_==(JString("8a53ef3e-34e8-4569-8f68-ac0dfc548a0f"))
      customer \ "email" must be_==(JString("client@merchant.com"))
    }
  }

  private def assertOrder2080(order: JValue) = {
    order \ "uuid" must be_==(JString("f9f71371-17f3-4dcd-bf8f-5d313470ccdf"))
    order \ "transactionUUID" must be_==(JString("f9f71371-17f3-4dcd-bf8f-5d313470ccdf"))
    order \ "authorizationId" must be_==(JString("8167684525895056883"))
    order \ "amount" must be_==(JInt(2080))
    order \ "status" must be_==(JString("PAYMENT_CONFIRMED"))
    order \ "email" must be_==(JString("client@merchant.com"))
    order \ "extra" must be_==(JNothing)
    order \ "vendor" must be_==(JNothing)

    val deliveryStatus = checkJArray(order \ "deliveryStatus")
    deliveryStatus must size(1)
    deliveryStatus(0) must be_==(JString("NOT_STARTED"))
  }

  private def assertOrder2560(order: JValue) = {
    order \ "uuid" must be_==(JString("4c7a5788-0079-4781-b823-047cbef84198"))
    order \ "transactionUUID" must be_==(JString("4c7a5788-0079-4781-b823-047cbef84198"))
    order \ "authorizationId" must be_==(JString("3666656541829891917"))
    order \ "amount" must be_==(JInt(2560))
    order \ "status" must be_==(JString("PAYMENT_CONFIRMED"))
    order \ "email" must be_==(JString("client@merchant.com"))
    order \ "extra" must be_==(JNothing)
    order \ "vendor" must be_==(JNothing)

    val deliveryStatus = checkJArray(order \ "deliveryStatus")
    deliveryStatus must size(1)
    deliveryStatus(0) must be_==(JString("NOT_STARTED"))
  }

  private def assertOrder1188000(order: JValue) = {
    order \ "uuid" must be_==(JString("931eedc2-a4cd-431f-ba9c-aba4ed68806c"))
    order \ "transactionUUID" must be_==(JString("931eedc2-a4cd-431f-ba9c-aba4ed68806c"))
    order \ "authorizationId" must be_==(JString("4936994872997537717"))
    order \ "amount" must be_==(JInt(1188000))
    order \ "status" must be_==(JString("PAYMENT_CONFIRMED"))
    order \ "email" must be_==(JString("client@merchant.com"))
    order \ "extra" must be_==(JNothing)
    order \ "vendor" must be_==(JNothing)

    val deliveryStatus = checkJArray(order \ "deliveryStatus")
    deliveryStatus must size(1)
    deliveryStatus(0) must be_==(JString("NOT_STARTED"))
  }
}
