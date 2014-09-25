package com.mogobiz.handlers

import com.mogobiz.es._
import com.mogobiz.es.EsClient
import com.mogobiz.es.EsClient._
import com.sksamuel.elastic4s.ElasticDsl.{search => esearch4s, _}
import com.sksamuel.elastic4s.FilterDefinition
import org.json4s.JsonAST.JValue
import org.json4s._

class CategoryHandler {

  def queryCategories(store: String, hidden: Boolean, parentId: Option[String], brandId: Option[String], categoryPath: Option[String], lang: String, promotionId: Option[String]): JValue = {
    var filters:List[FilterDefinition] = if(!hidden) List(termFilter("category.hide", "false")) else List.empty
    def results(req:SearchDefinition):JValue = EsClient.searchAllRaw(filterRequest(req, filters) sourceExclude(createExcludeLang(store, lang) :+ "imported" :_*)).getHits
    brandId match {
      case Some(s) =>
        filters +:= termFilter("brand.id", s)
        if(parentId.isDefined) filters +:= termFilter("category.parentId", parentId.get)
        if(categoryPath.isDefined) filters +:= createRegexFilter("category.path", categoryPath).get
        if(promotionId.isDefined) filters +:= createTermFilter("category.coupons", promotionId).get
        results(esearch4s in store -> "product") \ "category"
      case None =>
        if(parentId.isDefined) filters +:= termFilter("parentId", parentId.get)
        else if(categoryPath.isEmpty) missingFilter("parentId") existence true includeNull true
        if(categoryPath.isDefined) filters +:= createRegexFilter("path", categoryPath).get
        if(promotionId.isDefined) filters +:= createTermFilter("coupons", promotionId).get
        results(esearch4s in store -> "category")
    }
  }
}
