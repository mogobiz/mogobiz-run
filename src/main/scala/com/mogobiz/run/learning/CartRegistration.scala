/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.learning

import java.util.{Calendar, Date}

import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl._
import com.mogobiz.es.EsClient._
import com.mogobiz.run.model.Learning._
import com.mogobiz.system.BootedMogobizSystem
import com.sksamuel.elastic4s.bulk.BulkCompatibleDefinition
import com.sksamuel.elastic4s.http.bulk.BulkResponse
import com.sksamuel.elastic4s.script.ScriptDefinition
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

import scala.concurrent.{Await, Future}

object CartRegistration extends BootedMogobizSystem {
  val logger = Logger(
    LoggerFactory.getLogger("com.mogobiz.run.learning.CartRegistration"))

  def computeFIS(store: String,
                 itemids: Seq[String],
                 segment: Option[String]): Unit = {
//    val sorted: Seq[String] = itemids.distinct.sorted
//
//    if (sorted.length > 1 && sorted.length < 100) {
//      implicit val _ = ActorFlowMaterializer()
//
//      val g: FlowGraph = FlowGraph { implicit builder =>
//        import FlowGraphImplicits._
//
//        val source = Source.single(sorted)
//
//        import com.sksamuel.elastic4s.ElasticDsl._
//
//        val transform = Flow[List[Seq[String]]].map(_.map(seq => {
//          val now  = Calendar.getInstance().getTime
//          val uuid = seq.mkString("-")
//          update(uuid)
//            .in(s"${esFISStore(store, segment)}/CartCombination")
//            .upsert("uuid" -> uuid, "combinations" -> seq, "counter" -> 1, "dateCreated" -> now, "lastUpdated" -> now)
//            .script(ScriptDefinition("ctx._source.counter += count;ctx._source.lastUpdated = now",
//                                     params = Map("count" -> 1, "now" -> now)))
//        }))
//        val flatten = Flow[List[BulkCompatibleDefinition]].mapConcat[BulkCompatibleDefinition](identity)
//
//        val count = Flow[BulkResponse].map[Int]((resp) => {
//          val nb = resp.items.length
//          logger.debug(s"index $nb combinations within ${resp.took} ms")
//          nb
//        })
//
//        source ~> combinationsFlow ~> transform ~> flatten ~> bulkBalancedFlow() ~> count ~> sumSink
//      }
//
//      val sum: Future[Int] = g.runWith(sumSink)
//
//      import scala.concurrent.duration._
//      Await.result(sum, 100 seconds) //FIXME
//
//      import system.dispatcher
//
//      val start = new Date().getTime
//
//      sum.foreach(c => logger.debug(s"*** $c combinations indexed within ${new Date().getTime - start} ms"))
//    }
  }

  def register(store: String,
               trackingid: String,
               itemids: Seq[String]): Unit = {
    index(esPurchasePredictions(store, None),
          CartAction(trackingid, itemids.mkString(" ")),
          refresh = false)
    computeFIS(store, itemids, None)
  }

}
