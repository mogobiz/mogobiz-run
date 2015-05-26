package com.mogobiz.run.learning

import java.util.{Calendar, Date}

import akka.stream.ActorFlowMaterializer
import com.mogobiz.es.EsClient
import com.mogobiz.run.model.Learning._
import com.mogobiz.system.BootedMogobizSystem
import com.sksamuel.elastic4s.BulkCompatibleDefinition
import com.typesafe.scalalogging.slf4j.LazyLogging
import org.elasticsearch.action.bulk.BulkResponse

import akka.stream.scaladsl._

import scala.concurrent.{Await, Future}

object CartRegistration extends BootedMogobizSystem with LazyLogging {
  def computeFIS(store: String, itemids: Seq[String]): Unit = {
    val sorted: Seq[String] = itemids.distinct.sorted

    if (sorted.length < 100) {
      implicit val _ = ActorFlowMaterializer()

      val g: FlowGraph = FlowGraph { implicit builder =>
        import FlowGraphImplicits._

        val source = Source.single(sorted)

        import com.sksamuel.elastic4s.ElasticDsl._

        val transform = Flow[List[Seq[String]]].map(_.map(seq => {
          val now = Calendar.getInstance().getTime
          val uuid = seq.mkString("-")
          println("Upserting -->"+uuid)
          update(uuid)
            .in(s"${esFISStore(store)}/CartCombination")
            .upsert(
              "uuid" -> uuid,
              "combinations" -> seq,
              "counter" -> 1,
              "dateCreated" -> now,
              "lastUpdated" -> now)
            .script("ctx._source.counter += count;ctx._source.lastUpdated = now")
            .params("count" -> 1, "now" -> now)
        }))
        val flatten = Flow[List[BulkCompatibleDefinition]].mapConcat[BulkCompatibleDefinition](identity)

        val count = Flow[BulkResponse].map[Int]((resp) => {
          val nb = resp.getItems.length
          logger.debug(s"index $nb combinations within ${resp.getTookInMillis} ms")
          nb
        })

        import EsClient._

        source ~> combinationsFlow ~> transform ~> flatten ~> bulkBalancedFlow() ~> count ~> sumSink
      }

      val sum: Future[Int] = g.runWith(sumSink)

      import scala.concurrent.duration._
      Await.result(sum, 100 seconds)

      import system.dispatcher

      val start = new Date().getTime

      sum.foreach(c => logger.debug(s"*** $c combinations indexed within ${new Date().getTime - start} ms"))
    }
  }

  def register(store: String, trackingid: String, itemids: Seq[String]): Unit = {

    EsClient.index(esPurchasePredictions(store), CartAction(trackingid, itemids.mkString(" ")))

    computeFIS(store, itemids)
  }

}
