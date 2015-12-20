/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import com.mogobiz.es.EsClient
import com.mogobiz.run.es._
import com.mogobiz.json.JsonUtil
import com.mogobiz.run.exceptions.NotFoundException
import com.sksamuel.elastic4s.ElasticDsl.{ search => esearch4s, _ }
import com.sksamuel.elastic4s.{ SearchDefinition, FilterDefinition }
import org.elasticsearch.search.sort.SortOrder
import org.json4s.JsonAST.JValue
import org.json4s.JsonDSL._
import org.json4s._
import com.mogobiz.es._

class CategoryHandler extends JsonUtil {

  def queryCategory(store: String, categoryId: String): JValue = {
    var filters: List[FilterDefinition] = List(termFilter("id", categoryId))
    val req = esearch4s in store -> "category"
    EsClient.searchRaw(filterRequest(req, filters) sourceExclude ("imported")).map { hit =>
      hit2JValue(hit)
    }.getOrElse(throw new NotFoundException(""))
  }

  def queryCategories(store: String, hidden: Boolean, parentId: Option[String], brandId: Option[String], categoryPath: Option[String], lang: String, promotionId: Option[String], size: Option[Int]): JValue = {
    var filters: List[FilterDefinition] = List.empty
    val _size = size.getOrElse(Integer.MAX_VALUE / 2)
    def results(req: SearchDefinition): JValue = EsClient.searchAllRaw(filterRequest(req, filters) sourceExclude (createExcludeLang(store, lang) :+ "imported": _*)).getHits
    brandId match {
      case Some(s) =>
        filters +:= termFilter("brand.id", s)
        if (!hidden) filters +:= termFilter("category.hide", "false")
        if (parentId.isDefined) filters +:= termFilter("category.parentId", parentId.get)
        if (categoryPath.isDefined) filters +:= createRegexFilter("category.path", categoryPath, false).get
        if (promotionId.isDefined) filters +:= createTermFilter("category.coupons", promotionId).get
        distinctById(results(esearch4s in store -> "product" from 0 size _size sort { by field "position" order SortOrder.ASC }) \ "category")
      case None =>
        if (!hidden) filters +:= termFilter("hide", "false")
        if (parentId.isDefined) filters +:= termFilter("parentId", parentId.get)
        if (categoryPath.isDefined) filters +:= createRegexFilter("path", categoryPath, false).get
        else if (!parentId.isDefined) filters +:= missingFilter("parentId") existence true includeNull true
        if (promotionId.isDefined) filters +:= createTermFilter("coupons", promotionId).get
        results(esearch4s in store -> "category" from 0 size _size sort { by field "position" order SortOrder.ASC })
    }
  }
}
