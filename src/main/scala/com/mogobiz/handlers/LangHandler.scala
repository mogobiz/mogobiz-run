package com.mogobiz.handlers

import com.mogobiz.es.ElasticSearchClient
import org.json4s.JsonAST.JValue


class LangHandler {

  def queryLang(storeCode: String): JValue = {
    ElasticSearchClient.queryStoreLanguages(storeCode)
  }
}
