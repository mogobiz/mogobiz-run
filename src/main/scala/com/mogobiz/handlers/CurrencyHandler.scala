package com.mogobiz.handlers

import com.mogobiz.es._
import com.mogobiz.es.EsClientOld$
import com.mogobiz.es.EsClientOld._
import com.sksamuel.elastic4s.ElasticDsl.{search => esearch4s,_}
import org.json4s.JsonAST.JValue

class CurrencyHandler {

  def queryCurrency(storeCode: String, lang: String): JValue = {
    EsClientOld.searchAllRaw(
      esearch4s in storeCode -> "rate" sourceExclude(createExcludeLang(storeCode, lang) :+ "imported" :_*)
    ).getHits
  }
}
