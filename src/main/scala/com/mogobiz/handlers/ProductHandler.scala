package com.mogobiz.handlers

import com.mogobiz._
import org.json4s.JsonAST.JValue
import scala.concurrent.duration._
import scala.concurrent.Await
import com.mogobiz.ProductRequest
import com.mogobiz.CompareProductParameters
import com.mogobiz.FullTextSearchProductParameters
import scala.util.{Failure, Success}


class ProductHandler {
  val esClient = new ElasticSearchClient

  def queryProductsByCriteria(storeCode: String, productRequest: ProductRequest): JValue = {
    //TODO with Elastic4s
    val response = esClient.queryProductsByCriteria(storeCode,productRequest)
    Await.result(response, 10 seconds)
  }
  
  def queryProductsByFulltextCriteria(storeCode: String, params: FullTextSearchProductParameters): JValue = {
    //TODO with Elastic4s
    val response = esClient.queryProductsByFulltextCriteria(storeCode,params)
    Await.result(response, 10 seconds)
  }

  def getProductsFeatures(storeCode: String, params: CompareProductParameters): JValue = {
    //TODO with Elastic4s
    val response = esClient.getProductsFeatures(storeCode, params)
    Await.result(response, 10 seconds)
  }

  def getProductDetails(storeCode: String, params: ProductDetailsRequest, productId: Long, uuid: String): JValue = {
    //TODO with Elastic4s
    val response = esClient.queryProductDetails(storeCode, params, productId, uuid)
    Await.result(response, 10 seconds)
  }

}
