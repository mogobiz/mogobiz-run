package com.mogobiz.handlers

import com.mogobiz.{ElasticSearchClient, Prefs}

import scala.concurrent.Await
import scala.concurrent.duration._


class PreferenceHandler {

  def getPreferences(storeCode: String, uuid: String): Prefs = {
    //TODO with Elastic4s
    val response = ElasticSearchClient.getPreferences(storeCode, uuid)
    Await.result(response, 10 seconds)
  }

  def savePreference(storeCode: String, uuid: String, params: Prefs): Boolean = {
    //TODO with Elastic4s
    val response = ElasticSearchClient.savePreferences(storeCode, uuid, params)
    Await.result(response, 10 seconds)
  }

}
