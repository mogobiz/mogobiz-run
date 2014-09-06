package com.mogobiz.handlers

import com.mogobiz.ElasticSearchClient
import org.json4s.JsonAST.JValue


class TagHandler {
  val esClient = new ElasticSearchClient

  def queryTags(storeCode: String, hidden: Boolean, inactive: Boolean, lang: String): JValue = {
    esClient.queryTags(storeCode, hidden, inactive, lang)
  }
}
