package com.mogobiz.run.handlers

import com.mogobiz.es.EsClient
import com.mogobiz.run.model.MogoLearn.UserAction.UserAction

import com.mogobiz.run.model.MogoLearn._
import com.sksamuel.elastic4s.ElasticDsl.{search => esearch4s, _}
import org.elasticsearch.search.sort.SortOrder

class LearningHandler {
  def cooccurences(store: String, productId: String, action: UserAction): Seq[String] = {
    val actionString = action.toString
    EsClient.load[Prediction](esStore(store), productId).map(_.purchase).getOrElse(Nil)
  }

  def browserHistory(store: String, uuid: String, action: UserAction, historyCount: Int, count: Int, matchCount: Int): Seq[String] = {
    val historyReq = esearch4s in esInputStore(store) -> "UserItemAction" limit historyCount from 0 filter {
      and(
        termFilter("action", action.toString),
        termFilter("uuid", uuid)
      )
    } sort {
      by field ("dateCreated") order SortOrder.DESC
    }

    val itemids = (EsClient searchAllRaw historyReq).getHits map (_.sourceAsMap().get("itemid"))

    val req = esearch4s in esStore(store) -> "Prediction" limit count query {
      bool {
        must(
          termsQuery(action.toString, itemids: _*)
            minimumShouldMatch (matchCount)
        )
      }
    }
    println(req._builder.toString)
    val predictions = EsClient.searchAll[Prediction](req).map(_.uid)
    predictions.foreach(println)
    predictions
  }

}


object LearningHandler extends App {

  import UserAction._

  val l = new LearningHandler()
  val res = l.cooccurences("mogobiz", "718", Purchase)
  l.browserHistory("mogobiz", "119", Purchase, 10, 20, 3)
}