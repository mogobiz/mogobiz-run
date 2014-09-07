package com.mogobiz.handlers

import com.mogobiz.ElasticSearchClient
import com.mogobiz.model.BrandRequest
import org.json4s.JsonAST.JValue

class BrandHandler {

  def queryBrand(storeCode: String, hidden: Boolean, categoryPath: Option[String], lang: String): JValue = {
    ElasticSearchClient.queryBrands(storeCode, new BrandRequest(hidden, categoryPath, lang))
  }
}
