package com.mogobiz.handlers

import com.mogobiz.es.{ElasticSearchClient}
import com.mogobiz.json.Json4sProtocol
import org.json4s._

class FacetHandler {

  def getProductCriteria(storeCode: String, priceInterval: Long) : JValue = {
    ElasticSearchClient.getProductCriteria(storeCode, priceInterval)
  }
}
