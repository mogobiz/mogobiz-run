/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import com.mogobiz.es.{EsClient, _}
import com.mogobiz.run.es._
import com.mogobiz.run.exceptions.NotFoundException
import com.sksamuel.elastic4s.http.ElasticDsl.{search => esearch4s, _}
import com.sksamuel.elastic4s.searches.queries.QueryDefinition
import org.json4s.JsonAST.JValue

class TagHandler {

  def queryTagId(store: String, tagId: String): JValue = {
    var filters: List[QueryDefinition] = List(termQuery("id", tagId))
    val req = esearch4s(store -> "tag")
    EsClient
      .searchRaw(filterRequest(req, filters) sourceExclude ("imported"))
      .map { hit =>
        hit
      }
      .getOrElse(throw new NotFoundException(""))
  }

  def queryTagName(store: String, tagName: String): JValue = {
    var filters: List[QueryDefinition] = List(termQuery("name", tagName))
    val req = esearch4s(store -> "tag")
    EsClient
      .searchRaw(filterRequest(req, filters) sourceExclude ("imported"))
      .getOrElse(throw new NotFoundException(""))
  }

  def queryTags(storeCode: String, hidden: Boolean, inactive: Boolean, lang: String, size: Option[Int]): JValue = {
    val _size = size.getOrElse(Int.MaxValue / 2)
    EsClient
      .searchAllRaw(
          esearch4s(storeCode -> "tag") from 0 size _size sourceExclude (createExcludeLang(storeCode, lang) :+ "imported")
      )
      .hits
  }

}
