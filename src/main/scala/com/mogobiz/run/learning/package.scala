package com.mogobiz.run

import akka.stream.scaladsl._
import com.mogobiz.es.EsClient._
import com.mogobiz.run.es._
import com.mogobiz.run.model.Learning._
import com.sksamuel.elastic4s.ElasticDsl.{search => esearch4s, _}
import org.elasticsearch.search.sort.SortOrder

import scala.collection.mutable.ListBuffer

/**
 *
 * Created by smanciot on 22/02/15.
 */
package object learning {

  val combinationsFlow = Flow(){implicit b =>
    import FlowGraphImplicits._

    val undefinedSource = UndefinedSource[Seq[Long]]
    val undefinedSink = UndefinedSink[List[Seq[Long]]]

    undefinedSource ~> Flow[Seq[Long]].map[List[Seq[Long]]](s => {
      val combinations : ListBuffer[Seq[Long]] = ListBuffer.empty
      1 to s.length foreach {i => combinations ++= s.combinations(i)/*.map(_.sorted)*/.toList}
      combinations.toList
    }) ~> undefinedSink

    (undefinedSource, undefinedSink)
  }

  val sumSink = FoldSink[Int, Int](0)(_ + _)

  def loadProductOccurrences(store:String) = Flow[(String, Double)]
    .map(d => (load[CartCombination](esInputStore(store), d._1), d._2))
    .map(d => d._1.map(x => Some(x.uuid, Math.ceil(x.counter * d._2).toLong)).getOrElse(None))


  def cartCombinations(store: String, x: (String, Long)): SearchDefinition = {
    filterRequest(esearch4s in esInputStore(store) -> "CartCombination" query {
      matchall
    }, List(
      createTermFilter("combinations", Some(x._1)),
      createNumericRangeFilter("counter", Some(x._2), None),
      Some(scriptFilter("doc['combinations'].values.size() >= 2"))
    ).flatten) from 0 size 1
  }

  def loadCartCombinationsByFrequency(store: String) = Flow[Option[(String, Long)]].map(_.map((x) =>
    searchAll[CartCombination](cartCombinations(store, x) sort {
      by field "counter" order SortOrder.DESC
    }).headOption).getOrElse(None))

  def loadCartCombinationsBySize(store: String) = Flow[Option[(String, Long)]].map(_.map((x) =>
    searchAll[CartCombination](cartCombinations(store, x) sort {
      by script "doc['combinations'].values.size()" order SortOrder.DESC
    }).headOption).getOrElse(None))


  def frequentItemSets(store: String) = Flow() { implicit builder =>
    import FlowGraphImplicits._

    val undefinedSource = UndefinedSource[(String, Double)]
    val broadcast = Broadcast[Option[(String, Long)]]
    val zip = Zip[Option[CartCombination], Option[CartCombination]]
    val undefinedSink = UndefinedSink[(Option[CartCombination], Option[CartCombination])]

    undefinedSource ~> loadProductOccurrences(store) ~> broadcast ~> loadCartCombinationsByFrequency(store) ~> zip.left
                                                        broadcast ~> loadCartCombinationsBySize(store)      ~> zip.right
    zip.out ~> undefinedSink

    (undefinedSource, undefinedSink)
  }

}
