/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import com.mogobiz.es.EsClient
import com.mogobiz.pay.config.Settings
import com.mogobiz.run.es._
import com.mogobiz.run.exceptions.NotFoundException
import com.sksamuel.elastic4s.ElasticDsl.{search => esearch4s, _}
import com.sksamuel.elastic4s.FilterDefinition
import org.json4s.JsonAST.JValue
import com.mogobiz.es._

class CurrencyHandler {

  def queryCurrencyByCode(storeCode: String, currencyCode: String): JValue = {
    val filters: List[FilterDefinition] = List(termFilter("currencyCode", currencyCode))
    val req                             = esearch4s in Settings.Mogopay.EsIndex -> "Rate"
    EsClient
      .searchRaw(filterRequest(req, filters) sourceExclude "imported")
      .map { hit =>
        hit2JValue(hit)
      }
      .getOrElse(throw new NotFoundException(""))
  }

  def queryCurrency(storeCode: String, lang: String): JValue = {
    EsClient
      .searchAllRaw(
          esearch4s in Settings.Mogopay.EsIndex -> "Rate" sourceExclude (createExcludeLang(storeCode, lang) :+ "imported": _*)
      )
      .getHits
  }
}
