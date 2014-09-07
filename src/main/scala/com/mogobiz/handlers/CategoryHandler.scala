package com.mogobiz.handlers

import com.mogobiz.ElasticSearchClient
import com.mogobiz.model.CategoryRequest
import org.json4s.JsonAST.JValue


class CategoryHandler {

  def queryCategories(storeCode: String, hidden: Boolean, parentId: Option[String], brandId: Option[String], categoryPath: Option[String], lang: String): JValue = {
    ElasticSearchClient.queryCategories(storeCode, CategoryRequest(hidden, parentId, brandId, categoryPath, lang))
  }
}
