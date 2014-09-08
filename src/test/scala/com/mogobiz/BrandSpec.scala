package com.mogobiz

import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import spray.routing.HttpService
import com.mogobiz.services.MogobizRoutes
import com.mogobiz.actors.{MogobizActors, MogobizSystem}
import scala.concurrent.duration._

/**
 * Created by yoannbaudy on 07/09/14.
 */
class BrandSpec extends Specification with Specs2RouteTest with HttpService with MogobizRoutes with MogobizActors with MogobizSystem {
  def actorRefFactory = system // connect the DSL to the test ActorSystem
  val STORE = "mogobiz"

  implicit val routeTestTimeout = RouteTestTimeout(FiniteDuration(5, SECONDS))
  val node  = ESTest.startIfNecessary

  "The Brand service" should {
    "return all brands" in {
      //implicit val customTimeout = RouteTestTimeout(60 seconds)
      Get("/store/" + STORE + "/brands") ~> sealRoute(routes) ~> check {
        val r = response;
        val s = responseAs[String]
        s must beNull
      }
    }
  }
}
