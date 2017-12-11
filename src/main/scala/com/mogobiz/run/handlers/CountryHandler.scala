/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import com.mogobiz.es.{EsClient, _}
import com.mogobiz.pay.config.Settings
import com.mogobiz.run.es._
import com.mogobiz.run.exceptions.NotFoundException
import com.sksamuel.elastic4s.http.ElasticDsl.{search => esearch4s, _}
import com.sksamuel.elastic4s.searches.queries.QueryDefinition
import org.json4s.JsonAST.JValue

class CountryHandler {

  def queryCountryByCode(storeCode: String, countryCode: String): JValue = {
    val filters: List[QueryDefinition] = List(termQuery("code", countryCode))
    val req                            = esearch4s(Settings.Mogopay.EsIndex -> "Country")
    EsClient
      .searchRaw(filterRequest(req, filters) sourceExclude ("imported"))
      .getOrElse(throw new NotFoundException(""))
  }

  def queryCountries(storeCode: String, lang: String): JValue = {
    EsClient
      .searchAllRaw(
          esearch4s(Settings.Mogopay.EsIndex -> "Country")
      )
      .hits
  }
}
