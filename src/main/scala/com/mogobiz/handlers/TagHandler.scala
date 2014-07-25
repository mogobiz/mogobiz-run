package com.mogobiz.handlers

import com.mogobiz.ElasticSearchClient
import org.json4s.JsonAST.JValue
import org.json4s.native.JsonMethods._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Created by hayssams on 25/07/14.
 */
class TagHandler {
  val esClient = new ElasticSearchClient

  def queryTags(storeCode: String, hidden: Boolean, inactive: Boolean, lang: String): JValue = {
    //TODO with Elastic4s
    val response = esClient.queryTags(storeCode, hidden, inactive, lang)
    val data = response map { responseBody =>
      val json = parse(responseBody.entity.asString)
      val subset = json \ "hits" \ "hits" \ "_source"
      subset
    }
    Await.result(data, 10 seconds)
  }
}
