/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz

import com.mogobiz.json.JacksonConverter
import org.json4s.JsonAST.{JBool, JInt, JString, _}


/**
  *
  */
class CategorySpec extends MogobizRouteTest {

  "The Category service" should "return not hidden categories" in {
    Get("/store/" + STORE + "/categories") ~> sealRoute(routes) ~> check {
      val categories: List[JValue] = checkJArray(JacksonConverter.parse(responseAs[String]))
      categories should have size 3
      checkCategoryCinema(categories(0))
      checkCategoryHabillement(categories(1))
      checkCategoryHightech(categories(2))
    }
  }

  it should "return hidden categories" in {
    Get("/store/" + STORE + "/categories?hidden=true") ~> sealRoute(routes) ~> check {
      val categories: List[JValue] = checkJArray(JacksonConverter.parse(responseAs[String]))
      categories should have size 3
      checkCategoryCinema(categories(0))
      checkCategoryHabillement(categories(1))
      checkCategoryHightech(categories(2))
    }
  }

  it should "return not hidden categories by parentId" in {
    Get("/store/" + STORE + "/categories?parentId=20") ~> sealRoute(routes) ~> check {
      val categories: List[JValue] = checkJArray(JacksonConverter.parse(responseAs[String]))
      categories should have size 1
      checkCategoryTelevisions(categories(0))
    }
  }

  it should "return not hidden categories by unknown parentId" in {
    Get("/store/" + STORE + "/categories?parentId=1") ~> sealRoute(routes) ~> check {
      val categories: List[JValue] = checkJArray(JacksonConverter.parse(responseAs[String]))
      categories should have size 0
    }
  }

  it should "return not hidden categories by brandId" in {
    Get("/store/" + STORE + "/categories?brandId=35") ~> sealRoute(routes) ~> check {
      val categories: List[JValue] = checkJArray(JacksonConverter.parse(responseAs[String]))
      categories should have size 1
      checkCategoryTelevisions(categories(0))
    }
  }

  it should "return not hidden categories by unknown brandId" in {
    Get("/store/" + STORE + "/categories?brandId=1") ~> sealRoute(routes) ~> check {
      val categories: List[JValue] = checkJArray(JacksonConverter.parse(responseAs[String]))
      categories should have size 0
    }
  }

  it should "return not hidden categories by categoryPath hightech/televisions" in {
    Get("/store/" + STORE + "/categories?categoryPath=hightech/televisions") ~> sealRoute(routes) ~> check {
      val categories: List[JValue] = checkJArray(JacksonConverter.parse(responseAs[String]))
      categories should have size 1
      checkCategoryTelevisions(categories(0))
    }
  }

  it should "return not hidden categories by categoryPath hightech" in {
    Get("/store/" + STORE + "/categories?categoryPath=hightech") ~> sealRoute(routes) ~> check {
      val categories: List[JValue] = checkJArray(JacksonConverter.parse(responseAs[String]))
      categories should have size 2
      checkCategoryTelevisions(categories(0))
      checkCategoryHightech(categories(1))
    }
  }

  it should "return not hidden categories by unknown categoryPath" in {
    Get("/store/" + STORE + "/categories?categoryPath=nimportequoi") ~> sealRoute(routes) ~> check {
      val categories: List[JValue] = checkJArray(JacksonConverter.parse(responseAs[String]))
      categories should have size 0
    }
  }


  def checkCategoryHightech(category: JValue, checkOnlyLang: String = null) = {
    category \ "id" should be(JInt(20))
    category \ "parentId" should be(JNull)
    category \ "keywords" should be(JString(""))
    category \ "hide" should be(JBool(false))
    category \ "description" should be(JString(""))
    category \ "name" should be(JString("Hightech"))
    category \ "path" should be(JString("hightech"))
    category \ "increments" should be(JInt(0))
    category \ "uuid" should be(JString("0e0ac866-a3e7-44e0-bd77-e9d7cfb3f7fc"))
    category \ "imported" should be(JNothing)
    checkLang(category, checkOnlyLang, "fr", JString("Hightech"), JString(""), JString(""))
  }

  def checkCategoryHabillement(category: JValue, checkOnlyLang: String = null) = {
    category \ "id" should be(JInt(19))
    category \ "parentId" should be(JNull)
    category \ "keywords" should be(JString("vetements homme femme enfant"))
    category \ "hide" should be(JBool(false))
    category \ "description" should be(JString(""))
    category \ "name" should be(JString("Habillement"))
    category \ "path" should be(JString("habillement"))
    category \ "increments" should be(JInt(0))
    category \ "uuid" should be(JString("fd3114cd-9ad7-44e5-aae8-a49de1e59100"))
    category \ "imported" should be(JNothing)
    checkLang(category, checkOnlyLang, "fr", JString("Habillement"), JString(""), JString("vetements homme femme enfant"))
  }

  def checkCategoryTelevisions(category: JValue, checkOnlyLang: String = null) = {
    category \ "id" should be(JInt(21))
    category \ "parentId" should be(JInt(20))
    category \ "keywords" should be(JString("TV télé télévision HD"))
    category \ "hide" should be(JBool(false))
    category \ "description" should be(JString(""))
    category \ "name" should be(JString("Télévisions"))
    category \ "path" should be(JString("hightech/televisions"))
    category \ "increments" should be(JInt(0))
    category \ "uuid" should be(JString("f7ed5217-eb3a-4755-82aa-c7b2383ab731"))
    category \ "imported" should be(JNothing)
    checkLang(category, checkOnlyLang, "fr", JString("Télévisions"), JString(""), JString("TV télé télévision HD"))
    checkLang(category, checkOnlyLang, "en", JString("Televisions"), JNothing, JNothing)
  }

  def checkCategoryCinema(category: JValue, checkOnlyLang: String = null) = {
    category \ "id" should be(JInt(22))
    category \ "parentId" should be(JNull)
    category \ "keywords" should be(JString(""))
    category \ "hide" should be(JBool(false))
    category \ "description" should be(JString(""))
    category \ "name" should be(JString("Cinéma"))
    category \ "path" should be(JString("cinema"))
    category \ "increments" should be(JInt(0))
    category \ "uuid" should be(JString("4f4df917-5630-4d35-a204-99014e43d46f"))
    category \ "imported" should be(JNothing)
    checkLang(category, checkOnlyLang, "fr", JString("Cinéma"), JString(""), JString(""))
  }

  def checkLang(category: JValue, checkOnlyLang: String, lang: String, exceptedName: JValue, exceptedDescription: JValue, exceptedKeywords: JValue) = {
    if (checkOnlyLang == null || checkOnlyLang == lang) {
      category \ lang \ "name" should be(exceptedName)
      category \ lang \ "description" should be(exceptedDescription)
      category \ lang \ "keywords" should be(exceptedKeywords)
    }
    else category \ lang should be(JNothing)
  }
}
