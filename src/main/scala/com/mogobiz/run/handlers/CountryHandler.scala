package com.mogobiz.run.handlers

import com.mogobiz.es.EsClient
import com.mogobiz.run.es._
import com.sksamuel.elastic4s.ElasticDsl.{search => esearch4s, _}
import org.json4s.JsonAST.JValue


class CountryHandler {

  def queryCountries(storeCode: String, lang: String): JValue = {
    EsClient.searchAllRaw(
      esearch4s in storeCode -> "country" sourceExclude(createExcludeLang(storeCode, lang) :+ "imported" :_*)
    ).getHits
  }
}
