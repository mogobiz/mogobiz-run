package com.mogobiz.handlers

import com.mogobiz.ElasticSearchClient
import org.json4s.JsonAST.JValue
import org.json4s.native.JsonMethods._
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global


class CountryHandler {

  def queryCountries(storeCode: String, lang: String): JValue = {
    ElasticSearchClient.queryCountries(storeCode, lang)
  }
}
