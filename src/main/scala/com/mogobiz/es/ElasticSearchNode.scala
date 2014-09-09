package com.mogobiz.es

import java.io.IOException

import com.mogobiz.config.Settings._
import com.typesafe.scalalogging.slf4j.Logger
import org.elasticsearch.common.collect.Tuple
import org.elasticsearch.common.io.FileSystemUtils
import org.elasticsearch.common.settings.ImmutableSettings.Builder.EMPTY_SETTINGS
import org.elasticsearch.common.settings.{ImmutableSettings, Settings}
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.env.Environment
import org.elasticsearch.node.internal.InternalSettingsPreparer
import org.elasticsearch.node.{Node, NodeBuilder}
import org.elasticsearch.plugins.PluginManager
import org.slf4j.LoggerFactory

/**
 *
 * Created by smanciot on 08/09/14.
 */
trait ElasticSearchNode {

  def start():Unit

}

trait EmbeddedElasticSearchNode extends ElasticSearchNode {

  private val logger = Logger(LoggerFactory.getLogger("esNode"))

  val esHeadPlugin = "mobz/elasticsearch-head"
  val icuPlugin = "elasticsearch/elasticsearch-analysis-icu/2.2.0"
  val plugins = Seq(esHeadPlugin, icuPlugin)

  lazy val node:Node = {
    val initialSettings : Tuple[Settings, Environment]= InternalSettingsPreparer.prepareSettings(EMPTY_SETTINGS, true)
    if (!initialSettings.v2().pluginsFile().exists()) {
      FileSystemUtils.mkdirs(initialSettings.v2().pluginsFile())
    }

    val pluginManager : PluginManager = new PluginManager(initialSettings.v2(), null, PluginManager.OutputMode.VERBOSE, TimeValue.timeValueMillis(0))
    plugins.foreach(plugin => {
      pluginManager.removePlugin(plugin)
      try {
        pluginManager.downloadAndExtract(plugin)
      }
      catch {
        case e: IOException  =>
          logger.error(e.getMessage)
      }
    })

    var settings : ImmutableSettings.Builder = ImmutableSettings.settingsBuilder()
    val t =  getClass.getResource("/es/data").getPath
    settings = settings.put("path.data", t).put("script.disable_dynamic", false)

    val esNode: Node = NodeBuilder.nodeBuilder().local(false).clusterName(EsCluster).settings(settings).node()

    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run() : Unit = {
        logger.info("ES is stopped.")
        esNode.close()
      }
    })

    esNode
  }

  override def start() : Unit = {
    if(node.isClosed){
      node.start()
      logger.info("ES is starting...")
    }
  }
}