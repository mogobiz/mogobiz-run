package com.mogobiz.run.learning

import com.mogobiz.es.EsClient
import com.mogobiz.run.model.MogoLearn.UserAction.UserAction
import com.mogobiz.run.model.MogoLearn.UserItemAction


object UserActionRegistration {
  def esStore(store: String): String = s"${store}_learning"

  def register(store: String, trackingid: String, itemid: String, action: UserAction): String = {
    EsClient.index(esStore(store), UserItemAction(trackingid, itemid, action), false)
  }
}