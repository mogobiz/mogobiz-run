/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz

import org.json4s.JsonAST._
import org.json4s.native.JsonParser
import org.specs2.matcher.MatchResult
import org.json4s.JsonAST.JString
import org.json4s.JsonAST.JInt
import org.json4s.JsonAST.JBool


/**
 *
 * Created by yoannbaudy on 11/09/14.
 */
class CategorySpec extends MogobizRouteTest {

  "The Category service" should {

    "return not hidden categories" in {
      Get("/store/" + STORE + "/categories") ~> sealRoute(routes) ~> check {
        val categories: List[JValue] = checkJArray(JsonParser.parse(responseAs[String]))
        categories must have size 3
        checkCategoryCinema(categories(0))
        checkCategoryHabillement(categories(1))
        checkCategoryHightech(categories(2))
      }
    }

    "return hidden categories" in {
      Get("/store/" + STORE + "/categories?hidden=true") ~> sealRoute(routes) ~> check {
        val categories: List[JValue] = checkJArray(JsonParser.parse(responseAs[String]))
        categories must have size 3
        checkCategoryCinema(categories(0))
        checkCategoryHabillement(categories(1))
        checkCategoryHightech(categories(2))
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
        checkCategoryTelevisions(categories(0))
        checkCategoryHightech(categories(1))
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
    category \ "keywords" must be_==(JString(""))
    category \ "hide" must be_==(JBool(false))
    category \ "description" must be_==(JString(""))
    category \ "name" must be_==(JString("Hightech"))
    category \ "path" must be_==(JString("hightech"))
    category \ "increments" must be_==(JInt(0))
    category \ "uuid" must be_==(JString("0e0ac866-a3e7-44e0-bd77-e9d7cfb3f7fc"))
    category \ "imported" must be_==(JNothing)
    checkLang(category, checkOnlyLang, "fr", JString("Hightech"), JString(""), JString(""))
  }

  def checkCategoryHabillement(category: JValue, checkOnlyLang: String = null) : MatchResult[JValue] = {
    category \ "id" must be_==(JInt(19))
    category \ "parentId" must be_==(JNull)
    category \ "keywords" must be_==(JString("vetements homme femme enfant"))
    category \ "hide" must be_==(JBool(false))
    category \ "description" must be_==(JString(""))
    category \ "name" must be_==(JString("Habillement"))
    category \ "path" must be_==(JString("habillement"))
    category \ "increments" must be_==(JInt(0))
    category \ "uuid" must be_==(JString("fd3114cd-9ad7-44e5-aae8-a49de1e59100"))
    category \ "imported" must be_==(JNothing)
    checkLang(category, checkOnlyLang, "fr", JString("Habillement"), JString(""), JString("vetements homme femme enfant"))
  }

  def checkCategoryTelevisions(category: JValue, checkOnlyLang: String = null) : MatchResult[JValue] = {
    category \ "id" must be_==(JInt(21))
    category \ "parentId" must be_==(JInt(20))
    category \ "keywords" must be_==(JString("TV télé télévision HD"))
    category \ "hide" must be_==(JBool(false))
    category \ "description" must be_==(JString(""))
    category \ "name" must be_==(JString("Télévisions"))
    category \ "path" must be_==(JString("hightech/televisions"))
    category \ "increments" must be_==(JInt(0))
    category \ "uuid" must be_==(JString("f7ed5217-eb3a-4755-82aa-c7b2383ab731"))
    category \ "imported" must be_==(JNothing)
    checkLang(category, checkOnlyLang, "fr", JString("Télévisions"), JString(""), JString("TV télé télévision HD"))
    checkLang(category, checkOnlyLang, "en", JString("Televisions"), JNothing, JNothing)
  }

  def checkCategoryCinema(category: JValue, checkOnlyLang: String = null) : MatchResult[JValue] = {
    category \ "id" must be_==(JInt(22))
    category \ "parentId" must be_==(JNull)
    category \ "keywords" must be_==(JString(""))
    category \ "hide" must be_==(JBool(false))
    category \ "description" must be_==(JString(""))
    category \ "name" must be_==(JString("Cinéma"))
    category \ "path" must be_==(JString("cinema"))
    category \ "increments" must be_==(JInt(0))
    category \ "uuid" must be_==(JString("4f4df917-5630-4d35-a204-99014e43d46f"))
    category \ "imported" must be_==(JNothing)
    checkLang(category, checkOnlyLang, "fr", JString("Cinéma"), JString(""), JString(""))
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
