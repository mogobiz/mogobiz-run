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


class CountryHandler {

  def queryCountryByCode(storeCode: String, countryCode: String): JValue = {
    var filters:List[FilterDefinition] = List(termFilter("code", countryCode))
    val req = esearch4s in Settings.Mogopay.EsIndex -> "Country"
    EsClient.searchRaw(filterRequest(req, filters) sourceExclude("imported")).map { hit =>
      hit2JValue(hit)
    }.getOrElse(throw new NotFoundException(""))
  }

  def queryCountries(storeCode: String, lang: String): JValue = {
    EsClient.searchAllRaw(
      esearch4s in Settings.Mogopay.EsIndex -> "Country"
    ).getHits
  }
}
