package com.mogobiz

import com.mogobiz.es.EmbeddedElasticSearchNode
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import spray.testkit.Specs2RouteTest
import spray.routing.HttpService
import com.mogobiz.services.MogobizRoutes
import com.mogobiz.actors.{MogobizActors, MogobizSystem}
import scala.concurrent.duration._
import org.specs2.matcher._
import org.json4s.native.JsonParser
import org.json4s.JsonAST._
import com.mogobiz.json.JsonUtil

/**
 *
 * Created by yoannbaudy on 07/09/14.
 */
class BrandSpec extends Specification with Specs2RouteTest with HttpService with MogobizRoutes with MogobizActors with MogobizSystem with JsonMatchers with EmbeddedElasticSearchNode with JsonUtil with NoTimeConversions {
  def actorRefFactory = system // connect the DSL to the test ActorSystem
  val STORE = "mogobiz"

  sequential

  implicit val routeTestTimeout = RouteTestTimeout(FiniteDuration(5, SECONDS))

  step(start)

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

    "return categories brands" in {
      Get("/store/" + STORE + "/brands?categoryPath=hightech") ~> sealRoute(routes) ~> check {
        val brands: List[JValue] = checkJArray(JsonParser.parse(responseAs[String]))
        brands must have size 2
        checkBrandPhilips(brands(0))
        checkBrandSamsung(brands(1))
      }
    }
  }

  def checkJArray(j: JValue) : List[JValue] = j match {
    case JArray(a) => a
    case _ => List(j)
  }

  def checkBrandSamsung(brand: JValue) : MatchResult[JValue] = {
    brand \ "id" must be_==(JInt(35))
    brand \ "twitter" must be_==(JNull)
    brand \ "hide" must be_==(JBool(false))
    brand \ "website" must be_==(JString("http://www.samsung.com/fr"))
    brand \ "description" must be_==(JNull)
    brand \ "name" must be_==(JString("Samsung"))
    brand \ "increments" must be_==(JInt(0))
    brand \ "imported" must be_==(JNothing)
    brand \ "fr" \ "name" must be_==(JString("Samsung"))
    brand \ "fr" \ "website" must be_==(JString("http://www.samsung.com/fr"))
    brand \ "en" \ "website" must be_==(JString("http://www.samsung.com"))
    brand \ "es" \ "website" must be_==(JString("http://www.samsung.com/es"))
  }

  def checkBrandPhilips(brand: JValue) : MatchResult[JValue] = {
    brand \ "id" must be_==(JInt(40))
    brand \ "twitter" must be_==(JNull)
    brand \ "hide" must be_==(JBool(false))
    brand \ "website" must be_==(JString("http://www.philips.com"))
    brand \ "description" must be_==(JNull)
    brand \ "name" must be_==(JString("Philips"))
    brand \ "increments" must be_==(JInt(0))
    brand \ "imported" must be_==(JNothing)
    brand \ "fr" \ "name" must be_==(JString("Philips"))
    brand \ "fr" \ "website" must be_==(JString("http://www.philips.com"))
  }

  def checkBrandNike(brand: JValue) : MatchResult[JValue] = {
    brand \ "id" must be_==(JInt(41))
    brand \ "twitter" must be_==(JNull)
    brand \ "hide" must be_==(JBool(false))
    brand \ "website" must be_==(JString("http://www.nike.com/fr/fr_fr/"))
    brand \ "description" must be_==(JNull)
    brand \ "name" must be_==(JString("Nike"))
    brand \ "increments" must be_==(JInt(0))
    brand \ "imported" must be_==(JNothing)
    brand \ "fr" \ "name" must be_==(JString("Nike"))
    brand \ "fr" \ "website" must be_==(JString("http://www.nike.com/fr/fr_fr/"))
    brand \ "de" \ "website" must be_==(JString("http://www.nike.com/de/de_de/"))
    brand \ "en" \ "website" must be_==(JString("http://www.nike.com"))
    brand \ "es" \ "website" must be_==(JString("http://www.nike.com/es/es_es/"))
  }

  def checkBrandPuma(brand: JValue) : MatchResult[JValue] = {
    brand \ "id" must be_==(JInt(46))
    brand \ "twitter" must be_==(JNull)
    brand \ "hide" must be_==(JBool(false))
    brand \ "website" must be_==(JString("http://www.shop.puma.fr"))
    brand \ "description" must be_==(JNull)
    brand \ "name" must be_==(JString("Puma"))
    brand \ "increments" must be_==(JInt(0))
    brand \ "imported" must be_==(JNothing)
    brand \ "fr" \ "name" must be_==(JString("Puma"))
    brand \ "fr" \ "website" must be_==(JString("http://www.shop.puma.fr"))
    brand \ "de" \ "website" must be_==(JString("http://www.shop.puma.de"))
    brand \ "en" \ "website" must be_==(JString("http://www.puma.com"))
    brand \ "es" \ "website" must be_==(JString("http://www.puma.com"))
  }

  def checkBrandHideBrand(brand: JValue) : MatchResult[JValue] = {
    brand \ "id" must be_==(JInt(51))
    brand \ "twitter" must be_==(JNull)
    brand \ "hide" must be_==(JBool(true))
    brand \ "website" must be_==(JString("http://www.google.fr"))
    brand \ "description" must be_==(JNull)
    brand \ "name" must be_==(JString("Hide brand"))
    brand \ "increments" must be_==(JInt(0))
    brand \ "imported" must be_==(JNothing)
    brand \ "fr" \ "name" must be_==(JString("Hide brand"))
    brand \ "fr" \ "website" must be_==(JString("http://www.google.fr"))
  }
}
