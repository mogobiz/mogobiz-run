package com.mogobiz

import com.mogobiz.es.ElasticSearchClient
import com.mogobiz.model.{Prefs, CommentGetRequest}
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
import org.json4s.{DefaultFormats, Formats}

/**
 *
 * Created by Christophe on 15/03/14.
 */
class ElasticSearchClientSpec  extends Specification with NoTimeConversions  {
  val store = "mogobiz"
  val sdf = new SimpleDateFormat("yyyy-MM-dd")

  implicit def json4sFormats: Formats = DefaultFormats

  "save preferences" in {
    var prefs = Prefs(5)
    val res = ElasticSearchClient.savePreferences(store, "UUID_TEST", prefs)
    res must beTrue
  }

  "get preferences" in {
    var prefs = Prefs(5)
    ElasticSearchClient.savePreferences(store, "UUID_TEST", prefs)
    ElasticSearchClient.getPreferences(store, "UUID_TEST") must not beNull
  }

  /*{
 "listAllLanguages list all languages" in {
   val langs = ElasticSearchClient.listAllLanguages()
   langs must have size 4
   langs must contain("fr")
   langs must contain("en")
   langs must contain("de")
   langs must contain("es")
 }

 "getAllExcludedLanguagesExcept" in {
   val langs = ElasticSearchClient.getAllExcludedLanguagesExcept("fr")
   langs should not contain("fr")
   langs must have size 3
 }

 "query product" in {
   val criteria = new ProductRequest("fr","EUR","FR")
//    criteria.name=Some("pull")
   val response = Await.result(ElasticSearchClient.queryProductsByCriteria(store,criteria), 3 second)
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


    val response = Await.result(ElasticSearchClient.queryProductDates(store,id,req), 3 second)
    println("+++ queryProductDates Response +++")
    println(pretty(render(response)))
    response must not be null

  }

  "add product to history" in {
    val productId = 95
    val sessionId = "47d8952b-6b26-453e-b755-d846a182f227"
    val res = Await.result(ElasticSearchClient.addToHistory(store,productId,sessionId), 3 second)
    res must beTrue
  }

  "get product history" in {
    val sessionId = "47d8952b-6b26-453e-b755-d846a182f227"
    val res = Await.result(ElasticSearchClient.getProductHistory(store,sessionId), 3 second)

    res must beEmpty
  }

  "get products from ids" in {
    val req = ProductDetailsRequest(false,None,Some("EUR"),Some("FR"),"fr")
    val res = Await.result(ElasticSearchClient.getProducts(store,List(1,2,3,94,95,47,61),req),3 second)
//    println(res)
//    println(res.length)

    res must not beEmpty
  }


  "get queryProductTimes " in {
    val id = 122
    val req = ProductTimesRequest(Some("2014-03-02"))
    val res = Await.result(ElasticSearchClient.queryProductTimes(store,id,req), 3 second)
    val extractedValues = res.extract[List[String]]

    extractedValues.head mustEqual "15:00"
    extractedValues.tail.head mustEqual "21:00"

  }

  "create a comment " in {
    val userId = ""
    val pid = 94
    //TODO create a mock for mogopay query
    val req = new CommentRequest(userId,"toto",1,"bad product","it failed after 1 week")
    val res = Await.result(ElasticSearchClient.createComment(store,pid,req), 3 second)
    println(res)
    /*
    {"_index":"mogobiz_comment","_type":"comment","_id":"keyp-SeGSvCNB_6S7lvkAQ","_version":1,"created":true}
     */
    true must beTrue
  }

  "create several comments and get them " in {
    val userId = ""
    val pid = 95
    //TODO create a mock for mogopay query
    var i = 0
    for(i <- 1 to 5){
      val req = new CommentRequest("userId_"+i,"username_"+i,i,"comment ___ "+i,"it failed after "+i+" week")
      val res = Await.result(ElasticSearchClient.createComment(store,pid,req), 3 second)
      println(res)
    }
    /*
    {"_index":"mogobiz_comment","_type":"comment","_id":"keyp-SeGSvCNB_6S7lvkAQ","_version":1,"created":true}
     */
    true must beTrue
  }
*/

  "get comments sorted by date desc" in {
    val req = new CommentGetRequest(Some(2),Some(0))
    val res = ElasticSearchClient.getComments(store,1, req)
    println(res)
    true must beTrue
  }
}
