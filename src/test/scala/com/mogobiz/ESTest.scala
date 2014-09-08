package com.mogobiz

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

/**
 *
 * Created by yoannbaudy on 04/09/14.
 */
object ESTest {

  val esHeadPlugin = "mobz/elasticsearch-head";
  val esICUPlugin = "elasticsearch/elasticsearch-analysis-icu/2.2.0";

  def startIfNecessary() : Node = {
    val initialSettings : Tuple[Settings, Environment]= InternalSettingsPreparer.prepareSettings(EMPTY_SETTINGS, true);
    if (!initialSettings.v2().pluginsFile().exists()) {
      FileSystemUtils.mkdirs(initialSettings.v2().pluginsFile());
    }

    val pluginManager : PluginManager = new PluginManager(initialSettings.v2(), null, PluginManager.OutputMode.VERBOSE, TimeValue.timeValueMillis(0));
    pluginManager.removePlugin(esHeadPlugin)
    try {
      pluginManager.downloadAndExtract(esHeadPlugin);
    }
    catch {
      case e: IOException  =>
        // ignore if exists
        e.printStackTrace()
    }

    var settings : ImmutableSettings.Builder = ImmutableSettings.settingsBuilder()
    val t =  getClass.getResource("/es/data").getPath
    settings = settings.put("path.data", t)

    val esNode: Node = NodeBuilder.nodeBuilder().local(false).clusterName(com.mogobiz.config.Settings.EsCluster).settings(settings).node();
    esNode.start()

    Runtime.getRuntime().addShutdownHook(new Thread() {
      override def run : Unit = {
        println("ES is stopped.")
        esNode.close();
      }
    });

    println("ES is starting...")
    esNode
  }
}
