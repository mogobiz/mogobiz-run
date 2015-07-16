/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import com.mogobiz.es.EsClient
import com.mogobiz.run.es._
import com.sksamuel.elastic4s.ElasticDsl.{search => esearch4s, _}
import org.json4s.JsonAST.JValue
import com.mogobiz.es._


class TagHandler {

  def queryTags(storeCode: String, hidden: Boolean, inactive: Boolean, lang: String, size: Option[Int]): JValue = {
    val _size = size.getOrElse(Integer.MAX_VALUE / 2)
    EsClient.searchAllRaw(
      esearch4s in storeCode -> "tag" from 0 size _size sourceExclude(createExcludeLang(storeCode, lang) :+ "imported" :_*)
    ).getHits
  }

}
