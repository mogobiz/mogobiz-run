/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import java.util.Date

import com.mogobiz.es._
import com.mogobiz.run.es._
import com.mogobiz.run.exceptions.NotFoundException
import com.mogobiz.run.model.RequestParameters.PromotionRequest
import com.mogobiz.run.utils.Paging
import com.sksamuel.elastic4s.http.ElasticDsl.{search => esearch4s, _}
import com.sksamuel.elastic4s.http.search.SearchHits
import com.sksamuel.elastic4s.searches.queries.QueryDefinition
import com.sksamuel.elastic4s.searches.sort.FieldSortDefinition
import org.elasticsearch.search.sort.SortOrder
import org.joda.time.format.DateTimeFormat
import org.json4s.JsonAST.{JArray, JValue}
import org.json4s._

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
              esearch4s(storeCode -> "category"),
              List(
                  createRegexFilter("path", req.categoryPath)
              ).flatten
          ) sourceInclude "coupons"
      ) match {
        case Some(s) => s
        case None    => JNothing
      }

      implicit def json4sFormats: Formats = DefaultFormats

      idsQuery(
          (categories \ "coupons")
            .extract[JArray]
            .values
            .asInstanceOf[List[List[BigInt]]]
            .flatten
            .map(coupon => coupon.toString())
      )
    } else {
      matchAllQuery()
    }
    val now = DateTimeFormat.forPattern("yyyy-MM-dd").print(new Date().getTime)
    val filters: List[QueryDefinition] = List(
        termQuery("anonymous", true),
        should(rangeQuery("startDate") lte now, not(existsQuery("startDate"))),
        should(rangeQuery("endDate") gte now, not(existsQuery("endDate")))
    )

    EsClient.searchAllRaw(
        filterRequest(esearch4s(storeCode -> "coupon"), filters, _query)
          from _from
          size _size
          sortBy {
            FieldSortDefinition(_sort).sortOrder(SortOrder.fromString(_sortOrder))
          } fetchSource includeSource
    )
  }

  def getPromotions(storeCode: String, req: PromotionRequest): JValue = {
    val response: SearchHits = this.queryPromotion(storeCode, req)
    Paging.wrap(response.total, response.hits, response.hits.children.size, req)
  }

  def getPromotionById(storeCode: String, promotionId: String): JValue = {
    var filters: List[QueryDefinition] = List(termQuery("id", promotionId))
    val req = esearch4s(storeCode -> "coupon")
    EsClient.searchRaw(filterRequest(req, filters) sourceExclude "imported").getOrElse(throw new NotFoundException(""))
  }

  def getPromotionIds(storeCode: String): Array[String] = {
    val response = this.queryPromotion(storeCode, PromotionRequest(None, None, None, None, None, ""), false)
    response.hits.map(_.id)
  }
}
