package com.mogobiz.run.learning

import akka.stream.ActorFlowMaterializer
import com.mogobiz.es.EsClient
import com.mogobiz.run.model.MogoLearn._
import com.mogobiz.system.BootedMogobizSystem
import com.sksamuel.elastic4s.BulkCompatibleDefinition
import org.elasticsearch.action.bulk.BulkResponse

import scala.collection.mutable.ListBuffer

import akka.stream.scaladsl._

object CartRegistration extends BootedMogobizSystem {
  def esStore(store: String): String = s"${store}_learning"

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

  def register(store: String, trackingid: String, itemids: Seq[String]): String = {
    val sorted: Seq[Long] = itemids.map(_.toLong).distinct.sorted

    if(sorted.length > 100){
      EsClient.index(esStore(store), CartAction(trackingid, itemids.mkString(" ")))
    }
    else{
      implicit val _ = ActorFlowMaterializer()
      import com.sksamuel.elastic4s.ElasticDsl._

      val bulkFlow = Flow[List[Seq[Long]]].map(_.map(seq =>
        update(seq.mkString("-"))
          .in(s"${esStore(store)}/CartCombination")
          .upsert("combinations" -> seq.map(_.toString), "counter" -> 1)
          .script("ctx._source.counter += count")
          .params("count" -> 1)
      )).mapConcat[BulkCompatibleDefinition](identity).grouped(100).map(EsClient.bulk(_))

      val sink = ForeachSink[BulkResponse](resp => println(s"${resp.getItems.length} -> ${resp.getTookInMillis} ms"))

      combinationsFlow.via(bulkFlow).runWith(Source.single(sorted), sink)
      ""
    }
  }

}

