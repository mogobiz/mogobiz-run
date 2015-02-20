package com.mogobiz.run.handlers

import com.mogobiz.MogobizRouteTest
import com.mogobiz.run.model.RequestParameters.BOListOrderRequest
import org.json4s.JsonAST._

/**
 * Created by yoannbaudy on 19/02/2015.
 */
class BackofficeHandlerSpec extends MogobizRouteTest {

  val handler = new BackofficeHandler
  val storeCode = "acmesport"
  val merchantUuid = "d7b864c8-4567-4603-abd4-5f85e9ff56e6"

  "BackofficeHandler" should {

    "retrieve orders by lastName with authenticate merchant" in {
      val req = BOListOrderRequest(maxItemPerPage = Some(1), pageOffset = Some(1), lastName = Some("TEST"))
      val result = handler.listOrders(storeCode, Some(merchantUuid), req)
      result.hasNext must beTrue
      result.hasPrevious must beFalse
      result.maxItemsPerPage mustEqual(1)
      result.pageCount mustEqual(4)
      result.pageOffset mustEqual(1)
      result.pageSize mustEqual(1)
      result.totalCount mustEqual(3)
      result.list must size(1)

      val order = result.list(0)
      order \ "uuid" must be_==(JString("931eedc2-a4cd-431f-ba9c-aba4ed68806c"))
      order \ "transactionUUID" must be_==(JString("931eedc2-a4cd-431f-ba9c-aba4ed68806c"))
      order \ "authorizationId" must be_==(JString("2827366219624362032"))
      order \ "amount" must be_==(JInt(1188000))
      order \ "status" must be_==(JString("PAYMENT_CONFIRMED"))
      order \ "email" must be_==(JString("client@merchant.com"))
      order \ "extra" must be_==(JNothing)
      order \ "vendor" must be_==(JNothing)

      val deliveryStatus = checkJArray(order \ "deliveryStatus")
      deliveryStatus must size(1)
      deliveryStatus(0) \ "status" must be_==(JString("NOT_STARTED"))
    }

  }

}
