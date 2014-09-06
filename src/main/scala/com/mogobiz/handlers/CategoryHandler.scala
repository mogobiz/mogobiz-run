package com.mogobiz.handlers

import com.mogobiz.{CategoryRequest, ElasticSearchClient}
import org.json4s.JsonAST.JValue
import org.json4s.native.JsonMethods._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global


class CategoryHandler {

  def queryCategories(storeCode: String, hidden: Boolean, parentId: Option[String], brandId: Option[String], categoryPath: Option[String], lang: String): JValue = {
    ElasticSearchClient.queryCategories(storeCode, CategoryRequest(hidden, parentId, brandId, categoryPath, lang))
  }
}
