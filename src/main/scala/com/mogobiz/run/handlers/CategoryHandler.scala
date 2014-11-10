package com.mogobiz.run.handlers

import com.mogobiz.run.es._
import com.mogobiz.run.es.EsClientOld
import EsClientOld._
import com.mogobiz.json.JsonUtil
import com.sksamuel.elastic4s.ElasticDsl.{search => esearch4s, _}
import com.sksamuel.elastic4s.FilterDefinition
import org.json4s.JsonAST.JValue
import org.json4s.JsonDSL._
import org.json4s._

class CategoryHandler extends JsonUtil {

  def queryCategories(store: String, hidden: Boolean, parentId: Option[String], brandId: Option[String], categoryPath: Option[String], lang: String, promotionId: Option[String]): JValue = {
    var filters:List[FilterDefinition] = List.empty
    def results(req:SearchDefinition):JValue = EsClientOld.searchAllRaw(filterRequest(req, filters) sourceExclude(createExcludeLang(store, lang) :+ "imported" :_*)).getHits
    brandId match {
      case Some(s) =>
        filters +:= termFilter("brand.id", s)
        if(!hidden) filters +:= termFilter("category.hide", "false")
        if(parentId.isDefined) filters +:= termFilter("category.parentId", parentId.get)
        if(categoryPath.isDefined) filters +:= createRegexFilter("category.path", categoryPath).get
        if(promotionId.isDefined) filters +:= createTermFilter("category.coupons", promotionId).get
        distinctById(results(esearch4s in store -> "product") \ "category")
      case None =>
        if(!hidden) filters +:= termFilter("hide", "false")
        if(parentId.isDefined) filters +:= termFilter("parentId", parentId.get)
        if(categoryPath.isDefined) filters +:= createRegexFilter("path", categoryPath).get
        else if(!parentId.isDefined) filters +:= missingFilter("parentId") existence true includeNull true
        if(promotionId.isDefined) filters +:= createTermFilter("coupons", promotionId).get
        results(esearch4s in store -> "category")
    }
  }
}