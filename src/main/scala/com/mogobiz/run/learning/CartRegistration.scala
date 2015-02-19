package com.mogobiz.run.learning

import com.mogobiz.es.EsClient
import com.mogobiz.run.model.MogoLearn.CartAction

import scala.collection.mutable.ListBuffer


object CartRegistration {
  def esStore(store: String): String = s"${store}_learning"

  def register(store: String, trackingid: String, itemids: Seq[String]): String = {
//    var i = 0
//    val sorted = itemids.map(_.toLong).sorted
//    val combinations : ListBuffer[Seq[String]] = ListBuffer.empty
//    for (i <- 1 to sorted.length) {
//      combinations ++= sorted.combinations(i).map(_.map(_.toString)).toList
//    }
//     calcul de toutes les combinaisons à placer dans un acteur


    EsClient.index(esStore(store), CartAction(trackingid, itemids.mkString(" ")), false)
  }
}

