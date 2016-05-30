/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz

import java.io.{File, IOException}
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

import com.mogobiz.json.JsonUtil
import com.mogobiz.run.config.MogobizRoutes
import com.mogobiz.run.es.EmbeddedElasticSearchNode
import com.mogobiz.system.{ActorSystemLocator, MogobizSystem}
import com.typesafe.scalalogging.LazyLogging
import com.typesafe.scalalogging.slf4j.Logger
import org.elasticsearch.node.Node
import org.json4s.JsonAST.{JArray, JValue}
import org.slf4j.LoggerFactory
import org.specs2.matcher.JsonMatchers
import org.specs2.mutable.Specification
import org.specs2.specification.{AfterExample, Fragments, Step}
import org.specs2.time.NoTimeConversions
import spray.http.{ContentType, MediaTypes}
import spray.routing.HttpService
import spray.testkit.Specs2RouteTest

import scala.concurrent.duration._

abstract class MogobizRouteTest extends Specification with Specs2RouteTest with HttpService with MogobizRoutes with MogobizSystem with JsonMatchers with EmbeddedElasticSearchNode with JsonUtil with NoTimeConversions with AfterExample {
  implicit val routeTestTimeout = RouteTestTimeout(FiniteDuration(5, SECONDS))
  val STORE = "mogobiz"
  val STORE_ACMESPORT = "acmesport"


  def actorRefFactory = system // connect the DSL to the test ActorSystem
  ActorSystemLocator(system)

  sequential

  // Node ES utilisé pour chaque test. Il est créer puis détruit à chaque test
  var esNode : Node = null

  private def copyDerbyDataBase() : Unit = {
    val sourceDerby = Paths.get(getClass.getResource("/derby").getPath)
    val targetDerby = "/tmp/mogobiz/run/test/derby"
    val targetDerbyPath = Paths.get(targetDerby)
    val targetDerbyFile = new File(targetDerby)

    if (targetDerbyFile.exists()) {
      // Suppression du contenu du répertoire
      Files.walkFileTree(targetDerbyPath, new SimpleFileVisitor[Path]() {
        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          Files.delete(file)
          FileVisitResult.CONTINUE
        }
        override def postVisitDirectory(dir: Path, exc: IOException): FileVisitResult = {
          Files.delete(dir)
          FileVisitResult.CONTINUE
        }
      })
    }

    // Création du répertoire
    targetDerbyFile.mkdirs();

    // Copie du répertoire
    Files.walkFileTree(sourceDerby, new SimpleFileVisitor[Path]() {
      @Override
      override def preVisitDirectory(dir:Path, attrs:BasicFileAttributes):FileVisitResult = {
        Files.createDirectories(targetDerbyPath.resolve(sourceDerby.relativize(dir)))
        FileVisitResult.CONTINUE
      }

      @Override
      override def visitFile(file:Path, attrs:BasicFileAttributes):FileVisitResult = {
        Files.copy(file, targetDerbyPath.resolve(sourceDerby.relativize(file)))
        FileVisitResult.CONTINUE
      }
    })

  }

  override def map(fs: =>Fragments) = Step(copyDerbyDataBase()) ^ Step(esNode = startES()) ^ fs ^ Step(stopES(esNode))

  override def after = prepareRefresh(esNode)

  val contenTypeJson = ContentType(MediaTypes.`application/json`)

  def checkJArray(j: JValue) : List[JValue] = j match {
    case JArray(a) => a
    case _ => List(j)
  }
}

object StartEmbeddedElasticSearchNodeApp extends App with EmbeddedElasticSearchNode {
  val logger = Logger(LoggerFactory.getLogger("com.mogobiz.StartEmbeddedElasticSearchNodeApp"))
  val esNode = startES()

  println("Press Enter to quit")
  System.in.read()

  stopES(esNode)
}