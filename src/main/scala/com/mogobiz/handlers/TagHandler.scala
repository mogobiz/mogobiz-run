package com.mogobiz.handlers

import com.mogobiz.es.ElasticSearchClient
import org.json4s.JsonAST.JValue


class TagHandler {

  def queryTags(storeCode: String, hidden: Boolean, inactive: Boolean, lang: String): JValue = {
    ElasticSearchClient.queryTags(storeCode, hidden, inactive, lang)
  }

}
