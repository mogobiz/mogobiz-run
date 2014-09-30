package com.mogobiz.handlers

import com.mogobiz.es._
import com.mogobiz.es.EsClient._
import com.mogobiz.json.JsonUtil
import com.sksamuel.elastic4s.ElasticDsl.{search => esearch4s, _}
import com.sksamuel.elastic4s.FilterDefinition
import org.elasticsearch.search.sort.SortOrder
import org.json4s.JsonAST.JArray
import org.json4s.JsonDSL._
import org.json4s._

class BrandHandler extends JsonUtil {

  def queryBrands(storeCode: String, hidden: Boolean, categoryPath: Option[String], lang: String, promotionId:Option[String]): JValue = {
    var filters:List[FilterDefinition] = List.empty
    categoryPath match {
      case Some(s) =>
        val req = esearch4s in storeCode -> "product"
        filters :+= regexFilter("category.path", s".*${s.toLowerCase}.*")
        if(promotionId.isDefined) filters +:= createTermFilter("category.coupons", promotionId).get
        if(!hidden) filters :+= termFilter("brand.hide", "false")
        val r : JArray = EsClient.searchAllRaw(
          filterRequest(req, filters)
            sourceInclude "brand.*"
            sourceExclude(createExcludeLang(storeCode, lang) :+ "brand.imported" :_*)
            sort {by field "brand.name" order SortOrder.ASC}
        ).getHits
        distinctById(r \ "brand")
      case None =>
        val req = esearch4s in storeCode -> "brand"
        if(!hidden) filters :+= termFilter("hide", "false")
        val r : JArray = EsClient.searchAllRaw(
          filterRequest(req, filters)
            sourceExclude(createExcludeLang(storeCode, lang) :+ "imported" :_*)
            sort {by field "name" order SortOrder.ASC}
        ).getHits
        r
    }
  }
}
