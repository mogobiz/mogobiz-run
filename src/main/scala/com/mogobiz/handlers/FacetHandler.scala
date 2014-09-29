package com.mogobiz.handlers

import com.mogobiz.es.{ElasticSearchClient}
import org.json4s._

class FacetHandler {

  def getProductCriteria(storeCode: String, lang:String, priceInterval: Long) : JValue = {
    ElasticSearchClient.getProductCriteria(storeCode, lang, priceInterval)
  }
}
