package com.mogobiz.handlers

import com.mogobiz.es.ElasticSearchClient
import com.mogobiz.model._
import org.json4s.JsonAST.JValue


class ProductHandler {

  def queryProductsByCriteria(storeCode: String, productRequest: ProductRequest): JValue = {
    ElasticSearchClient.queryProductsByCriteria(storeCode,productRequest)
  }

  def queryProductsByFulltextCriteria(storeCode: String, params: FullTextSearchProductParameters): JValue = {
    ElasticSearchClient.queryProductsByFulltextCriteria(storeCode,params)
  }

  def getProductsFeatures(storeCode: String, params: CompareProductParameters): JValue = {
    ElasticSearchClient.getProductsFeatures(storeCode, params)
  }

  def getProductDetails(storeCode: String, params: ProductDetailsRequest, productId: Long, uuid: String): JValue = {
    ElasticSearchClient.queryProductDetails(storeCode, params, productId, uuid)
  }

  def getProductDates(storeCode: String, params: ProductDatesRequest, productId: Long, uuid: String): JValue = {
    ElasticSearchClient.queryProductDates(storeCode, productId, params)
  }

  def getProductTimes(storeCode: String, params: ProductTimesRequest, productId: Long, uuid: String): JValue = {
    ElasticSearchClient.queryProductTimes(storeCode, productId, params)
  }

  def updateComment(storeCode: String, productId: Long, commentId: String, useful: Boolean): Boolean = {
    ElasticSearchClient.updateComment(storeCode, productId,commentId,useful)
  }

  def createComment(storeCode: String, productId: Long, req: CommentRequest): Comment = {
    ElasticSearchClient.createComment(storeCode, productId, req)
  }

  def getComment(storeCode: String, productId: Long, req: CommentGetRequest): JValue = {
    ElasticSearchClient.getComments(storeCode, productId, req)
  }

  def getProductHistory(storeCode: String, req: VisitorHistoryRequest, sessionId: String): List[JValue] = {
    val ids = ElasticSearchClient.getProductHistory(storeCode, sessionId)
    if (ids.isEmpty) List() else ElasticSearchClient.getProductsByIds(
      storeCode, ids, ProductDetailsRequest(historize = false, None, req.currency, req.country, req.lang)
    )
  }

}
