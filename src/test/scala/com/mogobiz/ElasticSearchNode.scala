package com.mogobiz

import com.typesafe.scalalogging.slf4j.Logger
import org.elasticsearch.node.{NodeBuilder, Node}
import org.elasticsearch.common.collect.Tuple
import org.elasticsearch.common.settings.{ImmutableSettings, Settings}
import org.elasticsearch.env.Environment
import org.elasticsearch.node.internal.InternalSettingsPreparer
import org.elasticsearch.common.settings.ImmutableSettings.Builder.EMPTY_SETTINGS
import org.elasticsearch.common.io.FileSystemUtils
import org.elasticsearch.plugins.PluginManager
import org.elasticsearch.common.unit.TimeValue
import java.io.IOException

import com.mogobiz.config.Settings._
import org.slf4j.LoggerFactory

/**
 *
 * Created by smanciot on 08/09/14.
 */
trait ElasticSearchNode {

  def start:Unit

}

trait EmbeddedElasticSearchNode extends ElasticSearchNode {

  private val logger = Logger(LoggerFactory.getLogger("ElasticSearchClient"))

  val esHeadPlugin = "mobz/elasticsearch-head"

  lazy val node:Node = {
    val initialSettings : Tuple[Settings, Environment]= InternalSettingsPreparer.prepareSettings(EMPTY_SETTINGS, true)
    if (!initialSettings.v2().pluginsFile().exists()) {
      FileSystemUtils.mkdirs(initialSettings.v2().pluginsFile())
    }

    val pluginManager : PluginManager = new PluginManager(initialSettings.v2(), null, PluginManager.OutputMode.VERBOSE, TimeValue.timeValueMillis(0))
    pluginManager.removePlugin(esHeadPlugin)
    try {
      pluginManager.downloadAndExtract(esHeadPlugin)
    }
    catch {
      case e: IOException  =>
        logger.error(e.getMessage)
    }

    var settings : ImmutableSettings.Builder = ImmutableSettings.settingsBuilder()
    val t =  getClass.getResource("/es/data").getPath
    settings = settings.put("path.data", t)

    val esNode: Node = NodeBuilder.nodeBuilder().local(false).clusterName(EsCluster).settings(settings).node()

    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run() : Unit = {
        logger.info("ES is stopped.")
        esNode.close()
      }
    })

    logger.info("ES is starting...")
    esNode
  }

  override def start : Unit = {
    if(node.isClosed){
      node.start()
    }
  }
}