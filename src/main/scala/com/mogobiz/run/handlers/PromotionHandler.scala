package com.mogobiz.run.handlers

import java.text.SimpleDateFormat
import java.util.Date

import com.mogobiz.run.es._
import com.mogobiz.run.es.EsClientOld
import EsClientOld._
import com.mogobiz.run.model.Promotion._
import com.mogobiz.run.vo.Paging
import com.sksamuel.elastic4s.ElasticDsl.{search => esearch4s, _}
import com.sksamuel.elastic4s.FilterDefinition
import org.elasticsearch.search.SearchHits
import org.elasticsearch.search.sort.SortOrder
import org.json4s.JsonAST.{JArray, JNothing, JValue}
import org.json4s._

class PromotionHandler {

  def queryPromotion(storeCode: String, req: PromotionRequest): JValue = {
    val _size: Int = req.maxItemPerPage.getOrElse(100)
    val _from: Int = req.pageOffset.getOrElse(0) * _size
    val _sort = req.orderBy.getOrElse("startDate")
    val _sortOrder = req.orderDirection.getOrElse("asc")
    val _query =
      if(req.categoryPath.isDefined){
        val categories:JValue = EsClientOld.searchRaw(
          filterRequest(
            esearch4s in storeCode -> "category", List(
              createRegexFilter("path", req.categoryPath)
            ).flatten
          ) sourceInclude "coupons"
        ) match {
          case Some(s) => s
          case None => JNothing
        }
        implicit def json4sFormats: Formats = DefaultFormats
        ids(
          (categories  \ "coupons").extract[JArray].values.asInstanceOf[List[List[BigInt]]].flatten.map(coupon=>coupon.toString()):_*
        )
      }
      else{
        matchall
      }
    val now = new SimpleDateFormat("yyyy-MM-dd").format(new Date())
    val filters:List[FilterDefinition] = List(
      termFilter("anonymous", true),
      termFilter("active", true),
      rangeFilter("startDate") lte now,
      rangeFilter("endDate") gte now
    )
    val response:SearchHits = EsClientOld.searchAllRaw(
      filterRequest(esearch4s in storeCode -> "coupon", filters, _query)
        from _from
        size _size
        sort {
        by field _sort order SortOrder.valueOf(_sortOrder.toUpperCase)
      }
    )
    Paging.wrap(response.getTotalHits.toInt, response.getHits, req)
  }
}
