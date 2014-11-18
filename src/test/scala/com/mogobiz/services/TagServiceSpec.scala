package com.mogobiz.services

import com.mogobiz.MogobizRouteTest
import org.specs2.matcher._
import org.json4s.native.JsonParser
import org.json4s.JsonAST._

class TagServiceSpec extends MogobizRouteTest{
  "The Tag service" should {

    "return tags" in {
      Get("/store/" + STORE + "/tags") ~> sealRoute(routes) ~> check {
        val tags: List[JValue] = checkJArray(JsonParser.parse(responseAs[String]))
        tags must have size 4

        checkTag(tags(0), 87, "PULL")
        checkTag(tags(1), 88, "TSHIRT")
        checkTag(tags(2), 141, "CINEMA")
        checkTag(tags(3), 89, "THEATER")
      }
    }
  }

  def checkTag(tag:JValue, id:Int, name:String): MatchResult[JValue] = {
    tag \ "id" must be_==(JInt(id))
    tag \ "name" must be_==(JString(name))
    tag \ "increments" must be_==(JInt(0))
    //tag \ "fr" must be_==(JString(name))
  }
}
