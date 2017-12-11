/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.services

import com.mogobiz.MogobizRouteTest
import com.mogobiz.run.model.Prefs
import org.json4s.JsonAST._
import org.json4s.native.JsonParser
import org.json4s.{DefaultFormats, Formats}
import spray.http.{HttpCookie, HttpHeaders}

class PreferenceServiceSpec extends MogobizRouteTest {

  implicit val json4sFormats: Formats = DefaultFormats
  val cookies = Seq(new HttpCookie("mogobiz_uuid", "UNIT_TEST"), new HttpCookie("Path","/store/"+STORE))
  val cookiesHeader = List(HttpHeaders.Cookie(cookies))

  "The Preference service" should "saving preference should return OK" in {
      Post("/store/" + STORE + "/prefs").withHeaders(cookiesHeader) ~> sealRoute(routes) ~> check {
        val res = JsonParser.parse(responseAs[String])
        res \ "code" should be(JBool(true))
      }
    }

    it should "return preference" in {
      Get("/store/" + STORE + "/prefs").withHeaders(cookiesHeader) ~> sealRoute(routes) ~> check {
        val res: JValue= JsonParser.parse(responseAs[String])
        println(res)
        val prefs = res.extract[Prefs]
        prefs.productsNumber should be(10)
    }
  }
}
