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
import com.typesafe.scalalogging.Logger
import org.elasticsearch.node.Node
import org.json4s.JsonAST.{JArray, JValue}
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}
import org.slf4j.LoggerFactory
import spray.http.{ContentType, MediaTypes}
import spray.routing.HttpService
import spray.testkit.ScalatestRouteTest

import scala.concurrent.duration._

abstract class MogobizRouteTest extends FlatSpec with Matchers with ScalatestRouteTest with HttpService with MogobizRoutes with MogobizSystem with EmbeddedElasticSearchNode with JsonUtil with BeforeAndAfter{
  implicit val routeTestTimeout: RouteTestTimeout = RouteTestTimeout(FiniteDuration(5, SECONDS))
  val STORE = "mogobiz"
  val STORE_ACMESPORT = "acmesport"


  def actorRefFactory = system // connect the DSL to the test ActorSystem
  ActorSystemLocator(system)

  // Node ES utilisé pour chaque test. Il est créé puis détruit à chaque test
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

  //override def map(fs: =>Fragments) = Step(copyDerbyDataBase()) ^ Step(esNode = startES()) ^ fs ^ Step(stopES(esNode))

  after {
    prepareRefresh(esNode)
  }

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