package com.mogobiz

import com.mogobiz.es.EmbeddedElasticSearchNode
import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import spray.routing.HttpService
import com.mogobiz.services.MogobizRoutes
import com.mogobiz.actors.{MogobizActors, MogobizSystem}
import scala.concurrent.duration._
import org.specs2.matcher._
import org.json4s.native.JsonParser
import org.json4s.JsonAST._

/**
 *
 * Created by yoannbaudy on 07/09/14.
 */
class BrandSpec extends Specification with Specs2RouteTest with HttpService with MogobizRoutes with MogobizActors with MogobizSystem with JsonMatchers with EmbeddedElasticSearchNode with JSonTest {
  def actorRefFactory = system // connect the DSL to the test ActorSystem
  val STORE = "mogobiz"

  implicit val routeTestTimeout = RouteTestTimeout(FiniteDuration(5, SECONDS))

  step(start)

  "The Brand service" should {
    "return not hidden brands" in {
      Get("/store/" + STORE + "/brands") ~> sealRoute(routes) ~> check {
        val brands = sortById(JsonParser.parse(responseAs[String]))
        brands must have size(5)
        checkBrandSamsung(brands(0))
        checkBrandSamsung(brands(1))
        checkBrandPhilips(brands(2))
        checkBrandNike(brands(3))
        checkBrandPuma(brands(4))
      }
    }

    "return not hidden brands" in {
      Get("/store/" + STORE + "/brands?hidden=true") ~> sealRoute(routes) ~> check {
        val brands = sortById(JsonParser.parse(responseAs[String]))
        brands must have size(6)
        checkBrandSamsung(brands(0))
        checkBrandSamsung(brands(1))
        checkBrandPhilips(brands(2))
        checkBrandNike(brands(3))
        checkBrandPuma(brands(4))
        checkBrandHideBrand(brands(5))
      }
    }
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
