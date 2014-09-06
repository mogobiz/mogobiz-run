package com.mogobiz.handlers

import com.mogobiz.ElasticSearchClient
import org.json4s.JsonAST.JValue
import scala.concurrent.duration._
import scala.concurrent.Await


class LangHandler {

  def queryLang(storeCode: String): JValue = {
    //TODO with Elastic4s
    val response = ElasticSearchClient.queryStoreLanguages(storeCode)
    Await.result(response, 10 seconds)
  }
}
