/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.es

import java.io.File
import java.nio.file.FileVisitResult._
import java.nio.file.Files._
import java.nio.file.Paths.get
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor}

import com.mogobiz.run.config.Settings._
import com.sksamuel.elastic4s.embedded.LocalNode
import com.typesafe.scalalogging.Logger
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.node.Node
import org.slf4j.LoggerFactory

/**
  *
  */
trait ElasticSearchNode {

  def prepareRefresh(node: Node): Unit = {
    node.client().admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet();
    node.client().admin().indices().prepareRefresh().execute().actionGet()
  }

  def startES(esPath: String): Node

  def stopES(node: Node): Unit
}

trait EmbeddedElasticSearchNode extends ElasticSearchNode {

  private val logger = Logger(LoggerFactory.getLogger("esNode"))

  val esHeadPlugin = "mobz/elasticsearch-head"
  val icuPlugin    = "elasticsearch/elasticsearch-analysis-icu/2.2.0"
  val plugins      = Seq(esHeadPlugin)

  def startES(esPath: String = EsEmbedded): Node = {
    logger.info(s"ES is starting using source path '$esPath'...")
    // Copie le jeu de données dans un répertoire temporaire
    val tmpdir: String = s"${System.getProperty("java.io.tmpdir")}${System.currentTimeMillis()}/data"
    new File(tmpdir).mkdirs()

    implicit def toPath(filename: String): Path = {
      val c = filename.charAt(0)
      if ((c == '/' || c == '\\') && c.toString != File.separator) get(filename.substring(1))
      else get(filename)
    }

    Files.walkFileTree(esPath, new SimpleFileVisitor[Path]() {
      @Override
      override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult = {
        Files.createDirectories(tmpdir.resolve(esPath.relativize(dir)))
        CONTINUE
      }

      @Override
      override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
        copy(file, tmpdir.resolve(esPath.relativize(file)))
        CONTINUE
      }
    })
    val settings = Settings
      .builder()
      .put("cluster.name", EsCluster)
      .put("path.data", tmpdir)
      .put("script.disable_dynamic", false)
      .build()

    val esNode: Node = LocalNode(settings)

    // On attend que ES ait bien démarré
    esNode.client().admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet()
    logger.info(s"ES is started using '$tmpdir'.")
    esNode
  }

  def stopES(node: Node): Unit = {
    logger.info("ES is stopping...")
    if (!node.isClosed) {
      node.close()
    }
    new File(node.settings().get("path.data")).delete()
    logger.info("ES is stopped.")
  }

}
