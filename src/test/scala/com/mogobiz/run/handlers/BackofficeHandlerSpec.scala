/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import com.mogobiz.MogobizRouteTest
import com.mogobiz.run.exceptions.NotFoundException
import com.mogobiz.run.model.Mogobiz.{ReturnStatus, ReturnedItemStatus}
import com.mogobiz.run.model.RequestParameters.{BOListCustomersRequest, BOListOrdersRequest, CreateBOReturnedItemRequest, UpdateBOReturnedItemRequest}
import org.json4s.JsonAST._

/**
  */
class BackofficeHandlerSpec extends MogobizRouteTest {

  val handler = new BackofficeHandler
  val storeCode = "acmesports"
  val merchantUuid = "d7b864c8-4567-4603-abd4-5f85e9ff56e6"
  val customerUuid = "8a53ef3e-34e8-4569-8f68-ac0dfc548a0f"
  val transactionUuid = "dcacac6e-720f-4715-b5d7-ed6ed4431ab2"
  val boCartItemUuid = "734b0e5b-49f0-4dff-9732-8b28b5827518"

  "BackofficeHandler list orders" should "by lastName with authenticate merchant using paging (maxItemPerPage = 1)" in {
    val req = BOListOrdersRequest(maxItemPerPage = Some(1), lastName = Some("TEST"))
    val result = handler.listOrders(storeCode, Some(merchantUuid), req)
    result.hasNext should be(true)
    result.hasPrevious should be(true)
    result.maxItemsPerPage should equal(1)
    result.pageCount should equal(3)
    result.pageOffset should equal(0)
    result.pageSize should equal(1)
    result.totalCount should equal(3)
    result.list should have size (1)

    assertOrder6000(result.list(0))
  }

  it should "by firstName with authenticate merchant (maxItemPerPage = 2)" in {
    val req = BOListOrdersRequest(maxItemPerPage = Some(2), lastName = Some("Client 1"))
    val result = handler.listOrders(storeCode, Some(merchantUuid), req)
    result.hasNext should be(true)
    result.hasPrevious should be(false)
    result.maxItemsPerPage should equal(2)
    result.pageCount should equal(2)
    result.pageOffset should equal(0)
    result.pageSize should equal(2)
    result.totalCount should equal(3)
    result.list should have size (2)

    assertOrder6000(result.list(0))
    assertOrder13200(result.list(1))
  }

  it should "by email with authenticate merchant" in {
    val req = BOListOrdersRequest(email = Some("client@merchant.com"))
    val result = handler.listOrders(storeCode, Some(merchantUuid), req)
    result.hasNext should be(false)
    result.hasPrevious should be(false)
    result.maxItemsPerPage should equal(100)
    result.pageCount should equal(1)
    result.pageOffset should equal(0)
    result.pageSize should equal(3)
    result.totalCount should equal(3)
    result.list should have size (3)

    assertOrder6000(result.list(0))
    assertOrder13200(result.list(1))
    assertOrder20040(result.list(2))
  }

  it should "by price with authenticate merchant" in {
    val req = BOListOrdersRequest(price = Some("13200"))
    val result = handler.listOrders(storeCode, Some(merchantUuid), req)
    result.hasNext should be(false)
    result.hasPrevious should be(false)
    result.maxItemsPerPage should equal(100)
    result.pageCount should equal(1)
    result.pageOffset should equal(0)
    result.pageSize should equal(1)
    result.totalCount should equal(1)
    result.list should have size (1)

    assertOrder13200(result.list(0))
  }

  it should "by Delivery Status with authenticate merchant" in {
    val req = BOListOrdersRequest(deliveryStatus = Some("NOT_STARTED"))
    val result = handler.listOrders(storeCode, Some(merchantUuid), req)
    result.hasNext should be(false)
    result.hasPrevious should be(false)
    result.maxItemsPerPage should equal(100)
    result.pageCount should equal(1)
    result.pageOffset should equal(0)
    result.pageSize should equal(3)
    result.totalCount should equal(3)
    result.list should have size (3)

    assertOrder6000(result.list(0))
    assertOrder13200(result.list(1))
    assertOrder20040(result.list(2))
  }

  it should "by Transaction Status with authenticate merchant" in {
    val req = BOListOrdersRequest(transactionStatus = Some("PAYMENT_CONFIRMED"))
    val result = handler.listOrders(storeCode, Some(merchantUuid), req)
    result.hasNext should be(false)
    result.hasPrevious should be(false)
    result.maxItemsPerPage should equal(100)
    result.pageCount should equal(1)
    result.pageOffset should equal(0)
    result.pageSize should equal(3)
    result.totalCount should equal(3)
    result.list should have size (3)

    assertOrder6000(result.list(0))
    assertOrder13200(result.list(1))
    assertOrder20040(result.list(2))
  }

  it should "by Transaction and Delivery Status with authenticate merchant" in {
    val req = BOListOrdersRequest(transactionStatus = Some("PAYMENT_CONFIRMED"), deliveryStatus = Some("NOT_STARTED"))
    val result = handler.listOrders(storeCode, Some(merchantUuid), req)
    result.hasNext should be(false)
    result.hasPrevious should be(false)
    result.maxItemsPerPage should equal(100)
    result.pageCount should equal(1)
    result.pageOffset should equal(0)
    result.pageSize should equal(3)
    result.totalCount should equal(3)
    result.list should have size (3)

    assertOrder6000(result.list(0))
    assertOrder13200(result.list(1))
    assertOrder20040(result.list(2))
  }

  it should "by not found Delivery Status with authenticate merchant" in {
    val req = BOListOrdersRequest(deliveryStatus = Some("???NOT_FOUND???"))
    val result = handler.listOrders(storeCode, Some(merchantUuid), req)
    result.hasNext should be(false)
    result.hasPrevious should be(false)
    result.maxItemsPerPage should equal(100)
    result.pageCount should equal(0)
    result.pageOffset should equal(0)
    result.pageSize should equal(0)
    result.totalCount should equal(0)
    result.list should have size (0)
  }

  it should "by startDate with authenticate merchant" in {
    val req = BOListOrdersRequest(startDate = Some("2015-03-13T14:03:30Z"))
    val result = handler.listOrders(storeCode, Some(merchantUuid), req)
    result.hasNext should be(false)
    result.hasPrevious should be(false)
    result.maxItemsPerPage should equal(100)
    result.pageCount should equal(1)
    result.pageOffset should equal(0)
    result.pageSize should equal(2)
    result.totalCount should equal(2)
    result.list should have size (2)

    assertOrder6000(result.list(0))
    assertOrder13200(result.list(1))
  }

  it should "by endDate with authenticate merchant" in {
    val req = BOListOrdersRequest(endDate = Some("2015-03-13T14:04:00Z"))
    val result = handler.listOrders(storeCode, Some(merchantUuid), req)
    result.hasNext should be(false)
    result.hasPrevious should be(false)
    result.maxItemsPerPage should equal(100)
    result.pageCount should equal(1)
    result.pageOffset should equal(0)
    result.pageSize should equal(2)
    result.totalCount should equal(2)
    result.list should have size (2)

    assertOrder13200(result.list(0))
    assertOrder20040(result.list(1))
  }

  it should "by startDate and endDate with authenticate merchant" in {
    val req = BOListOrdersRequest(startDate = Some("2015-03-13T14:03:30Z"), endDate = Some("2015-03-13T14:04:00Z"))
    val result = handler.listOrders(storeCode, Some(merchantUuid), req)
    result.hasNext should be(false)
    result.hasPrevious should be(false)
    result.maxItemsPerPage should equal(100)
    result.pageCount should equal(1)
    result.pageOffset should equal(0)
    result.pageSize should equal(1)
    result.totalCount should equal(1)
    result.list should have size (1)

    assertOrder13200(result.list(0))
  }

  "BackofficeHandler list customers" should "for merchant" in {
    val req = BOListCustomersRequest()
    val result = handler.listCustomers(storeCode, Some(merchantUuid), req)
    result.hasNext should be(false)
    result.hasPrevious should be(false)
    result.maxItemsPerPage should equal(100)
    result.pageCount should equal(1)
    result.pageOffset should equal(0)
    result.pageSize should equal(6)
    result.totalCount should equal(6)
    result.list should have size (6)

    result.list(0) \ "uuid" should equal(JString("15995735-56ca-4d19-806b-a6bc7fedc162"))
    result.list(0) \ "email" should equal(JString("waiting@merchant.com"))
    result.list(1) \ "uuid" should equal(JString("7f441fa3-d382-4838-8255-9fc238cdb958"))
    result.list(1) \ "email" should equal(JString("client1@merchant.com"))
    result.list(2) \ "uuid" should equal(JString("8a53ef3e-34e8-4569-8f68-ac0dfc548a0f"))
    result.list(2) \ "email" should equal(JString("client@merchant.com"))
    result.list(3) \ "uuid" should equal(JString("8da1fe36-2b46-4b7c-9ca4-9516e49ffdd1"))
    result.list(3) \ "email" should equal(JString("dgriffon@jahia.com"))
    result.list(4) \ "uuid" should equal(JString("fd80c7e4-c91d-492a-8b48-214b809105d8"))
    result.list(4) \ "email" should equal(JString("inactif@merchant.com"))
    result.list(5) \ "uuid" should equal(JString("a8858dd5-e14f-4aa0-9504-3d56bab5229d"))
    result.list(5) \ "email" should equal(JString("existing.account@test.com"))
  }

  it should "for merchant with email" in {
    val req = BOListCustomersRequest(email = Some("client@merchant.com"))
    val result = handler.listCustomers(storeCode, Some(merchantUuid), req)
    result.hasNext should be(false)
    result.hasPrevious should be(false)
    result.maxItemsPerPage should equal(100)
    result.pageCount should equal(1)
    result.pageOffset should equal(0)
    result.pageSize should equal(1)
    result.totalCount should equal(1)
    result.list should have size (1)

    val customer = result.list(0)
    customer \ "uuid" should equal(JString("8a53ef3e-34e8-4569-8f68-ac0dfc548a0f"))
    customer \ "email" should equal(JString("client@merchant.com"))
  }

  it should "for merchant with unknown email" in {
    val req = BOListCustomersRequest(email = Some("unknown@merchant.com"))
    val result = handler.listCustomers(storeCode, Some(merchantUuid), req)
    result.hasNext should be(false)
    result.hasPrevious should be(false)
    result.maxItemsPerPage should equal(100)
    result.pageCount should equal(0)
    result.pageOffset should equal(0)
    result.pageSize should equal(0)
    result.totalCount should equal(0)
    result.list should have size (0)
  }

  it should "for merchant with lastName" in {
    val req = BOListCustomersRequest(lastName = Some("TEST"))
    val result = handler.listCustomers(storeCode, Some(merchantUuid), req)
    result.hasNext should be(false)
    result.hasPrevious should be(false)
    result.maxItemsPerPage should equal(100)
    result.pageCount should equal(1)
    result.pageOffset should equal(0)
    result.pageSize should equal(1)
    result.totalCount should equal(1)
    result.list should have size (1)

    val customer = result.list(0)
    customer \ "uuid" should equal(JString("8a53ef3e-34e8-4569-8f68-ac0dfc548a0f"))
    customer \ "email" should equal(JString("client@merchant.com"))
  }

  it should "for merchant with firstName" in {
    val req = BOListCustomersRequest(lastName = Some("Client 1"))
    val result = handler.listCustomers(storeCode, Some(merchantUuid), req)
    result.hasNext should be(false)
    result.hasPrevious should be(false)
    result.maxItemsPerPage should equal(100)
    result.pageCount should equal(1)
    result.pageOffset should equal(0)
    result.pageSize should equal(1)
    result.totalCount should equal(1)
    result.list should have size (1)

    val customer = result.list(0)
    customer \ "uuid" should equal(JString("8a53ef3e-34e8-4569-8f68-ac0dfc548a0f"))
    customer \ "email" should equal(JString("client@merchant.com"))
  }

  it should "for merchant with unknown lastName" in {
    val req = BOListCustomersRequest(lastName = Some("unknown"))
    val result = handler.listCustomers(storeCode, Some(merchantUuid), req)
    result.hasNext should be(false)
    result.hasPrevious should be(false)
    result.maxItemsPerPage should equal(100)
    result.pageCount should equal(0)
    result.pageOffset should equal(0)
    result.pageSize should equal(0)
    result.totalCount should equal(0)
    result.list should have size (0)
  }

  "BackofficeHandler return cart details" should "for merchant with existing transactionUuid" in {
    val result = handler.cartDetails(storeCode, Some(merchantUuid), "e8be061f-f036-4696-bed3-8e529c95eb27")
    result should not be null
    result \ "transactionUuid" should equal(JString("e8be061f-f036-4696-bed3-8e529c95eb27"))
    result \ "price" should equal(JInt(13200))
  }

  it should "for merchant with not existing transactionUuid" in {
    try {
      handler.cartDetails(storeCode, Some(merchantUuid), "not-found")
      assert(false)
    }
    catch {
      case ex: NotFoundException => assert(true)
      case _: Throwable => assert(false)
    }
  }

  "BackofficeHandler" should "create BOReturnedItem for customer" in {
    val req = new CreateBOReturnedItemRequest(quantity = 1, motivation = "test init return")
    handler.createBOReturnedItem(storeCode, customerUuid, transactionUuid, boCartItemUuid, req, None)

    val bOReturnedItem = retreiveBoReturnedItem()
    bOReturnedItem.refunded should equal(0)
    bOReturnedItem.totalRefunded should equal(0)
    bOReturnedItem.quantity should equal(1)
    bOReturnedItem.status should equal(ReturnedItemStatus.UNDEFINED)
    bOReturnedItem.boReturns should have size (1)
    bOReturnedItem.boReturns(0).motivation shouldBe Some("test init return")
    bOReturnedItem.boReturns(0).status should equal(ReturnStatus.RETURN_SUBMITTED)
  }

  it should "accepte returned submitted for merchant" in {
    val boReturnedItemUudi = retreiveBoReturnedItem().uuid
    val req = new UpdateBOReturnedItemRequest(
      status = ReturnedItemStatus.UNDEFINED.toString(),
      refunded = 0,
      totalRefunded = 0,
      returnStatus = ReturnStatus.RETURN_TO_BE_RECEIVED.toString(),
      motivation = "return is possible"
    )
    handler.updateBOReturnedItem(storeCode, merchantUuid, transactionUuid, boCartItemUuid, boReturnedItemUudi, req, None)

    val bOReturnedItem = retreiveBoReturnedItem()
    bOReturnedItem.refunded should equal(0)
    bOReturnedItem.totalRefunded should equal(0)
    bOReturnedItem.quantity should equal(1)
    bOReturnedItem.status should equal(ReturnedItemStatus.UNDEFINED)
    bOReturnedItem.boReturns should have size (2)
    bOReturnedItem.boReturns(0).motivation shouldBe Some("return is possible")
    bOReturnedItem.boReturns(0).status should equal(ReturnStatus.RETURN_TO_BE_RECEIVED)
    bOReturnedItem.boReturns(1).motivation shouldBe Some("test init return")
    bOReturnedItem.boReturns(1).status should equal(ReturnStatus.RETURN_SUBMITTED)
  }

  it should "received returned for merchant" in {
    val boReturnedItemUudi = retreiveBoReturnedItem().uuid
    val req = new UpdateBOReturnedItemRequest(
      status = ReturnedItemStatus.UNDEFINED.toString(),
      refunded = 0,
      totalRefunded = 0,
      returnStatus = ReturnStatus.RETURN_RECEIVED.toString(),
      motivation = "item received"
    )
    handler.updateBOReturnedItem(storeCode, merchantUuid, transactionUuid, boCartItemUuid, boReturnedItemUudi, req, None)

    val bOReturnedItem = retreiveBoReturnedItem()
    bOReturnedItem.refunded should equal(0)
    bOReturnedItem.totalRefunded should equal(0)
    bOReturnedItem.quantity should equal(1)
    bOReturnedItem.status should equal(ReturnedItemStatus.UNDEFINED)
    bOReturnedItem.boReturns should have size (3)
    bOReturnedItem.boReturns(0).motivation shouldBe Some("item received")
    bOReturnedItem.boReturns(0).status should equal(ReturnStatus.RETURN_RECEIVED)
    bOReturnedItem.boReturns(1).motivation shouldBe Some("return is possible")
    bOReturnedItem.boReturns(1).status should equal(ReturnStatus.RETURN_TO_BE_RECEIVED)
    bOReturnedItem.boReturns(2).motivation shouldBe Some("test init return")
    bOReturnedItem.boReturns(2).status should equal(ReturnStatus.RETURN_SUBMITTED)
  }

  it should "accpete returned for merchant" in {
    val boReturnedItemUudi = retreiveBoReturnedItem().uuid
    val req = new UpdateBOReturnedItemRequest(
      status = ReturnedItemStatus.BACK_TO_STOCK.toString(),
      refunded = 1000,
      totalRefunded = 2000,
      returnStatus = ReturnStatus.RETURN_ACCEPTED.toString(),
      motivation = "accepted"
    )
    handler.updateBOReturnedItem(storeCode, merchantUuid, transactionUuid, boCartItemUuid, boReturnedItemUudi, req, None)

    val bOReturnedItem = retreiveBoReturnedItem()
    bOReturnedItem.refunded should equal(1000)
    bOReturnedItem.totalRefunded should equal(2000)
    bOReturnedItem.quantity should equal(1)
    bOReturnedItem.status should equal(ReturnedItemStatus.BACK_TO_STOCK)
    bOReturnedItem.boReturns should have size (4)
    bOReturnedItem.boReturns(0).motivation shouldBe Some("accepted")
    bOReturnedItem.boReturns(0).status should equal(ReturnStatus.RETURN_ACCEPTED)
    bOReturnedItem.boReturns(1).motivation shouldBe Some("item received")
    bOReturnedItem.boReturns(1).status should equal(ReturnStatus.RETURN_RECEIVED)
    bOReturnedItem.boReturns(2).motivation shouldBe Some("return is possible")
    bOReturnedItem.boReturns(2).status should equal(ReturnStatus.RETURN_TO_BE_RECEIVED)
    bOReturnedItem.boReturns(3).motivation shouldBe Some("test init return")
    bOReturnedItem.boReturns(3).status should equal(ReturnStatus.RETURN_SUBMITTED)
  }


  private def retreiveBoReturnedItem(): BOReturnedItem = {
    val boCartOpt: Option[BOShopCart] = ??? // BOCartESDao.load(storeCode, "782a1be4-7d4f-458c-80ab-25648590a36c")
    boCartOpt shouldBe a[BOShopCart]
    val boCartItemOpt: Option[BOCartItem] = boCartOpt.get.cartItems.find {
      boCartItem => boCartItem.uuid == boCartItemUuid
    }
    boCartItemOpt shouldBe defined
    val boCartItem = boCartItemOpt.get
    boCartItem shouldBe a[BOCartItem]
    boCartItem.bOReturnedItems should have size (1)
    boCartItem.bOReturnedItems(0)
  }

  private def assertOrder13200(order: JValue) = {
    order \ "uuid" should equal(JString("e8be061f-f036-4696-bed3-8e529c95eb27"))
    order \ "transactionUUID" should equal(JString("e8be061f-f036-4696-bed3-8e529c95eb27"))
    order \ "authorizationId" should equal(JString(""))
    order \ "amount" should equal(JInt(13200))
    order \ "status" should equal(JString("PAYMENT_CONFIRMED"))
    order \ "email" should equal(JString("client@merchant.com"))
    order \ "extra" should equal(JNothing)
    order \ "vendor" should equal(JNothing)

    val deliveryStatus = checkJArray(order \ "deliveryStatus")
    deliveryStatus should have size (1)
    deliveryStatus(0) should equal(JString("NOT_STARTED"))
  }

  private def assertOrder20040(order: JValue) = {
    order \ "uuid" should equal(JString("dcacac6e-720f-4715-b5d7-ed6ed4431ab2"))
    order \ "transactionUUID" should equal(JString("dcacac6e-720f-4715-b5d7-ed6ed4431ab2"))
    order \ "authorizationId" should equal(JString(""))
    order \ "amount" should equal(JInt(20040))
    order \ "status" should equal(JString("PAYMENT_CONFIRMED"))
    order \ "email" should equal(JString("client@merchant.com"))
    order \ "extra" should equal(JNothing)
    order \ "vendor" should equal(JNothing)

    val deliveryStatus = checkJArray(order \ "deliveryStatus")
    deliveryStatus should have size (2)
    deliveryStatus(0) should equal(JString("NOT_STARTED"))
    deliveryStatus(1) should equal(JString("NOT_STARTED"))
  }

  private def assertOrder6000(order: JValue) = {
    order \ "uuid" should equal(JString("c71f721d-1bcc-4858-85e9-5b70169d4c7e"))
    order \ "transactionUUID" should equal(JString("c71f721d-1bcc-4858-85e9-5b70169d4c7e"))
    order \ "authorizationId" should equal(JString(""))
    order \ "amount" should equal(JInt(6000))
    order \ "status" should equal(JString("PAYMENT_CONFIRMED"))
    order \ "email" should equal(JString("client@merchant.com"))
    order \ "extra" should equal(JNothing)
    order \ "vendor" should equal(JNothing)

    val deliveryStatus = checkJArray(order \ "deliveryStatus")
    deliveryStatus should have size (1)
    deliveryStatus(0) should equal(JString("NOT_STARTED"))
  }
}
