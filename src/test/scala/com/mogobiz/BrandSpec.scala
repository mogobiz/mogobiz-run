package com.mogobiz

import org.specs2.matcher._
import org.json4s.native.JsonParser
import org.json4s.JsonAST._

/**
 *
 * Created by yoannbaudy on 07/09/14.
 */
class BrandSpec extends MogobizRouteTest {

  "The Brand service" should {

    "return not hidden brands" in {
      Get("/store/" + STORE + "/brands") ~> sealRoute(routes) ~> check {
        val brands: List[JValue] = checkJArray(JsonParser.parse(responseAs[String]))
        brands must have size 4
        checkBrandNike(brands(0))
        checkBrandPhilips(brands(1))
        checkBrandPuma(brands(2))
        checkBrandSamsung(brands(3))
      }
    }

    "return hidden brands" in {
      Get("/store/" + STORE + "/brands?hidden=true") ~> sealRoute(routes) ~> check {
        val brands: List[JValue] = checkJArray(JsonParser.parse(responseAs[String]))
        brands must have size 5
        checkBrandHideBrand(brands(0))
        checkBrandNike(brands(1))
        checkBrandPhilips(brands(2))
        checkBrandPuma(brands(3))
        checkBrandSamsung(brands(4))
      }
    }

    "return brands with invalide hidden parameter" in {
      Get("/store/" + STORE + "/brands?hidden=nimportequoi") ~> sealRoute(routes) ~> check {
        status.intValue must be_==(400)
      }
    }

    "return not hidden brands for given category path" in {
      Get("/store/" + STORE + "/brands?categoryPath=hightech") ~> sealRoute(routes) ~> check {
        val brands: List[JValue] = checkJArray(JsonParser.parse(responseAs[String]))
        brands must have size 2
        checkBrandPhilips(brands(0))
        checkBrandSamsung(brands(1))
      }
    }

    "return not hidden brands for given unknown category path" in {
      Get("/store/" + STORE + "/brands?categoryPath=un+chemin+qui+nexiste+pas") ~> sealRoute(routes) ~> check {
        val brands: List[JValue] = checkJArray(JsonParser.parse(responseAs[String]))
        brands must have size 0
      }
    }

    "return not hidden brands for language 'fr'" in {
      Get("/store/" + STORE + "/brands?lang=fr") ~> sealRoute(routes) ~> check {
        val brands: List[JValue] = checkJArray(JsonParser.parse(responseAs[String]))
        brands must have size 4
        checkBrandNike(brands(0), "fr")
        checkBrandPhilips(brands(1), "fr")
        checkBrandPuma(brands(2), "fr")
        checkBrandSamsung(brands(3), "fr")
      }
    }

    "return not hidden brands for language 'de'" in {
      Get("/store/" + STORE + "/brands?lang=de") ~> sealRoute(routes) ~> check {
        val brands: List[JValue] = checkJArray(JsonParser.parse(responseAs[String]))
        brands must have size 4
        checkBrandNike(brands(0), "de")
        checkBrandPhilips(brands(1), "de")
        checkBrandPuma(brands(2), "de")
        checkBrandSamsung(brands(3), "de")
      }
    }

    "return not hidden brands for illegale language" in {
      Get("/store/" + STORE + "/brands?lang=ZZ") ~> sealRoute(routes) ~> check {
        val brands: List[JValue] = checkJArray(JsonParser.parse(responseAs[String]))
        brands must have size 4
        checkBrandNike(brands(0), "ZZ")
        checkBrandPhilips(brands(1), "ZZ")
        checkBrandPuma(brands(2), "ZZ")
        checkBrandSamsung(brands(3), "ZZ")
      }
    }

    "return hidden brands for given category path" in {
      Get("/store/" + STORE + "/brands?hidden=true&categoryPath=hightech") ~> sealRoute(routes) ~> check {
        val brands: List[JValue] = checkJArray(JsonParser.parse(responseAs[String]))
        brands must have size 2
        checkBrandPhilips(brands(0))
        checkBrandSamsung(brands(1))
      }
    }

    "return hidden brands for language 'fr'" in {
      Get("/store/" + STORE + "/brands?hidden=true&lang=fr") ~> sealRoute(routes) ~> check {
        val brands: List[JValue] = checkJArray(JsonParser.parse(responseAs[String]))
        brands must have size 5
        checkBrandHideBrand(brands(0), "fr")
        checkBrandNike(brands(1), "fr")
        checkBrandPhilips(brands(2), "fr")
        checkBrandPuma(brands(3), "fr")
        checkBrandSamsung(brands(4), "fr")
      }
    }

    "return not hidden brands for given category path and language 'fr'" in {
      Get("/store/" + STORE + "/brands?categoryPath=hightech&lang=fr") ~> sealRoute(routes) ~> check {
        val brands: List[JValue] = checkJArray(JsonParser.parse(responseAs[String]))
        brands must have size 2
        checkBrandPhilips(brands(0), "fr")
        checkBrandSamsung(brands(1), "fr")
      }
    }

    "return hidden brands for given category path and language 'fr'" in {
      Get("/store/" + STORE + "/brands?hidden=true&categoryPath=hightech&lang=fr") ~> sealRoute(routes) ~> check {
        val brands: List[JValue] = checkJArray(JsonParser.parse(responseAs[String]))
        brands must have size 2
        checkBrandPhilips(brands(0), "fr")
        checkBrandSamsung(brands(1), "fr")
      }
    }
  }

  def checkBrandSamsung(brand: JValue, checkOnlyLang: String = null) : MatchResult[JValue] = {
    brand \ "id" must be_==(JInt(35))
    brand \ "twitter" must be_==(JNull)
    brand \ "hide" must be_==(JBool(false))
    brand \ "website" must be_==(JString("http://www.samsung.com/fr"))
    brand \ "description" must be_==(JNull)
    brand \ "name" must be_==(JString("Samsung"))
    brand \ "increments" must be_==(JInt(0))
    brand \ "imported" must be_==(JNothing)
    checkLang(brand, checkOnlyLang, "fr", JString("Samsung"), JString("http://www.samsung.com/fr"))
    checkLang(brand, checkOnlyLang, "de", JNothing, JString("http://www.samsung.com/de"))
    checkLang(brand, checkOnlyLang, "en", JNothing, JString("http://www.samsung.com"))
    checkLang(brand, checkOnlyLang, "es", JNothing, JString("http://www.samsung.com/es"))
  }

  def checkBrandPhilips(brand: JValue, checkOnlyLang: String = null) : MatchResult[JValue] = {
    brand \ "id" must be_==(JInt(40))
    brand \ "twitter" must be_==(JNull)
    brand \ "hide" must be_==(JBool(false))
    brand \ "website" must be_==(JString("http://www.philips.com"))
    brand \ "description" must be_==(JNull)
    brand \ "name" must be_==(JString("Philips"))
    brand \ "increments" must be_==(JInt(0))
    brand \ "imported" must be_==(JNothing)
    checkLang(brand, checkOnlyLang, "fr", JString("Philips"), JString("http://www.philips.com"))
    checkLang(brand, checkOnlyLang, "de", JNothing, JNothing)
    checkLang(brand, checkOnlyLang, "en", JNothing, JNothing)
    checkLang(brand, checkOnlyLang, "es", JNothing, JNothing)
  }

  def checkBrandNike(brand: JValue, checkOnlyLang: String = null) : MatchResult[JValue] = {
    brand \ "id" must be_==(JInt(41))
    brand \ "twitter" must be_==(JNull)
    brand \ "hide" must be_==(JBool(false))
    brand \ "website" must be_==(JString("http://www.nike.com/fr/fr_fr/"))
    brand \ "description" must be_==(JNull)
    brand \ "name" must be_==(JString("Nike"))
    brand \ "increments" must be_==(JInt(0))
    brand \ "imported" must be_==(JNothing)
    checkLang(brand, checkOnlyLang, "fr", JString("Nike"), JString("http://www.nike.com/fr/fr_fr/"))
    checkLang(brand, checkOnlyLang, "de", JNothing, JString("http://www.nike.com/de/de_de/"))
    checkLang(brand, checkOnlyLang, "en", JNothing, JString("http://www.nike.com"))
    checkLang(brand, checkOnlyLang, "es", JNothing, JString("http://www.nike.com/es/es_es/"))
  }

  def checkBrandPuma(brand: JValue, checkOnlyLang: String = null) : MatchResult[JValue] = {
    brand \ "id" must be_==(JInt(46))
    brand \ "twitter" must be_==(JNull)
    brand \ "hide" must be_==(JBool(false))
    brand \ "website" must be_==(JString("http://www.shop.puma.fr"))
    brand \ "description" must be_==(JNull)
    brand \ "name" must be_==(JString("Puma"))
    brand \ "increments" must be_==(JInt(0))
    brand \ "imported" must be_==(JNothing)
    checkLang(brand, checkOnlyLang, "fr", JString("Puma"), JString("http://www.shop.puma.fr"))
    checkLang(brand, checkOnlyLang, "de", JNothing, JString("http://www.shop.puma.de"))
    checkLang(brand, checkOnlyLang, "en", JNothing, JString("http://www.puma.com"))
    checkLang(brand, checkOnlyLang, "es", JNothing, JString("http://www.puma.com"))
  }

  def checkBrandHideBrand(brand: JValue, checkOnlyLang: String = null) : MatchResult[JValue] = {
    brand \ "id" must be_==(JInt(51))
    brand \ "twitter" must be_==(JNull)
    brand \ "hide" must be_==(JBool(true))
    brand \ "website" must be_==(JString("http://www.google.fr"))
    brand \ "description" must be_==(JNull)
    brand \ "name" must be_==(JString("Hide brand"))
    brand \ "increments" must be_==(JInt(0))
    brand \ "imported" must be_==(JNothing)
    checkLang(brand, checkOnlyLang, "fr", JString("Hide brand"), JString("http://www.google.fr"))
  }

  def checkLang(brand: JValue, checkOnlyLang: String, lang: String, exceptedName: JValue, exceptedWebsite: JValue) : MatchResult[JValue] = {
    if (checkOnlyLang == null || checkOnlyLang == lang) {
      brand \ lang \ "name" must be_==(exceptedName)
      brand \ lang \ "website" must be_==(exceptedWebsite)
    }
    else brand \ lang must be_==(JNothing)
  }
}
