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
import java.util.Calendar
import java.text.SimpleDateFormat

/**
 * Created by Christophe on 15/03/14.
 */
class ElasticSearchClientSpec  extends Specification with NoTimeConversions  {
  val esClient = new ElasticSearchClient
  val store = "mogobiz"
  val sdf = new SimpleDateFormat("yyyy-MM-dd")

  /*{
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
   val criteria = new ProductRequest("fr","EUR","FR")
//    criteria.name=Some("pull")
   val response = Await.result(esClient.queryProductsByCriteria(store,criteria), 3 second)
/*
   val json = parse(response.entity.asString)
   println(response.entity.asString)
   val subset = json \ "hits" \ "hits" \ "_source"
   println(subset)
*/

   //println(pretty(render(response)))
   //TODO do a real test

   response must not be null


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

  "get dates for product" in {
    //val id = 22
    val id = 115
    val cal = Calendar.getInstance()
    cal.add(Calendar.MONTH,-1)
    val str = sdf.format(cal.getTime)
    println("date test = "+str)
    val req = new ProductDatesRequest(Some(str),None,None)


    val response = Await.result(esClient.queryProductDates(store,id,req), 3 second)
    println("+++ queryProductDates Response +++")
    println(pretty(render(response)))
    response must not be null

  }

  "add product to history" in {
    val productId = 95
    val sessionId = "47d8952b-6b26-453e-b755-d846a182f227"
    val res = Await.result(esClient.addToHistory(store,productId,sessionId), 3 second)
    res must beTrue
  }

  "get product history" in {
    val sessionId = "47d8952b-6b26-453e-b755-d846a182f227"
    val res = Await.result(esClient.getProductHistory(store,sessionId), 3 second)
//    println(res)

    res must not beEmpty
  }
*/
  "get products from ids" in {
    val req = ProductDetailsRequest(false,None,"EUR","FR","fr")
    val res = Await.result(esClient.getProducts(store,List(1,2,3,94,95,47,61),req),3 second)
//    println(res)
    println(res.length)

    res must not beEmpty
  }




}
