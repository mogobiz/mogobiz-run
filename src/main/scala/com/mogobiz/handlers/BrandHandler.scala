package com.mogobiz.handlers

import com.mogobiz.{BrandRequest, ElasticSearchClient}
import org.json4s.JsonAST.JValue
import org.json4s.native.JsonMethods._
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global


class BrandHandler {
  val esClient = new ElasticSearchClient

  def queryBrand(storeCode: String, hidden: Boolean, categoryPath: Option[String], lang: String): JValue = {
    //TODO with Elastic4s
    val response = esClient.queryBrands(storeCode, new BrandRequest(hidden, categoryPath, lang))
    Await.result(response, 10 seconds)
  }
}
