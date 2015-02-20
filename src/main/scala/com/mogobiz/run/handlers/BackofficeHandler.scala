package com.mogobiz.run.handlers

import com.mogobiz.es.EsClient
import com.mogobiz.pay.config.MogopayHandlers._
import com.mogobiz.pay.model.Mogopay.RoleName
import com.mogobiz.pay.settings.Settings
import com.mogobiz.run.exceptions.NotAuthorizedException
import com.mogobiz.run.model.RequestParameters.BOListOrderRequest
import com.mogobiz.run.utils.Paging
import com.sksamuel.elastic4s.ElasticDsl._
import com.mogobiz.run.es._
import com.sksamuel.elastic4s.FilterDefinition
import org.elasticsearch.search.sort.SortOrder
import org.json4s.JsonAST._

/**
 * Created by yoannbaudy on 19/02/2015.
 */
class BackofficeHandler {

  private val mogopayIndex = Settings.Mogopay.EsIndex

  def getBOCartIndex(storeCode: String) = s"${storeCode}_bo"

  def listOrders(storeCode: String, accountUuid: Option[String], req: BOListOrderRequest) : Paging[JValue] = {
    val account = accountUuid.map { uuid => accountHandler.load(uuid) }.flatten getOrElse(throw new NotAuthorizedException(""))
    val customer = account.roles.find{role => role == RoleName.CUSTOMER}.map{r => account}
    val merchant = account.roles.find{role => role == RoleName.MERCHANT}.map{r => account}

    val boCartFilters = List(
      createTermFilter("status", req.transactionStatus),
      createTermFilter("cartItems.bODelivery.status", req.deliveryStatus)
    ).flatten

    val boCartTransactionUuidList = if (boCartFilters.nonEmpty) {
      Some((EsClient.searchAllRaw(buildSearchQuery(getBOCartIndex(storeCode), "BOCart", boCartFilters)) hits() map { hit =>
        (hit2JValue(hit) \ "transactionUuid") match {
          case JString(uuid) => Some(uuid)
          case _ => None
        }
      }).toList.flatten.mkString("|"))
    }
    else None
    val filters = List(
      req.lastName.map {lastName => or(createTermFilter("customer.lastName", Some(lastName)).get, createTermFilter("customer.firstName", Some(lastName)).get) },
      createTermFilter("email", req.email),
      createTermFilter("customer.uuid", customer.map {c => c.uuid}),
      createTermFilter("vendor.uuid", merchant.map {m => m.uuid}),
      createRangeFilter("endDate", req.startDate, req.endDate),
      createTermFilter("amount", req.price),
      createOrFilterBySplitValues(boCartTransactionUuidList, v => createTermFilter("transactionUUID", Some(v)))
    ).flatten

    val _size: Int = req.maxItemPerPage.getOrElse(100)
    val _from: Int = req.pageOffset.getOrElse(0) * _size

    val response = EsClient.searchAllRaw(
      buildSearchQuery(mogopayIndex, "BOTransaction", filters)
      from _from
      size _size
      sort {by field "transactionDate" order SortOrder.DESC})

    Paging.build(req, response, {hit =>
      hit.transform(completeBOTransactionWithDeliveryStatus(storeCode)).transformField(filterBOTransactionField)
    })
  }

  private def buildSearchQuery(indexName: String, searchType: String, filters: List[FilterDefinition]) = {
    if (filters.size > 0) search in indexName types searchType filter and(filters: _*)
    else search in indexName types searchType filter filters(0)
  }

  private def completeBOTransactionWithDeliveryStatus(storeCode: String) : PartialFunction[JValue, JValue] = {
    case obj : JObject => {
      (obj \ "transactionUUID") match {
        case (JString(transactionUuid)) => {
          val filters = List(createTermFilter("transactionUuid", Some(transactionUuid))).flatten
          val statusList = (EsClient.searchAllRaw(buildSearchQuery(getBOCartIndex(storeCode), "BOCart", filters)) hits() map { hit =>
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

          (obj ++ JObject(JField("deliveryStatus", JArray(statusList))))
        }
        case _ => obj
      }
    }
  }

  private val filterBOTransactionField : PartialFunction[JField, JField] = {
    case JField("extra", v: JValue) => JField("vendor", JNothing)
    case JField("vendor", v: JValue) => JField("vendor", JNothing)
  }
}
