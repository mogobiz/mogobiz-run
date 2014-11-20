package com.mogobiz.services

import com.mogobiz.MogobizRouteTest
import com.mogobiz.run.model.Prefs
import org.json4s.{DefaultFormats, Formats}
import org.specs2.matcher._
import org.json4s.native.JsonParser
import org.json4s.JsonAST._

class PreferenceServiceSpec extends MogobizRouteTest {


  "The Preference service" should {

    "saving preference should return OK" in {
      Post("/store/" + STORE + "prefs") ~> sealRoute(routes) ~> check {
        val res = JsonParser.parse(responseAs[String])
        println(res)
        res must be_==(Nil)
      }
    }

    "return preference" in {
      implicit def json4sFormats: Formats = DefaultFormats
      Get("/store/" + STORE + "/prefs") ~> sealRoute(routes) ~> check {
        //val prefs: Prefs = JsonParser.parse(responseAs[Prefs])
        val res: JValue= JsonParser.parse(responseAs[String])
        println(res)
        val prefs = res.extract[Prefs]
        prefs.productsNumber must be_==(10)

      }
    }
  }
}
