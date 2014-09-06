package com.mogobiz.handlers

import com.mogobiz.{CurrencyRequest, ElasticSearchClient}
import org.json4s.JsonAST.JValue
import org.json4s.native.JsonMethods._
import scala.concurrent.duration._
import scala.concurrent.Await


class CurrencyHandler {

  def queryCurrency(storeCode: String, lang: String): JValue = {
    //TODO with Elastic4s
    val response = ElasticSearchClient.queryCurrencies(storeCode,lang)
    Await.result(response, 10 seconds)
  }
}
