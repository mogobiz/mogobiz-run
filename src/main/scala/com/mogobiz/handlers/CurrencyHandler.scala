package com.mogobiz.handlers

import com.mogobiz.es.ElasticSearchClient
import org.json4s.JsonAST.JValue

class CurrencyHandler {

  def queryCurrency(storeCode: String, lang: String): JValue = {
    ElasticSearchClient.queryCurrencies(storeCode,lang)
  }
}
