/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.es

/**
 * Created by hayssams on 27/10/14.
 */
object Bootstrap {

  def createIndex(): Unit = {
    //EsClient.client.client.prepareDeleteByQuery(Settings.EsMLIndex).setQuery(new TermQueryBuilder("_type", "UserHistoryMatrix")).execute.actionGet

  }
}
