package com.mogobiz

import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import spray.routing.HttpService
import com.mogobiz.services.MogobizRoutes
import com.mogobiz.actors.{MogobizActors, MogobizSystem}
import scala.concurrent.duration._
import org.specs2.matcher.JsonMatchers


/**
 * Created by yoannbaudy on 07/09/14.
 */
class BrandSpec extends Specification with Specs2RouteTest with HttpService with MogobizRoutes with MogobizActors with MogobizSystem with JsonMatchers {
  def actorRefFactory = system // connect the DSL to the test ActorSystem
  val STORE = "mogobiz"

  implicit val routeTestTimeout = RouteTestTimeout(FiniteDuration(5, SECONDS))
  val node  = ESTest.startIfNecessary

  "The Brand service" should {
    "return all brands" in {
      Get("/store/" + STORE + "/brands") ~> routes ~> check {
        val r : String = responseAs[String]
        r must /#(0) / ("id" -> 40)
      }
    }
  }
}
