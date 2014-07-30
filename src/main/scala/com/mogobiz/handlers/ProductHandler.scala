package com.mogobiz.handlers

import com.mogobiz.{FullTextSearchProductParameters, ProductRequest, ElasticSearchClient}
import org.json4s.JsonAST.JValue
import scala.concurrent.duration._
import scala.concurrent.Await


class ProductHandler {
  val esClient = new ElasticSearchClient

  def queryProductsByCriteria(storeCode: String, productRequest: ProductRequest): JValue = {
    //TODO with Elastic4s
    val response = esClient.queryProductsByCriteria(storeCode,productRequest)
    Await.result(response, 10 seconds)
  }
  
  def queryProductsByFulltextCriteria(storeCode: String, params: FullTextSearchProductParameters): JValue = {
    val response = esClient.queryProductsByFulltextCriteria(storeCode,params)
    Await.result(response, 10 seconds)
  }
}
