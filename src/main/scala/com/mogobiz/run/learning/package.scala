/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run

import akka.stream.scaladsl._
import com.mogobiz.es.EsClient._
import com.mogobiz.run.es._
import com.mogobiz.run.model.Learning._
import com.sksamuel.elastic4s.http.ElasticDsl.{search => esearch4s, _}
import com.sksamuel.elastic4s.searches.SearchDefinition

import scala.collection.mutable.ListBuffer

package object learning {

  val combinationsFlow: Flow[Seq[String], List[Seq[String]]] = Flow() { implicit b =>
    import FlowGraphImplicits._

    val undefinedSource = UndefinedSource[Seq[String]]
    val undefinedSink   = UndefinedSink[List[Seq[String]]]

    undefinedSource ~> Flow[Seq[String]].map[List[Seq[String]]](s => {
      val combinations: ListBuffer[Seq[String]] = ListBuffer.empty
      1 to s.length foreach { i =>
        combinations ++= s.combinations(i) /*.map(_.sorted)*/ .toList
      }
      combinations.toList
    }) ~> undefinedSink

    (undefinedSource, undefinedSink)
  }

  val sumSink: FoldSink[Int, Int] = FoldSink[Int, Int](0)(_ + _)

  def calculateSupportThreshold(store: String, segment: Option[String]) =
    Flow[(String, Double)]
      .map(d => (load[CartCombination](esFISStore(store, segment), d._1), d._2))
      .map(d => d._1.flatMap(x => Some(x.uuid, Math.ceil(x.counter * d._2).toLong)))

  def cartCombinations(store: String, segment: Option[String], x: (String, Long)): SearchDefinition = {
    filterRequest(esearch4s(esFISStore(store, segment) -> "CartCombination") query {
                    matchAllQuery()
                  },
                  List(
                      createtermQuery("combinations", Some(x._1)),
                      createNumericRangeFilter("counter", Some(x._2), None) //,
                      //Some(script("doc['combinations'].values.size() >= 2"))
                  ).flatten) from 0 size 1
  }

  def loadMostFrequentItemSet(
      store: String,
      segment: Option[String]): Flow[Option[(String, Long)], Option[(String, Long)]]#Repr[Option[CartCombination]] =
    Flow[Option[(String, Long)]].map(_.flatMap((x) =>
              searchAll[CartCombination](cartCombinations(store, segment, x) sortByFieldDesc "counter").headOption))

  def loadLargerFrequentItemSet(
      store: String,
      segment: Option[String]): Flow[Option[(String, Long)], Option[(String, Long)]]#Repr[Option[CartCombination]] =
    Flow[Option[(String, Long)]].map(_.flatMap((x) =>
              searchAll[CartCombination](cartCombinations(store, segment, x) scriptfields {
        scriptField("", "doc['combinations'].values.size()")
      }).headOption))

  def frequentItemSets(store: String, segment: Option[String]) =
    Flow() { implicit builder =>
      import FlowGraphImplicits._

      val undefinedSource = UndefinedSource[(String, Double)]
      val broadcast       = Broadcast[Option[(String, Long)]]
      val zip             = Zip[Option[CartCombination], Option[CartCombination]]
      val undefinedSink   = UndefinedSink[(Option[CartCombination], Option[CartCombination])]

      undefinedSource ~> calculateSupportThreshold(store, segment) ~> broadcast ~> loadMostFrequentItemSet(
          store,
          segment) ~> zip.left
      broadcast ~> loadLargerFrequentItemSet(store, segment) ~> zip.right
      zip.out ~> undefinedSink

      (undefinedSource, undefinedSink)
    }

}
