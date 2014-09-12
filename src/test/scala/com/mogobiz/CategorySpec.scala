package com.mogobiz

import org.json4s.JsonAST._
import org.json4s.native.JsonParser
import org.specs2.matcher.MatchResult
import org.json4s.JsonAST.JString
import org.json4s.JsonAST.JInt
import org.json4s.JsonAST.JBool


/**
 * Created by yoannbaudy on 11/09/14.
 */
class CategorySpec extends MogobizRouteTest {

  "The Category service" should {

    "return not hidden categories" in {
      Get("/store/" + STORE + "/categories") ~> sealRoute(routes) ~> check {
        val categories: List[JValue] = checkJArray(JsonParser.parse(responseAs[String]))
        categories must have size 4
        checkCategoryHightech(categories(0))
        checkCategoryHabillement(categories(1))
        checkCategoryTelevisions(categories(2))
        checkCategoryCinema(categories(3))
      }
    }

    "return hidden categories" in {
      Get("/store/" + STORE + "/categories?hidden=true") ~> sealRoute(routes) ~> check {
        val categories: List[JValue] = checkJArray(JsonParser.parse(responseAs[String]))
        categories must have size 4
        checkCategoryHightech(categories(0))
        checkCategoryHabillement(categories(1))
        checkCategoryTelevisions(categories(2))
        checkCategoryCinema(categories(3))
      }
    }

    "return not hidden categories by parentId" in {
      Get("/store/" + STORE + "/categories?parentId=20") ~> sealRoute(routes) ~> check {
        val categories: List[JValue] = checkJArray(JsonParser.parse(responseAs[String]))
        categories must have size 1
        checkCategoryTelevisions(categories(0))
      }
    }

    "return not hidden categories by unknown parentId" in {
      Get("/store/" + STORE + "/categories?parentId=1") ~> sealRoute(routes) ~> check {
        val categories: List[JValue] = checkJArray(JsonParser.parse(responseAs[String]))
        categories must have size 0
      }
    }

    "return not hidden categories by brandId" in {
      Get("/store/" + STORE + "/categories?brandId=35") ~> sealRoute(routes) ~> check {
        val categories: List[JValue] = checkJArray(JsonParser.parse(responseAs[String]))
        categories must have size 1
        checkCategoryTelevisions(categories(0))
      }
    }

    "return not hidden categories by unknown brandId" in {
      Get("/store/" + STORE + "/categories?brandId=1") ~> sealRoute(routes) ~> check {
        val categories: List[JValue] = checkJArray(JsonParser.parse(responseAs[String]))
        categories must have size 0
      }
    }

    "return not hidden categories by categoryPath hightech/televisions" in {
      Get("/store/" + STORE + "/categories?categoryPath=hightech/televisions") ~> sealRoute(routes) ~> check {
        val categories: List[JValue] = checkJArray(JsonParser.parse(responseAs[String]))
        categories must have size 1
        checkCategoryTelevisions(categories(0))
      }
    }

    "return not hidden categories by categoryPath hightech" in {
      Get("/store/" + STORE + "/categories?categoryPath=hightech") ~> sealRoute(routes) ~> check {
        val categories: List[JValue] = checkJArray(JsonParser.parse(responseAs[String]))
        categories must have size 2
        checkCategoryHightech(categories(0))
        checkCategoryTelevisions(categories(1))
      }
    }

    "return not hidden categories by unknown categoryPath" in {
      Get("/store/" + STORE + "/categories?categoryPath=nimportequoi") ~> sealRoute(routes) ~> check {
        val categories: List[JValue] = checkJArray(JsonParser.parse(responseAs[String]))
        categories must have size 0
      }
    }

  }

  def checkCategoryHightech(category: JValue, checkOnlyLang: String = null) : MatchResult[JValue] = {
    category \ "id" must be_==(JInt(20))
    category \ "parentId" must be_==(JNull)
    category \ "keywords" must be_==(JNull)
    category \ "hide" must be_==(JBool(false))
    category \ "description" must be_==(JNull)
    category \ "name" must be_==(JString("Hightech"))
    category \ "path" must be_==(JString("hightech"))
    category \ "increments" must be_==(JInt(0))
    category \ "uuid" must be_==(JString("71eabed1-b6e2-4066-a945-5963215d0378"))
    category \ "imported" must be_==(JNothing)
    checkLang(category, checkOnlyLang, "fr", JString("Hightech"), JNull, JNull)
  }

  def checkCategoryHabillement(category: JValue, checkOnlyLang: String = null) : MatchResult[JValue] = {
    category \ "id" must be_==(JInt(19))
    category \ "parentId" must be_==(JNull)
    category \ "keywords" must be_==(JString("vetements homme femme enfant"))
    category \ "hide" must be_==(JBool(false))
    category \ "description" must be_==(JNull)
    category \ "name" must be_==(JString("Habillement"))
    category \ "path" must be_==(JString("habillement"))
    category \ "increments" must be_==(JInt(0))
    category \ "uuid" must be_==(JString("f28f40fc-0362-4f21-9f26-7de561c497fa"))
    category \ "imported" must be_==(JNothing)
    checkLang(category, checkOnlyLang, "fr", JString("Habillement"), JNull, JString("vetements homme femme enfant"))
  }

  def checkCategoryTelevisions(category: JValue, checkOnlyLang: String = null) : MatchResult[JValue] = {
    category \ "id" must be_==(JInt(21))
    category \ "parentId" must be_==(JInt(20))
    category \ "keywords" must be_==(JString("TV télé télévision HD"))
    category \ "hide" must be_==(JBool(false))
    category \ "description" must be_==(JNull)
    category \ "name" must be_==(JString("Télévisions"))
    category \ "path" must be_==(JString("hightech/televisions"))
    category \ "increments" must be_==(JInt(0))
    category \ "uuid" must be_==(JString("24c453b0-f407-4156-a507-021779959d1a"))
    category \ "imported" must be_==(JNothing)
    checkLang(category, checkOnlyLang, "fr", JString("Télévisions"), JNull, JString("TV télé télévision HD"))
    checkLang(category, checkOnlyLang, "en", JString("Televisions"), JNothing, JNothing)
  }

  def checkCategoryCinema(category: JValue, checkOnlyLang: String = null) : MatchResult[JValue] = {
    category \ "id" must be_==(JInt(22))
    category \ "parentId" must be_==(JNull)
    category \ "keywords" must be_==(JNull)
    category \ "hide" must be_==(JBool(false))
    category \ "description" must be_==(JNull)
    category \ "name" must be_==(JString("Cinéma"))
    category \ "path" must be_==(JString("cinema"))
    category \ "increments" must be_==(JInt(0))
    category \ "uuid" must be_==(JString("4c1dcd08-80b3-476b-9b45-64c0718f674b"))
    category \ "imported" must be_==(JNothing)
    checkLang(category, checkOnlyLang, "fr", JString("Cinéma"), JNull, JNull)
  }

  def checkLang(category: JValue, checkOnlyLang: String, lang: String, exceptedName: JValue, exceptedDescription: JValue, exceptedKeywords: JValue) : MatchResult[JValue] = {
    if (checkOnlyLang == null || checkOnlyLang == lang) {
      category \ lang \ "name" must be_==(exceptedName)
      category \ lang \ "description" must be_==(exceptedDescription)
      category \ lang \ "keywords" must be_==(exceptedKeywords)
    }
    else category \ lang must be_==(JNothing)
  }

}
