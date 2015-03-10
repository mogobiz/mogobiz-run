package com.mogobiz

import com.mogobiz.run.es.EmbeddedElasticSearchNode
import com.mogobiz.system.MogobizSystem
import org.elasticsearch.node.Node
import org.specs2.mutable.Specification
import org.specs2.specification.{AfterExample, Step, Fragments}
import org.specs2.time.NoTimeConversions
import spray.http.{MediaTypes, HttpHeaders, ContentType, MediaType}
import spray.testkit.Specs2RouteTest
import spray.routing.HttpService
import com.mogobiz.run.services.MogobizRoutes
import com.mogobiz.run.actors.{ActorSystemLocator}
import com.mogobiz.json.JsonUtil
import org.specs2.matcher.JsonMatchers
import scala.concurrent.duration._
import org.json4s.JsonAST.{JArray, JValue}

abstract class MogobizRouteTest extends Specification with Specs2RouteTest with HttpService with MogobizRoutes with MogobizSystem with JsonMatchers with EmbeddedElasticSearchNode with JsonUtil with NoTimeConversions with AfterExample {
  implicit val routeTestTimeout = RouteTestTimeout(FiniteDuration(5, SECONDS))
  val STORE = "mogobiz"
  val STORE_ACMESPORT = "acmesport"


  def actorRefFactory = system // connect the DSL to the test ActorSystem
  ActorSystemLocator(system)

  sequential

  // Node ES utilisé pour chaque test. Il est créer puis détruit à chaque test
  var esNode : Node = null

  override def map(fs: =>Fragments) = Step(esNode = startES()) ^ fs ^ Step(stopES(esNode))

  override def after = prepareRefresh(esNode)

  val contenTypeJson = ContentType(MediaTypes.`application/json`)

  def checkJArray(j: JValue) : List[JValue] = j match {
    case JArray(a) => a
    case _ => List(j)
  }
}

object StartEmbeddedElasticSearchNodeApp extends App with EmbeddedElasticSearchNode {
  val esNode = startES()

  println("Press Enter to quit")
  System.in.read()

  stopES(esNode)
}