package com.mogobiz.services

import com.mogobiz.MogobizRouteTest
import com.mogobiz.run.model.Prefs
import org.json4s.{DefaultFormats, Formats}
import org.specs2.matcher._
import org.json4s.native.JsonParser
import org.json4s.JsonAST._
import spray.http.{HttpCookie,HttpHeaders}
import spray.http.HttpHeaders.Cookie

class PreferenceServiceSpec extends MogobizRouteTest {

  implicit def json4sFormats: Formats = DefaultFormats

  "The Preference service" should {
    val cookies = Seq(new HttpCookie("mogobiz_uuid", "UNIT_TEST"), new HttpCookie("Path","/store/"+STORE))
    val headers = List(HttpHeaders.Cookie(cookies))

    "saving preference should return OK" in {
      Post("/store/" + STORE + "/prefs").withHeaders(headers) ~> sealRoute(routes) ~> check {
        val res = JsonParser.parse(responseAs[String])
        res \ "code" must be_==(JBool(true))
      }
    }

    "return preference" in {
      Get("/store/" + STORE + "/prefs").withHeaders(headers) ~> sealRoute(routes) ~> check {
        //val prefs: Prefs = JsonParser.parse(responseAs[Prefs])
        val res: JValue= JsonParser.parse(responseAs[String])
        println(res)
        val prefs = res.extract[Prefs]
        prefs.productsNumber must be_==(10)

      }
    }
  }
}
