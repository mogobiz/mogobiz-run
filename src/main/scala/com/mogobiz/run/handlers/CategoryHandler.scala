/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import com.mogobiz.es.{EsClient, _}
import com.mogobiz.json.JsonUtil
import com.mogobiz.run.es._
import com.mogobiz.run.exceptions.NotFoundException
import com.sksamuel.elastic4s.http.ElasticDsl.{search => esearch4s, _}
import com.sksamuel.elastic4s.searches.SearchDefinition
import com.sksamuel.elastic4s.searches.queries.QueryDefinition
import org.elasticsearch.search.sort.SortOrder
import org.json4s.JsonAST.JValue
import org.json4s.JsonDSL._
import org.json4s._

class CategoryHandler extends JsonUtil {

  def queryCategory(store: String, categoryId: String): JValue = {
    var filters: List[QueryDefinition] = List(termQuery("id", categoryId))
    val req = esearch4s(store -> "category")
    EsClient
      .searchRaw(filterRequest(req, filters) sourceExclude ("imported"))
      .getOrElse(throw new NotFoundException(""))
  }

  def queryCategories(store: String,
                      hidden: Boolean,
                      parentId: Option[String],
                      brandId: Option[String],
                      categoryPath: Option[String],
                      lang: String,
                      promotionId: Option[String],
                      size: Option[Int]): JValue = {
    var filters: List[QueryDefinition] = List.empty
    val _size = size.getOrElse(Int.MaxValue / 2)

    def results(req: SearchDefinition): JValue =
      EsClient
        .searchAllRaw(filterRequest(req, filters) sourceExclude (createExcludeLang(store, lang) :+ "imported"))
        .hits

    brandId match {
      case Some(s) =>
        filters +:= termQuery("brand.id", s)
        if (!hidden) filters +:= termQuery("category.hide", "false")
        if (parentId.isDefined) filters +:= termQuery("category.parentId", parentId.get)
        if (categoryPath.isDefined) filters +:= createRegexFilter("category.path", categoryPath, false).get
        if (promotionId.isDefined) filters +:= createtermQuery("category.categoryCoupons", promotionId).get
        distinctById(results(esearch4s(store -> "product") from 0 size _size sortByFieldAsc ("position")) \ "category")
      case None =>
        if (!hidden) filters +:= termQuery("hide", "false")
        if (parentId.isDefined) filters +:= termQuery("parentId", parentId.get)
        if (categoryPath.isDefined) filters +:= createRegexFilter("path", categoryPath, false).get
        else if (!parentId.isDefined) filters +:= not(existsQuery("parentId")) //  existence true includeNull true
        if (promotionId.isDefined) filters +:= createtermQuery("categoryCoupons", promotionId).get
        results(esearch4s(store -> "category") from 0 size _size sortByFieldAsc ("position"))
    }
  }
}
