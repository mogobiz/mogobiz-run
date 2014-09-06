package com.mogobiz.handlers

import com.mogobiz.{CategoryRequest, ElasticSearchClient}
import org.json4s.JsonAST.JValue
import org.json4s.native.JsonMethods._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global


class CategoryHandler {

  def queryCategories(storeCode: String, hidden: Boolean, parentId: Option[String], brandId: Option[String], categoryPath: Option[String], lang: String): JValue = {
    //TODO with Elastic4s
    val response = ElasticSearchClient.queryCategories(storeCode, CategoryRequest(hidden, parentId, brandId, categoryPath, lang))
    val data = response map {
      responseBody =>
        val json = parse(responseBody.entity.asString)
        val subset = json \ "hits" \ "hits" \ "_source"
        subset
    }
    Await.result(data, 10 seconds)
  }
}
