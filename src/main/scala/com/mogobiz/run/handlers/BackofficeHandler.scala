package com.mogobiz.run.handlers

import com.mogobiz.es.EsClient
import com.mogobiz.pay.config.MogopayHandlers._
import com.mogobiz.pay.model.Mogopay.RoleName
import com.mogobiz.pay.settings.Settings
import com.mogobiz.run.exceptions.NotAuthorizedException
import com.mogobiz.run.model.RequestParameters.{BOListCustomersRequest, BOListOrdersRequest}
import com.mogobiz.run.utils.Paging
import com.sksamuel.elastic4s.ElasticDsl._
import com.mogobiz.run.es._
import org.elasticsearch.search.sort.SortOrder
import org.json4s.JsonAST._
import com.mogobiz.json.JsonUtil

/**
 * Created by yoannbaudy on 19/02/2015.
 */
class BackofficeHandler extends JsonUtil {

  private val mogopayIndex = Settings.Mogopay.EsIndex

  def getBOCartIndex(storeCode: String) = s"${storeCode}_bo"

  def listOrders(storeCode: String, accountUuid: Option[String], req: BOListOrdersRequest) : Paging[JValue] = {
    val account = accountUuid.map { uuid => accountHandler.load(uuid) }.flatten getOrElse(throw new NotAuthorizedException(""))
    val customer = account.roles.find{role => role == RoleName.CUSTOMER}.map{r => account}
    val merchant = account.roles.find{role => role == RoleName.MERCHANT}.map{r => account}

    val boCartTransactionUuidList = req.deliveryStatus.map { deliveryStatus =>
      (EsClient.searchAllRaw(search in getBOCartIndex(storeCode) types "BOCart" query matchQuery("cartItems.bODelivery.status", deliveryStatus)) hits() map { hit =>
        (hit2JValue(hit) \ "transactionUuid") match {
          case JString(uuid) => {
            Some(uuid)
          }
          case _ => None
        }
      }).toList.flatten.mkString("|")
    }

    val filters = List(
      req.lastName.map {lastName => or(createTermFilter("customer.lastName", Some(lastName)).get, createTermFilter("customer.firstName", Some(lastName)).get) },
      createTermFilter("status", req.transactionStatus),
      createTermFilter("email", req.email),
      createTermFilter("customer.uuid", customer.map {c => c.uuid}),
      createTermFilter("vendor.uuid", merchant.map {m => m.uuid}),
      createRangeFilter("transactionDate", req.startDate, req.endDate),
      createTermFilter("amount", req.price),
      createOrFilterBySplitValues(boCartTransactionUuidList, v => createTermFilter("transactionUUID", Some(v)))
    ).flatten

    val _size: Int = req.maxItemPerPage.getOrElse(100)
    val _from: Int = req.pageOffset.getOrElse(0) * _size

    val response = EsClient.searchAllRaw(
      search in mogopayIndex types "BOTransaction" filter and(filters : _*)
      from _from
      size _size
      sort {by field "transactionDate" order SortOrder.DESC})

    Paging.build(req, response, {hit =>
      hit.transform(completeBOTransactionWithDeliveryStatus(storeCode)).transformField(filterBOTransactionField)
    })
  }

  def listCustomers(storeCode: String, accountUuid: Option[String], req: BOListCustomersRequest) = {
    val merchant = accountUuid.map { uuid =>
      accountHandler.load(uuid).map { account =>
        account.roles.find{role => role == RoleName.MERCHANT}.map{r => account}
      }.flatten
    }.flatten getOrElse(throw new NotAuthorizedException(""))

    val response = EsClient.searchAllRaw(
      search in mogopayIndex types "BOTransaction" filter createTermFilter("vendor.uuid", Some(merchant.uuid)).get
    )

    Paging.build(req, response, {hit => hit \ "customer"}, {list: List[JValue] => distinctByProperty(JArray(list), "uuid")})
  }

  private def completeBOTransactionWithDeliveryStatus(storeCode: String) : PartialFunction[JValue, JValue] = {
    case obj : JObject => {
      (obj \ "transactionUUID") match {
        case (JString(transactionUuid)) => {
          val statusList = (EsClient.searchAllRaw(search in getBOCartIndex(storeCode) types "BOCart" query matchQuery("transactionUuid", transactionUuid)) hits() map { hit =>
            hit2JValue(hit) \ "cartItems" match {
              case JArray(cartItems) => {
                cartItems.map { cartItem =>
                  cartItem \ "bODelivery" \ "status" match {
                    case JString(status) => Some(JString(status))
                    case _ => None
                  }
                }.flatten
              }
              case _ => List()
            }
          }).toList.flatten

          JObject(JField("deliveryStatus", JArray(statusList)) :: obj.obj)
        }
        case _ => obj
      }
    }
  }

  private val filterBOTransactionField : PartialFunction[JField, JField] = {
    case JField("extra", v: JValue) => JField("extra", JNothing)
    case JField("vendor", v: JValue) => JField("vendor", JNothing)
  }
}
