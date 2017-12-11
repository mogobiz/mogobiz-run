/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.services

import java.util.regex.MatchResult

import com.mogobiz.MogobizRouteTest
import com.mogobiz.json.JacksonConverter
import org.json4s.JsonAST._
import org.scalatest.{FlatSpec, Matchers}
import spray.testkit.ScalatestRouteTest

class TagServiceSpec extends MogobizRouteTest {
  "The Tag service" should "return tags" in {
    Get("/store/" + STORE + "/tags") ~> sealRoute(routes) ~> check {
      val tags: List[JValue] = checkJArray(JacksonConverter.parse(responseAs[String]))
      tags should have size 4
      println(tags)
      //List(
      // JObject(List((id,JInt(89)), (name,JString(THEATER)), (fr,JObject(List((name,JString(THEATER))))), (increments,JInt(0)))),
      // JObject(List((id,JInt(87)), (name,JString(PULL)), (fr,JObject(List((name,JString(PULL))))), (increments,JInt(0)))),
      // JObject(List((id,JInt(88)), (name,JString(TSHIRT)), (fr,JObject(List((name,JString(TSHIRT))))), (increments,JInt(0)))),
      // JObject(List((id,JInt(141)), (name,JString(CINEMA)), (fr,JObject(List((name,JString(CINEMA))))), (increments,JInt(0)))))


      checkTag(tags(0), 89, "THEATER")
      checkTag(tags(1), 141, "CINEMA")
      checkTag(tags(2), 88, "TSHIRT")
      checkTag(tags(3), 87, "PULL")
    }
  }

  def checkTag(tag: JValue, id: Int, name: String) = {
    tag \ "id" should be(JInt(id))
    tag \ "name" should be(JString(name))
    tag \ "increments" should be(JInt(0))
    //tag \ "fr" must be_==(JString(name))
  }
}
