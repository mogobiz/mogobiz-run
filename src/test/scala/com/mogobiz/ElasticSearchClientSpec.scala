package com.mogobiz

import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Success, Failure}
import scala.async.Async.{async, await}
import org.json4s.native.JsonMethods._
import scala.Some

/**
 * Created by Christophe on 15/03/14.
 */
class ElasticSearchClientSpec  extends Specification with NoTimeConversions  {
  val esClient = new ElasticSearchClient

  "listAllLanguages list all languages" in {
    val langs = esClient.listAllLanguages()
    langs must have size 4
    langs must contain("fr")
    langs must contain("en")
    langs must contain("de")
    langs must contain("es")
  }

  "getAllExcludedLanguagesExcept" in {
    val langs = esClient.getAllExcludedLanguagesExcept("fr")
    langs should not contain("fr")
    langs must have size 3
  }

  "query product" in {
    val store = "mogobiz"
    val criteria = new ProductRequest("fr","EUR","FR")
    criteria.name=Some("pull")
    val response = Await.result(esClient.queryProductsByCriteria(store,criteria), 1 second)
    val json = parse(response.entity.asString)
    println(response.entity)
//    val subset = json \ "hits" \ "hits" \ "_source"
//    println(subset)
    response.entity must not beEmpty

    /* onComplete {
      case Success(response) => {
        val json = parse(response.entity.asString)
        val subset = json \ "hits" \ "hits" \ "_source"
        println(subset)
        subset.toString must not beEmpty
      }
      case Failure(error) => throw error
    }*/
  }
}
