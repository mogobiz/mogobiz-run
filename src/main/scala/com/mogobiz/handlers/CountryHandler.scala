package com.mogobiz.handlers

import com.mogobiz.es.ElasticSearchClient
import org.json4s.JsonAST.JValue


class CountryHandler {

  def queryCountries(storeCode: String, lang: String): JValue = {
    ElasticSearchClient.queryCountries(storeCode, lang)
  }
}
