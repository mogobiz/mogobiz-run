package com.mogobiz.run.implicits

import org.elasticsearch.search.SearchHit
import org.json4s.JsonAST.{JValue, JArray}
import org.json4s.native.JsonMethods._

/**
 * Created by yoannbaudy on 18/11/2014.
 */
object Es4Json {

  implicit def searchHits2JArray(searchHits:Array[SearchHit]) : JArray = JArray(searchHits.map(hit => parse(hit.getSourceAsString)).toList)

  implicit def searchHit2JValue(searchHit : SearchHit) : JValue = parse(searchHit.getSourceAsString)

}
