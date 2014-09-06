package com.mogobiz.handlers

import com.mogobiz._
import org.json4s.JsonAST.JValue
import org.json4s._
import scala.concurrent.duration._
import scala.concurrent.{Future, Await}
import com.mogobiz.ProductRequest
import com.mogobiz.CompareProductParameters
import com.mogobiz.FullTextSearchProductParameters
import scala.util.{Failure, Success}
import com.mogobiz.vo.{CommentGetRequest, Paging, CommentRequest, Comment}


class ProductHandler {

  def queryProductsByCriteria(storeCode: String, productRequest: ProductRequest): JValue = {
    ElasticSearchClient.queryProductsByCriteria(storeCode,productRequest)
  }
  
  def queryProductsByFulltextCriteria(storeCode: String, params: FullTextSearchProductParameters): JValue = {
    //TODO with Elastic4s
    val response = ElasticSearchClient.queryProductsByFulltextCriteria(storeCode,params)
    Await.result(response, 10 seconds)
  }

  def getProductsFeatures(storeCode: String, params: CompareProductParameters): JValue = {
    //TODO with Elastic4s
    val response = ElasticSearchClient.getProductsFeatures(storeCode, params)
    Await.result(response, 10 seconds)
  }

  def getProductDetails(storeCode: String, params: ProductDetailsRequest, productId: Long, uuid: String): JValue = {
    //TODO with Elastic4s
    val response = ElasticSearchClient.queryProductDetails(storeCode, params, productId, uuid)
    Await.result(response, 10 seconds)
  }

  def getProductDates(storeCode: String, params: ProductDatesRequest, productId: Long, uuid: String): JValue = {
    //TODO with Elastic4s
    val response = ElasticSearchClient.queryProductDates(storeCode, productId, params)
    Await.result(response, 10 seconds)
  }

  def getProductTimes(storeCode: String, params: ProductTimesRequest, productId: Long, uuid: String): JValue = {
    //TODO with Elastic4s
    val response = ElasticSearchClient.queryProductTimes(storeCode, productId, params)
    Await.result(response, 10 seconds)
  }

  def updateComment(storeCode: String, productId: Long, commentId: String, useful: Boolean): Boolean = {
    //TODO with Elastic4s
    val response = ElasticSearchClient.updateComment(storeCode, productId,commentId,useful)
    Await.result(response, 10 seconds)
  }

  def createComment(storeCode: String, productId: Long, req: CommentRequest): Comment = {
    //TODO with Elastic4s
    val response = ElasticSearchClient.createComment(storeCode, productId, req)
    Await.result(response, 10 seconds)
  }

  def getComment(storeCode: String, productId: Long, req: CommentGetRequest): Paging[Comment] = {
    //TODO with Elastic4s
    val response = ElasticSearchClient.getComments(storeCode, productId, req)
    Await.result(response, 10 seconds)
  }

  def getProductHistory(storeCode: String, req: VisitorHistoryRequest, uuid: String): List[JValue] = {
    //TODO with Elastic4s
    val response = ElasticSearchClient.getProductHistory(storeCode, uuid)
    val ids = Await.result(response, 10 seconds)
      if (ids.isEmpty) {
        List()
      } else {
        val future = ElasticSearchClient.getProductsByIds(storeCode,ids,ProductDetailsRequest(false, None, req.currency, req.country, req.lang))
        val products = Await.result(future, 10 seconds)
        println("visitedProductsRoute returned results", products.length)
        products
      }
    
  }
}
