package com.mogobiz.services

import com.mogobiz.MogobizRouteTest
import org.specs2.matcher._
import org.json4s.native.JsonParser
import org.json4s.JsonAST._

class LangServiceSpec extends MogobizRouteTest {
  "The Lang service" should {

    "return langs" in {
      Get("/store/" + STORE + "/langs") ~> sealRoute(routes) ~> check {
        val langs: List[JValue] = checkJArray(JsonParser.parse(responseAs[String]))
        langs must have size 4

        langs(0) must be_==(JString("en"))
        langs(1) must be_==(JString("fr"))
        langs(2) must be_==(JString("es"))
        langs(3) must be_==(JString("de"))
      }
    }
  }

}
