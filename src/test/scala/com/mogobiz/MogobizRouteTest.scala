package com.mogobiz

import com.mogobiz.es.EmbeddedElasticSearchNode
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import spray.testkit.Specs2RouteTest
import spray.routing.HttpService
import com.mogobiz.services.MogobizRoutes
import com.mogobiz.actors.{MogobizActors, MogobizSystem}
import com.mogobiz.implicits.JsonUtil
import org.specs2.matcher.JsonMatchers
import scala.concurrent.duration._
import org.json4s.JsonAST.{JArray, JValue}

/**
 *
 * Created by yoannbaudy on 11/09/14.
 */
abstract class MogobizRouteTest extends Specification with Specs2RouteTest with HttpService with MogobizRoutes with MogobizActors with MogobizSystem with JsonMatchers with EmbeddedElasticSearchNode with JsonUtil with NoTimeConversions {
  implicit val routeTestTimeout = RouteTestTimeout(FiniteDuration(5, SECONDS))
  val STORE = "mogobiz"

  def actorRefFactory = system // connect the DSL to the test ActorSystem

  sequential

  step(start())

  def checkJArray(j: JValue) : List[JValue] = j match {
    case JArray(a) => a
    case _ => List(j)
  }

}
