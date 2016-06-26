/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import java.util.Date

import com.mogobiz.run.es._
import com.mogobiz.run.exceptions.NotFoundException
import com.mogobiz.run.model.RequestParameters.PromotionRequest
import com.mogobiz.run.utils.Paging
import com.sksamuel.elastic4s.ElasticDsl.{search => esearch4s, _}
import com.sksamuel.elastic4s.FilterDefinition
import org.elasticsearch.search.SearchHits
import org.elasticsearch.search.sort.SortOrder
import org.joda.time.format.DateTimeFormat
import org.json4s.JsonAST.{JArray, JValue}
import org.json4s._
import com.mogobiz.es._

class PromotionHandler {

  private val defaultMaxItemPerPage = 100

  private def queryPromotion(storeCode: String, req: PromotionRequest, includeSource: Boolean = true): SearchHits = {
    val _size: Int = req.maxItemPerPage.getOrElse(defaultMaxItemPerPage)
    val _from: Int = req.pageOffset.getOrElse(0) * _size
    val _sort      = req.orderBy.getOrElse("startDate")
    val _sortOrder = req.orderDirection.getOrElse("asc")
    val _query = if (req.categoryPath.isDefined) {
      val categories: JValue = EsClient.searchRaw(
          filterRequest(
              esearch4s in storeCode -> "category",
              List(
                  createRegexFilter("path", req.categoryPath)
              ).flatten
          ) sourceInclude "coupons"
      ) match {
        case Some(s) => s
        case None    => JNothing
      }
      implicit def json4sFormats: Formats = DefaultFormats
      ids(
          (categories \ "coupons")
            .extract[JArray]
            .values
            .asInstanceOf[List[List[BigInt]]]
            .flatten
            .map(coupon => coupon.toString()): _*
      )
    } else {
      matchall
    }
    val now = DateTimeFormat.forPattern("yyyy-MM-dd").print(new Date().getTime)
    val filters: List[FilterDefinition] = List(
        termFilter("anonymous", true),
        or(rangeFilter("startDate") lte now, missingFilter("startDate")),
        or(rangeFilter("endDate") gte now, missingFilter("endDate"))
    )

    EsClient.searchAllRaw(
        filterRequest(esearch4s in storeCode -> "coupon", filters, _query)
          from _from
          size _size
          sort {
            by field _sort order SortOrder.valueOf(_sortOrder.toUpperCase)
          } fetchSource includeSource
    )
  }

  def getPromotions(storeCode: String, req: PromotionRequest): JValue = {
    val response = this.queryPromotion(storeCode, req)
    Paging.wrap(response.getTotalHits.toInt, response.getHits, response.getHits.children.size, req)
  }

  def getPromotionById(storeCode: String, promotionId: String): JValue = {
    var filters: List[FilterDefinition] = List(termFilter("id", promotionId))
    val req = esearch4s in storeCode -> "coupon"
    EsClient
      .searchRaw(filterRequest(req, filters) sourceExclude ("imported"))
      .map { hit =>
        hit2JValue(hit)
      }
      .getOrElse(throw new NotFoundException(""))
  }

  def getPromotionIds(storeCode: String): Array[String] = {
    val response = this.queryPromotion(storeCode, new PromotionRequest(None, None, None, None, None, ""), false)
    response.hits().map { h =>
      h.getId
    }
  }
}
