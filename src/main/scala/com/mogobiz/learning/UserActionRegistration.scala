package com.mogobiz.learning


import com.mogobiz.es.EsClient
import com.mogobiz.model.UserAction._
import com.mogobiz.model.UserItemAction


object UserActionRegistration {
  def esStore(store: String): String = s"${store}_learning"

  def register(store: String, trackingid: String, itemid: String, action: UserAction): Unit = {
    EsClient.index(esStore(store), UserItemAction(trackingid, itemid, action))
  }
}