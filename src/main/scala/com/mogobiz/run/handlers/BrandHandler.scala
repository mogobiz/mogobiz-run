/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import com.mogobiz.es.{EsClient, _}
import com.mogobiz.json.JsonUtil
import com.mogobiz.run.es._
import com.mogobiz.run.exceptions.NotFoundException
import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.searches.queries.QueryDefinition
import org.json4s.JsonAST.{JArray, JValue}
import org.json4s.JsonDSL._
import org.json4s._

class BrandHandler extends JsonUtil {

  def queryBrandId(store: String, brandId: String): JValue = {
    var filters: List[QueryDefinition] = List(termQuery("id", brandId))
    val req = search(store -> "brand")
    EsClient
      .searchRaw(filterRequest(req, filters) sourceExclude ("imported"))
      .getOrElse(throw new NotFoundException(""))
  }

  def queryBrandName(store: String, brandName: String): JValue = {
    var filters: List[QueryDefinition] = List(termQuery("name.raw", brandName))
    val req = search(store -> "brand")
    EsClient
      .searchRaw(filterRequest(req, filters) sourceExclude ("imported"))
      .getOrElse(throw new NotFoundException(""))
  }

  def queryBrands(storeCode: String,
                  hidden: Boolean,
                  categoryPath: Option[String],
                  lang: String,
                  promotionId: Option[String],
                  size: Option[Int]): JValue = {
    var filters: List[QueryDefinition] = List.empty
    val _size = size.getOrElse(Int.MaxValue / 2)
    categoryPath match {
      case Some(s) =>
        val req = search(storeCode -> "product") from 0 size _size
        filters :+= regexQuery("category.path", s".*${s.toLowerCase}.*")
        if (promotionId.isDefined) filters +:= createtermQuery("category.coupons", promotionId).get
        if (!hidden) filters :+= termQuery("brand.hide", "false")
        val r: JArray = EsClient
          .searchAllRaw(
              filterRequest(req, filters)
                sourceInclude "brand.*"
                sourceExclude (createExcludeLang(storeCode, lang) :+ "brand.imported")
                sortByFieldAsc "brand.name.raw"
          )
          .hits
          .toList
        distinctById(r \ "brand")
      case None =>
        val req = search(storeCode -> "brand") from 0 size _size
        if (!hidden) filters :+= termQuery("hide", "false")
        EsClient
          .searchAllRaw(
              filterRequest(req, filters)
                sourceExclude (createExcludeLang(storeCode, lang) :+ "imported")
                sortByFieldAsc "name.raw"
          )
          .hits
          .toList
    }
  }
}
