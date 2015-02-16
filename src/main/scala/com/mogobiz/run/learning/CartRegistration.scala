package com.mogobiz.run.learning

import com.mogobiz.es.EsClient
import com.mogobiz.run.model.MogoLearn.CartAction


object CartRegistration {
  def esStore(store: String): String = s"${store}_learning"

  def register(store: String, trackingid: String, itemids: Seq[String]): String = {
    EsClient.index(esStore(store), CartAction(trackingid, itemids.mkString(" ")), false)
  }
}

