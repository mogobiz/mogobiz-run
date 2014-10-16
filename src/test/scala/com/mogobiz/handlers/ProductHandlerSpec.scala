package com.mogobiz.handlers

import org.json4s.JsonAST
import org.json4s.JsonAST._
import org.specs2.mutable.Specification

class ProductHandlerSpec extends Specification {

  val handler = new ProductHandler
  val storeCode = "mogobiz"

  var values: List[JsonAST.JValue] = List(
    JObject(List(("notation",JString("2")), ("nbcomments",JInt(2)))), JObject(List(("notation",JString("3")), ("nbcomments",JInt(2)))),
    JObject(List(("notation",JString("5")), ("nbcomments",JInt(2)))), JObject(List(("notation",JString("1")), ("nbcomments",JInt(1)))))

  "handler " should {
    "update product with nbcomments by notations" in {
      val id = 79
      val res = handler.updateProductNotations(storeCode, id, values)
      res must beTrue
    }
  }
}
