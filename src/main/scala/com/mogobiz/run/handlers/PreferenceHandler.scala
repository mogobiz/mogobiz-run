/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import com.mogobiz.es.EsClient
import com.mogobiz.run.model.Prefs
import com.sksamuel.elastic4s.ElasticDsl.{update => esupdate4s, _}

class PreferenceHandler {

  def getPreferences(storeCode: String, uuid: String): Prefs = {
    EsClient.load[Prefs](indexName = prefsIndex(storeCode), uuid = uuid) match {
      case Some(s) => s
      case None    => Prefs(10)
    }
  }

  def savePreference(storeCode: String, uuid: String, params: Prefs): Boolean = {
    EsClient.updateRaw(esupdate4s id uuid in s"${prefsIndex(storeCode)}/prefs" docAsUpsert true docAsUpsert {
      "productsNumber" -> params.productsNumber
    } retryOnConflict 4)
    true
  }

  /**
    * Returns the ES index for store preferences user
    * @param store - store code
    * @return
    */
  private def prefsIndex(store: String): String = {
    s"${store}_prefs"
  }

}
