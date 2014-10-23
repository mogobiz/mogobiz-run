package com.mogobiz.handlers

import com.mogobiz.es._
import com.mogobiz.es.EsClient._
import com.sksamuel.elastic4s.ElasticDsl.{search => esearch4s, _}
import org.json4s.JsonAST.JValue


class TagHandler {

  def queryTags(storeCode: String, hidden: Boolean, inactive: Boolean, lang: String): JValue = {
    EsClient.searchAllRaw(
      esearch4s in storeCode -> "tag" sourceExclude(createExcludeLang(storeCode, lang) :+ "imported" :_*)
    ).getHits
  }

}
