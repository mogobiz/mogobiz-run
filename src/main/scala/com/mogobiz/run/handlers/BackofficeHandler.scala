package com.mogobiz.run.handlers

import java.text.DateFormat
import java.util.Locale

import com.mogobiz.es.EsClient
import com.mogobiz.pay.config.MogopayHandlers._
import com.mogobiz.pay.model.Mogopay.RoleName
import com.mogobiz.pay.settings.Settings
import com.mogobiz.run.exceptions.{NotFoundException, NotAuthorizedException}
import com.mogobiz.run.model.RequestParameters.{BOListCustomersRequest, BOListOrdersRequest}
import com.mogobiz.run.utils.Paging
import com.sksamuel.elastic4s.ElasticDsl._
import com.mogobiz.run.es._
import org.elasticsearch.common.joda.time.format.ISODateTimeFormat
import org.elasticsearch.search.SearchHit
import org.elasticsearch.search.sort.SortOrder
import org.joda.time.DateTime
import org.json4s.JsonAST._
import com.mogobiz.json.JsonUtil

/**
 * Created by yoannbaudy on 19/02/2015.
 */
class BackofficeHandler extends JsonUtil {

  private val mogopayIndex = Settings.Mogopay.EsIndex

  def getBOCartIndex(storeCode: String) = s"${storeCode}_bo"

  @throws[NotAuthorizedException]
  def listOrders(storeCode: String, accountUuid: Option[String], req: BOListOrdersRequest) : Paging[JValue] = {
    val account = accountUuid.map { uuid => accountHandler.load(uuid) }.flatten getOrElse(throw new NotAuthorizedException(""))
    val customer = account.roles.find{role => role == RoleName.CUSTOMER}.map{r => account}
    val merchant = account.roles.find{role => role == RoleName.MERCHANT}.map{r => account}

    val boCartTransactionUuidList = req.deliveryStatus.map { deliveryStatus =>
      (EsClient.searchAllRaw(search in getBOCartIndex(storeCode) types "BOCart" sourceInclude "transactionUuid" query matchQuery("cartItems.bODelivery.status", deliveryStatus)) hits() map { hit =>
        (hit2JValue(hit) \ "transactionUuid") match {
          case JString(uuid) => {
            Some(uuid)
          }
          case _ => None
        }
      }).toList.flatten.distinct.mkString("|")
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

    Paging.build(_size, _from, response, {hit =>
      hit.transform(completeBOTransactionWithDeliveryStatus(storeCode)).transformField(filterBOTransactionField)
    })
  }

  @throws[NotAuthorizedException]
  def listCustomers(storeCode: String, accountUuid: Option[String], req: BOListCustomersRequest) : Paging[JValue] = {
    val merchant = accountUuid.map { uuid =>
      accountHandler.load(uuid).map { account =>
        account.roles.find{role => role == RoleName.MERCHANT}.map{r => account}
      }.flatten
    }.flatten getOrElse(throw new NotAuthorizedException(""))

    val filters = List(
      req.lastName.map {lastName => or(createTermFilter("customer.lastName", Some(lastName)).get, createTermFilter("customer.firstName", Some(lastName)).get) },
      createTermFilter("email", req.email),
      createTermFilter("vendor.uuid", Some(merchant.uuid))
    ).flatten

    val customerUuidList = EsClient.searchAllRaw(search in mogopayIndex types "BOTransaction" sourceInclude "customer.uuid" filter and(filters : _*)).hits().map {hit =>
      (hit2JValue(hit) \ "customer" \ "uuid") match {
        case JString(uuid) => {
          Some(uuid)
        }
        case _ => None
      }
    }.toList.flatten.distinct.mkString("|")

    val _size: Int = req.maxItemPerPage.getOrElse(100)
    val _from: Int = req.pageOffset.getOrElse(0) * _size

    val response = EsClient.searchAllRaw(
      search in mogopayIndex types "Account" filter createOrFilterBySplitValues(Some(customerUuidList), v => createTermFilter("uuid", Some(v))).get
        from _from
        size _size
        sort {by field "lastName" order SortOrder.DESC})

    Paging.build(_size, _from, response, {hit => hit})
  }

  @throws[NotFoundException]
  def cartDetails(storeCode: String, accountUuid: Option[String], transactionUuid: String) : JValue = {
    val account = accountUuid.map { uuid => accountHandler.load(uuid) }.flatten getOrElse(throw new NotAuthorizedException(""))
    val customer = account.roles.find{role => role == RoleName.CUSTOMER}.map{r => account}
    val merchant = account.roles.find{role => role == RoleName.MERCHANT}.map{r => account}

    val transactionFilters = List(
      createTermFilter("customer.uuid", customer.map {c => c.uuid}),
      createTermFilter("vendor.uuid", merchant.map {m => m.uuid}),
      createTermFilter("transactionUUID", Some(transactionUuid))
    ).flatten

    val transactionRequest = search in mogopayIndex types "BOTransaction" sourceInclude "transactionUUID" filter and(transactionFilters : _*)
    val esTransactionUuid = EsClient.searchRaw(transactionRequest).map {hit =>
      (hit2JValue(hit) \ "transactionUUID") match {
        case JString(uuid) => {
          uuid
        }
        case _ => throw new NotFoundException("")
      }
    }.getOrElse(throw new NotFoundException(""))

    EsClient.searchRaw(
      search in getBOCartIndex(storeCode) types "BOCart" query matchQuery("transactionUuid", esTransactionUuid)
    ).getOrElse(throw new NotFoundException(""))
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
    case JField("transactionDate", JInt(v)) => JField("transactionDate", JString(formatDateTime(v)))
    case JField("creationDate", JInt(v)) => JField("creationDate", JString(formatDateTime(v)))
    case JField("dateCreated", JInt(v)) => JField("dateCreated", JString(formatDateTime(v)))
    case JField("lastUpdated", JInt(v)) => JField("lastUpdated", JString(formatDateTime(v)))
    case JField("startDate", JInt(v)) => JField("startDate", JString(formatDateTime(v)))
    case JField("endDate", JInt(v)) => JField("endDate", JString(formatDateTime(v)))
    case JField("orderDate", JInt(v)) => JField("orderDate", JString(formatDateTime(v)))
    case JField("expiryDate", JInt(v)) => JField("expiryDate", JString(formatDateTime(v)))
    case JField("birthDate", JInt(v)) => JField("birthDate", JString(formatDateTime(v)))
  }

  private def formatDateTime(value: BigInt) : String = {
    ISODateTimeFormat.dateTimeNoMillis().print(value.longValue())
  }

}