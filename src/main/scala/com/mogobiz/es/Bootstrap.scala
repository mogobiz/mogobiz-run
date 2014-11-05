package com.mogobiz.es

import com.mogobiz.config.Settings
import org.elasticsearch.index.query.TermQueryBuilder

/**
 * Created by hayssams on 27/10/14.
 */
object Bootstrap {

  def createIndex(): Unit = {
    //EsClient.client.client.prepareDeleteByQuery(Settings.EsMLIndex).setQuery(new TermQueryBuilder("_type", "UserHistoryMatrix")).execute.actionGet

  }
}
