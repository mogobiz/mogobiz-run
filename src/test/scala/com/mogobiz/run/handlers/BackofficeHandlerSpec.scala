package com.mogobiz.run.handlers

import com.mogobiz.MogobizRouteTest
import com.mogobiz.run.exceptions.NotFoundException
import com.mogobiz.run.model.ES.{BOCartItem, BOCart}
import com.mogobiz.run.model.Mogobiz.ReturnStatus._
import com.mogobiz.run.model.Mogobiz.{ReturnStatus, ReturnedItemStatus}
import com.mogobiz.run.model.RequestParameters.{BOListCustomersRequest, BOListOrdersRequest}
import org.json4s.JsonAST._

/**
 * Created by yoannbaudy on 19/02/2015.
 */
class BackofficeHandlerSpec extends MogobizRouteTest {

  val handler = new BackofficeHandler
  val storeCode = "acmesports"
  val merchantUuid = "d7b864c8-4567-4603-abd4-5f85e9ff56e6"
  val customerUuid = "8a53ef3e-34e8-4569-8f68-ac0dfc548a0f"
  val boCartItemUuid = "734b0e5b-49f0-4dff-9732-8b28b5827518"

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

      assertOrder6000(result.list(0))
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

      assertOrder6000(result.list(0))
      assertOrder13200(result.list(1))
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

      assertOrder6000(result.list(0))
      assertOrder13200(result.list(1))
      assertOrder20040(result.list(2))
    }

    "by price with authenticate merchant" in {
      val req = BOListOrdersRequest(price = Some("13200"))
      val result = handler.listOrders(storeCode, Some(merchantUuid), req)
      result.hasNext must beFalse
      result.hasPrevious must beFalse
      result.maxItemsPerPage mustEqual(100)
      result.pageCount mustEqual(1)
      result.pageOffset mustEqual(0)
      result.pageSize mustEqual(1)
      result.totalCount mustEqual(1)
      result.list must size(1)

      assertOrder13200(result.list(0))
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

      assertOrder6000(result.list(0))
      assertOrder13200(result.list(1))
      assertOrder20040(result.list(2))
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

      assertOrder6000(result.list(0))
      assertOrder13200(result.list(1))
      assertOrder20040(result.list(2))
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

      assertOrder6000(result.list(0))
      assertOrder13200(result.list(1))
      assertOrder20040(result.list(2))
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
      val req = BOListOrdersRequest(startDate = Some("2015-03-13T14:03:30Z"))
      val result = handler.listOrders(storeCode, Some(merchantUuid), req)
      result.hasNext must beFalse
      result.hasPrevious must beFalse
      result.maxItemsPerPage mustEqual(100)
      result.pageCount mustEqual(1)
      result.pageOffset mustEqual(0)
      result.pageSize mustEqual(2)
      result.totalCount mustEqual(2)
      result.list must size(2)

      assertOrder6000(result.list(0))
      assertOrder13200(result.list(1))
    }

    "by endDate with authenticate merchant" in {
      val req = BOListOrdersRequest(endDate = Some("2015-03-13T14:04:00Z"))
      val result = handler.listOrders(storeCode, Some(merchantUuid), req)
      result.hasNext must beFalse
      result.hasPrevious must beFalse
      result.maxItemsPerPage mustEqual(100)
      result.pageCount mustEqual(1)
      result.pageOffset mustEqual(0)
      result.pageSize mustEqual(2)
      result.totalCount mustEqual(2)
      result.list must size(2)

      assertOrder13200(result.list(0))
      assertOrder20040(result.list(1))
    }

    "by startDate and endDate with authenticate merchant" in {
      val req = BOListOrdersRequest(startDate = Some("2015-03-13T14:03:30Z"), endDate = Some("2015-03-13T14:04:00Z"))
      val result = handler.listOrders(storeCode, Some(merchantUuid), req)
      result.hasNext must beFalse
      result.hasPrevious must beFalse
      result.maxItemsPerPage mustEqual(100)
      result.pageCount mustEqual(1)
      result.pageOffset mustEqual(0)
      result.pageSize mustEqual(1)
      result.totalCount mustEqual(1)
      result.list must size(1)

      assertOrder13200(result.list(0))
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
      result.pageSize mustEqual(6)
      result.totalCount mustEqual(6)
      result.list must size(6)

      result.list(0) \ "uuid" must be_==(JString("15995735-56ca-4d19-806b-a6bc7fedc162"))
      result.list(0) \ "email" must be_==(JString("waiting@merchant.com"))
      result.list(1) \ "uuid" must be_==(JString("7f441fa3-d382-4838-8255-9fc238cdb958"))
      result.list(1) \ "email" must be_==(JString("client1@merchant.com"))
      result.list(2) \ "uuid" must be_==(JString("8a53ef3e-34e8-4569-8f68-ac0dfc548a0f"))
      result.list(2) \ "email" must be_==(JString("client@merchant.com"))
      result.list(3) \ "uuid" must be_==(JString("8da1fe36-2b46-4b7c-9ca4-9516e49ffdd1"))
      result.list(3) \ "email" must be_==(JString("dgriffon@jahia.com"))
      result.list(4) \ "uuid" must be_==(JString("fd80c7e4-c91d-492a-8b48-214b809105d8"))
      result.list(4) \ "email" must be_==(JString("inactif@merchant.com"))
      result.list(5) \ "uuid" must be_==(JString("a8858dd5-e14f-4aa0-9504-3d56bab5229d"))
      result.list(5) \ "email" must be_==(JString("existing.account@test.com"))
    }

    "for merchant with email" in {
      val req = BOListCustomersRequest(email = Some("client@merchant.com"))
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

    "for merchant with unknown email" in {
      val req = BOListCustomersRequest(email = Some("unknown@merchant.com"))
      val result = handler.listCustomers(storeCode, Some(merchantUuid), req)
      result.hasNext must beFalse
      result.hasPrevious must beFalse
      result.maxItemsPerPage mustEqual(100)
      result.pageCount mustEqual(0)
      result.pageOffset mustEqual(0)
      result.pageSize mustEqual(0)
      result.totalCount mustEqual(0)
      result.list must size(0)
    }

    "for merchant with lastName" in {
      val req = BOListCustomersRequest(lastName = Some("TEST"))
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

    "for merchant with firstName" in {
      val req = BOListCustomersRequest(lastName = Some("Client 1"))
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

    "for merchant with unknown lastName" in {
      val req = BOListCustomersRequest(lastName = Some("unknown"))
      val result = handler.listCustomers(storeCode, Some(merchantUuid), req)
      result.hasNext must beFalse
      result.hasPrevious must beFalse
      result.maxItemsPerPage mustEqual(100)
      result.pageCount mustEqual(0)
      result.pageOffset mustEqual(0)
      result.pageSize mustEqual(0)
      result.totalCount mustEqual(0)
      result.list must size(0)
    }
  }

  "BackofficeHandler return cart details" should {

    "for merchant with existing transactionUuid" in {
      val result = handler.cartDetails(storeCode, Some(merchantUuid), "e8be061f-f036-4696-bed3-8e529c95eb27")
      result must not(beNull)
      result \ "transactionUuid" must be_==(JString("e8be061f-f036-4696-bed3-8e529c95eb27"))
      result \ "price" must be_==(JInt(13200))
    }

    "for merchant with not existing transactionUuid" in {
      try {
        handler.cartDetails(storeCode, Some(merchantUuid), "not-found")
        failure
      }
      catch {
        case ex: NotFoundException => success
        case _ => failure
      }
    }
  }

  "BackofficeHandler" should {
    "create BOReturnedItem for customer" in {
      handler.createBOReturnedItem(storeCode, Some(customerUuid), boCartItemUuid, 1, "test init return")

      val bOReturnedItem = retreiveBoReturnedItem()
      bOReturnedItem.refunded must be_==(0)
      bOReturnedItem.totalRefunded must be_==(0)
      bOReturnedItem.quantity must be_==(1)
      bOReturnedItem.status must be_==(ReturnedItemStatus.UNDEFINED)
      bOReturnedItem.boReturns must size(1)
      bOReturnedItem.boReturns(0).motivation must beSome("test init return")
      bOReturnedItem.boReturns(0).status must be_==(ReturnStatus.RETURN_SUBMITTED)
    }

    "accepte returned submitted for merchant" in {
      val boReturnedItemUudi = retreiveBoReturnedItem().uuid
      handler.updateBOReturnedItem(storeCode, Some(merchantUuid), boReturnedItemUudi, ReturnedItemStatus.UNDEFINED, 0, 0, ReturnStatus.RETURN_TO_BE_RECEIVED, "return is possible")

      val bOReturnedItem = retreiveBoReturnedItem()
      bOReturnedItem.refunded must be_==(0)
      bOReturnedItem.totalRefunded must be_==(0)
      bOReturnedItem.quantity must be_==(1)
      bOReturnedItem.status must be_==(ReturnedItemStatus.UNDEFINED)
      bOReturnedItem.boReturns must size(2)
      bOReturnedItem.boReturns(0).motivation must beSome("return is possible")
      bOReturnedItem.boReturns(0).status must be_==(ReturnStatus.RETURN_TO_BE_RECEIVED)
      bOReturnedItem.boReturns(1).motivation must beSome("test init return")
      bOReturnedItem.boReturns(1).status must be_==(ReturnStatus.RETURN_SUBMITTED)
    }

    "received returned for merchant" in {
      val boReturnedItemUudi = retreiveBoReturnedItem().uuid
      handler.updateBOReturnedItem(storeCode, Some(merchantUuid), boReturnedItemUudi, ReturnedItemStatus.UNDEFINED, 0, 0, ReturnStatus.RETURN_RECEIVED, "item received")

      val bOReturnedItem = retreiveBoReturnedItem()
      bOReturnedItem.refunded must be_==(0)
      bOReturnedItem.totalRefunded must be_==(0)
      bOReturnedItem.quantity must be_==(1)
      bOReturnedItem.status must be_==(ReturnedItemStatus.UNDEFINED)
      bOReturnedItem.boReturns must size(3)
      bOReturnedItem.boReturns(0).motivation must beSome("item received")
      bOReturnedItem.boReturns(0).status must be_==(ReturnStatus.RETURN_RECEIVED)
      bOReturnedItem.boReturns(1).motivation must beSome("return is possible")
      bOReturnedItem.boReturns(1).status must be_==(ReturnStatus.RETURN_TO_BE_RECEIVED)
      bOReturnedItem.boReturns(2).motivation must beSome("test init return")
      bOReturnedItem.boReturns(2).status must be_==(ReturnStatus.RETURN_SUBMITTED)
    }

    "accpete returned for merchant" in {
      val boReturnedItemUudi = retreiveBoReturnedItem().uuid
      handler.updateBOReturnedItem(storeCode, Some(merchantUuid), boReturnedItemUudi, ReturnedItemStatus.BACK_TO_STOCK, 1000, 2000, ReturnStatus.RETURN_ACCEPTED, "accepted")

      val bOReturnedItem = retreiveBoReturnedItem()
      bOReturnedItem.refunded must be_==(1000)
      bOReturnedItem.totalRefunded must be_==(2000)
      bOReturnedItem.quantity must be_==(1)
      bOReturnedItem.status must be_==(ReturnedItemStatus.BACK_TO_STOCK)
      bOReturnedItem.boReturns must size(4)
      bOReturnedItem.boReturns(0).motivation must beSome("accepted")
      bOReturnedItem.boReturns(0).status must be_==(ReturnStatus.RETURN_ACCEPTED)
      bOReturnedItem.boReturns(1).motivation must beSome("item received")
      bOReturnedItem.boReturns(1).status must be_==(ReturnStatus.RETURN_RECEIVED)
      bOReturnedItem.boReturns(2).motivation must beSome("return is possible")
      bOReturnedItem.boReturns(2).status must be_==(ReturnStatus.RETURN_TO_BE_RECEIVED)
      bOReturnedItem.boReturns(3).motivation must beSome("test init return")
      bOReturnedItem.boReturns(3).status must be_==(ReturnStatus.RETURN_SUBMITTED)
    }
  }

  private def createBOReturnedItem() : String = {
    handler.createBOReturnedItem(storeCode, Some(customerUuid), boCartItemUuid, 1, "test init return")

    retreiveBoReturnedItem().uuid
  }

  private def retreiveBoReturnedItem() = {
    val boCartOpt = BOCartESDao.load(storeCode, "782a1be4-7d4f-458c-80ab-25648590a36c")
    boCartOpt must beSome[BOCart]
    val boCartItemOpt =  boCartOpt.get.cartItems.find{boCartItem => boCartItem.uuid == boCartItemUuid}
    boCartItemOpt must beSome[BOCartItem]
    boCartItemOpt.get.bOReturnedItems must size(1)
    boCartItemOpt.get.bOReturnedItems(0)
  }

  private def assertOrder13200(order: JValue) = {
    order \ "uuid" must be_==(JString("e8be061f-f036-4696-bed3-8e529c95eb27"))
    order \ "transactionUUID" must be_==(JString("e8be061f-f036-4696-bed3-8e529c95eb27"))
    order \ "authorizationId" must be_==(JString(""))
    order \ "amount" must be_==(JInt(13200))
    order \ "status" must be_==(JString("PAYMENT_CONFIRMED"))
    order \ "email" must be_==(JString("client@merchant.com"))
    order \ "extra" must be_==(JNothing)
    order \ "vendor" must be_==(JNothing)

    val deliveryStatus = checkJArray(order \ "deliveryStatus")
    deliveryStatus must size(1)
    deliveryStatus(0) must be_==(JString("NOT_STARTED"))
  }

  private def assertOrder20040(order: JValue) = {
    order \ "uuid" must be_==(JString("dcacac6e-720f-4715-b5d7-ed6ed4431ab2"))
    order \ "transactionUUID" must be_==(JString("dcacac6e-720f-4715-b5d7-ed6ed4431ab2"))
    order \ "authorizationId" must be_==(JString(""))
    order \ "amount" must be_==(JInt(20040))
    order \ "status" must be_==(JString("PAYMENT_CONFIRMED"))
    order \ "email" must be_==(JString("client@merchant.com"))
    order \ "extra" must be_==(JNothing)
    order \ "vendor" must be_==(JNothing)

    val deliveryStatus = checkJArray(order \ "deliveryStatus")
    deliveryStatus must size(2)
    deliveryStatus(0) must be_==(JString("NOT_STARTED"))
    deliveryStatus(1) must be_==(JString("NOT_STARTED"))
  }

  private def assertOrder6000(order: JValue) = {
    order \ "uuid" must be_==(JString("c71f721d-1bcc-4858-85e9-5b70169d4c7e"))
    order \ "transactionUUID" must be_==(JString("c71f721d-1bcc-4858-85e9-5b70169d4c7e"))
    order \ "authorizationId" must be_==(JString(""))
    order \ "amount" must be_==(JInt(6000))
    order \ "status" must be_==(JString("PAYMENT_CONFIRMED"))
    order \ "email" must be_==(JString("client@merchant.com"))
    order \ "extra" must be_==(JNothing)
    order \ "vendor" must be_==(JNothing)

    val deliveryStatus = checkJArray(order \ "deliveryStatus")
    deliveryStatus must size(1)
    deliveryStatus(0) must be_==(JString("NOT_STARTED"))
  }
}
