package com.mogobiz.handlers

import com.mogobiz.es.ElasticSearchClient
import com.mogobiz.model.Prefs

class PreferenceHandler {

  def getPreferences(storeCode: String, uuid: String): Prefs = {
    ElasticSearchClient.getPreferences(storeCode, uuid)
  }

  def savePreference(storeCode: String, uuid: String, params: Prefs): Boolean = {
    ElasticSearchClient.savePreferences(storeCode, uuid, params)
  }

}
