package com.mogobiz.handlers

import com.mogobiz.es.{ElasticSearchClient}
import com.mogobiz.model.FacetRequest
import org.json4s._

class FacetHandler {

  def getProductCriteria(storeCode: String, req: FacetRequest) : JValue = {
    ElasticSearchClient.getProductCriteria(storeCode, req)
  }
}
