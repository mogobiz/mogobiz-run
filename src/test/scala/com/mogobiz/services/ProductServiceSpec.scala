/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.services

import com.mogobiz.MogobizRouteTest
import org.json4s.JsonAST._
import org.json4s.native.JsonParser
import spray.http._

class ProductServiceSpec extends MogobizRouteTest {

  val cookies = Seq(new HttpCookie("mogobiz_uuid", "UNIT_TEST"), new HttpCookie("Path", "/store/" + STORE))
  val request_headers = List(HttpHeaders.Cookie(cookies))

  "The products route of Product service" should "returns suggestion for product" in {
    Get("/store/" + STORE_ACMESPORT + "/products/32531/suggestions") ~> sealRoute(routes) ~> check {
      val res: JValue = JsonParser.parse(responseAs[String])
      val array = checkJArray(res)
      array should have size (1)
    }
  }

  it should "return products on default criteria" in {
    Get("/store/" + STORE + "/products") ~> sealRoute(routes) ~> check {

      val res: JValue = JsonParser.parse(responseAs[String])
      println(res)
     res \ "totalCount" should be(JInt(7))
    }
  }


  "The 'find' route of Product service" should "return products via fulltext search" in {
    Get("/store/" + STORE + "/products/find?query=puma") ~> sealRoute(routes) ~> check {
      //val res = response
      val res: JValue = JsonParser.parse(responseAs[String])
      println(res)
      val products: List[JValue] = checkJArray(res \ "product")
      val brands: List[JValue] = checkJArray(res \ "brand")

      products(0) \ "id" should be(JInt(114))
      products(0) \ "name" should be(JString("TShirt Puma"))
      brands(0) \ "id" should be(JInt(46))
      brands(0) \ "name" should be(JString("Puma"))
    }
  }
  //TODO tests with parameters: highlight, size, categoryPath (lang, currency, country)


  "The 'compare' route of Product service" should "return array comparison of 2 product" in {
      Get("/store/" + STORE + "/products/compare?ids=61,70") ~> sealRoute(routes) ~> check {
        response.status.intValue should be(200)
        val res: JValue = JsonParser.parse(responseAs[String])
        println(res)
        val ids = checkJArray(res \ "ids")
        val result = checkJArray(res \ "result")
        ids(0) should be(JString("61"))
        ids(1) should be(JString("70"))
        //TODO result assertions
      }
    }


  "The 'notation' route of Product service" should "return all notations" in {
      Get("/store/" + STORE + "/products/notation") ~> sealRoute(routes) ~> check {
        val res: JValue = JsonParser.parse(responseAs[String])
        println("notation result: ", res)
        response.status.intValue should be(200)
      }
    }

  "The 'product' route of Product service" should "return product detail with default parameters (historize=false)" in {
    Get("/store/" + STORE + "/products/61") ~> sealRoute(routes) ~> check {
      val res: JValue = JsonParser.parse(responseAs[String])
      println("product detail(61): ", res)
     response.status.intValue should be(200)

      res \ "id" should be(JInt(61))
      res \ "name" should be(JString("""TV 100" Full HD"""))
      res \ "description" should be(JString("""Full HD 100" Television"""))
    }
  }

    //TODO tests with parameters: historize, visitorId (lang, currency, country)


    it should "return dates for product 61" in {
      Get("/store/" + STORE + "/products/61/dates") ~> sealRoute(routes) ~> check {
        val res: JValue = JsonParser.parse(responseAs[String])
        println("product dates(61): ", res)
        response.status.intValue should be(200)

        //TODO complete assertion
      }
    }
    it should "return times for product 61" in {
      Get("/store/" + STORE + "/products/61/times") ~> sealRoute(routes) ~> check {
        val res: JValue = JsonParser.parse(responseAs[String])
        println("product times(61): ", res)
        response.status.intValue should be(200)

        //TODO complete assertion
      }
    }

  "The product comment' route in Product service" should "return new comment created for product 61" in {
      val entity = HttpEntity(contenTypeJson,
        """
          |{
          |    "userId": "userId_soapui",
          |    "surname": "username_soapui",
          |    "notation": 3,
          |    "subject": "soap ui subject ___ ",
          |    "comment": "dsfsdf dlsfnflsk dsf kdlf sd lkdsjfklsd ds dsfdff l lkj klj sdkklkjls fdskj kljlk  lj soapui failed after 2 weeks"
          |}
        """.stripMargin)
      Post("/store/" + STORE + "/products/61/comments").withEntity(entity) ~> sealRoute(routes) ~> check {
        val res: JValue = JsonParser.parse(responseAs[String])
        println(res)
        response.status.intValue should be(200)
      }
    }


    it should "return comments for product 61" in {
      Get("/store/" + STORE + "/products/61/comments") ~> sealRoute(routes) ~> check {
        val res: JValue = JsonParser.parse(responseAs[String])
        println("GET product comment: ", res)
        response.status.intValue should be(200)
      }
    }


  "The 'history' route in Product service" should "return products visited by the user from its history" in {
      Get("/store/" + STORE + "/history").withHeaders(request_headers) ~> sealRoute(routes) ~> check {
        val res: JValue = JsonParser.parse(responseAs[String])
        println("product history: ", res)
        response.status.intValue should be(200)

        //TODO complete test with detail?historize=true
    }
  }
}
