/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

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
  }

  def checkTag(tag:JValue, id:Int, name:String): MatchResult[JValue] = {
    tag \ "id" must be_==(JInt(id))
    tag \ "name" must be_==(JString(name))
    tag \ "increments" must be_==(JInt(0))
    //tag \ "fr" must be_==(JString(name))
  }
}
