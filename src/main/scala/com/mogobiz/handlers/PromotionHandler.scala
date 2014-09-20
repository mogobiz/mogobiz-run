package com.mogobiz.handlers

import com.mogobiz.es.ElasticSearchClient
import com.mogobiz.model.PromotionRequest
import org.json4s.JsonAST.JValue

class PromotionHandler {

  def queryPromotion(storeCode: String, params: PromotionRequest): JValue = {
    ElasticSearchClient.queryPromotions(storeCode, params)
  }
}
