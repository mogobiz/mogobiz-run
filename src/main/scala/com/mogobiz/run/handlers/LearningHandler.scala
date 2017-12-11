/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import java.util.Date

import akka.actor.ActorSystem
import akka.event.Logging
import com.mogobiz.run.learning.CartRegistration
import com.sksamuel.elastic4s.searches.SearchDefinition
import org.elasticsearch.search.aggregations.bucket.terms.Terms

//import akka.stream.ActorFlowMaterializer
//import akka.stream.scaladsl._

import akka.stream.stage.{Context, Directive, PushStage, TerminationDirective}
import com.mogobiz.es.EsClient
import com.mogobiz.run.model.Learning.UserAction.UserAction
import com.mogobiz.run.model.Learning._
import com.mogobiz.system.BootedMogobizSystem

//import com.sksamuel.elastic4s.ElasticDsl.index

import com.sksamuel.elastic4s.http.ElasticDsl._

//import com.sksamuel.elastic4s.IndexesTypes

import com.typesafe.scalalogging.StrictLogging

//import scala.concurrent.Future

class LearningHandler extends BootedMogobizSystem with StrictLogging {
  def cooccurences(store: String, productId: String, action: UserAction, customer: Option[String]): Seq[String] = {
    val index = action match {
      case UserAction.Purchase => esPurchasePredictions _
      case UserAction.View     => esViewPredictions _
    }
    EsClient
      .load[Prediction](index(store, customer), productId)
      .map { p =>
        action match {
          case UserAction.Purchase => p.purchase
          case UserAction.View     => p.view
        }
      }
      .getOrElse(Nil)
  }

  def browserHistory(store: String,
                     uuid: String,
                     action: UserAction,
                     historyCount: Int,
                     count: Int,
                     matchCount: Int,
                     customer: Option[String]): Seq[String] = {
    val indices = EsClient.listIndices(esLearningStorePattern(store))
    //s"${store}_learning"
    //val historyReq = esearch4s in "*" types "UserItemAction" limit historyCount from 0 postFilter {
    val historyReq = search(indices) types "UserItemAction" limit historyCount from 0 query {
      boolQuery().must(
          termQuery("action", action.toString),
          termQuery("uuid", uuid)
      )
    } sortByFieldDesc "dateCreated"

    val itemids = (EsClient searchAllRaw historyReq).hits map (_.sourceAsMap.get("itemid").toString)

    if (matchCount > -1) {
      val targetIndex = action match {
        case UserAction.View     => esViewPredictions _
        case UserAction.Purchase => esPurchasePredictions _
      }

      val req = search(targetIndex(store, customer) -> "Prediction") limit count query {
        boolQuery().must {
          termsQuery(action.toString, itemids)
        } minimumShouldMatch matchCount
      }

      EsClient.debug(req)
      val predictions = EsClient.searchAll[Prediction](req).map(_.uid)
      predictions.foreach(p => logger.debug(p))
      predictions.distinct
    } else {
      itemids.distinct
    }
  }

  def fis(store: String,
          productId: String,
          frequency: Double = 0.2,
          segment: Option[String]): (Seq[String], Seq[String]) = {
    ???
    //
    //    //    implicit val _ = ActorFlowMaterializer()
    //    //    val source = Source.single((productId, frequency))
    //    //
    //    //    val extract = Flow[(Option[CartCombination], Option[CartCombination])].map((x) => {
    //    //      (
    //    //        x._1.map(_.combinations.toSeq).getOrElse(Seq.empty),
    //    //        x._2.map(_.combinations.toSeq).getOrElse(Seq.empty))
    //    //    })
    //    //
    //    //    val exclude = Flow[(Seq[String], Seq[String])].map(x => (x._1.filter(_ != productId), x._2.filter(_ != productId)))
    //    //
    //    //    val head = Sink.head[(Seq[String], Seq[String])]
    //    //
    //    //    val runnable: RunnableFlow = source
    //    //      .transform(() => new LoggingStage[(String, Double)]("Learning"))
    //    //      .via(frequentItemSets(store, segment))
    //    //      .via(extract)
    //    //      .via(exclude)
    //    //      .to(head)
    //    //
    //    //    runnable.run().get(head)
    //
    //    val supportThreshold: Option[(String, Long)] = EsClient.load[CartCombination](
    //      esFISStore(store, segment), productId
    //    )
    //      .map(
    //        x => Some(x.uuid, Math.ceil(x.counter * frequency).toLong)
    //      ).getOrElse(None)
    //
    //    val lmfis: Seq[String] = supportThreshold.map(x => EsClient.searchAll[CartCombination](cartCombinations(store, segment, x).
    //      sortByFieldDesc("counter")).headOption).getOrElse(None).map(_.combinations.toSeq).getOrElse(Seq.empty).filter(_ != productId)
    //
    //    val llfis: Seq[String] = supportThreshold.map(x => EsClient.searchAll[CartCombination](cartCombinations(store, segment, x) sort {
    //      by script "doc['combinations'].values.size()" order SortOrder.DESC
    //    }).headOption).getOrElse(None).map(_.combinations.toSeq).getOrElse(Seq.empty).filter(_ != productId)
    //
    //    (lmfis, llfis)
    //
  }

  def popular(store: String,
              action: UserAction,
              since: Date,
              count: Int,
              withQuantity: Boolean,
              customer: Option[String]): List[String] = {

    val indices = EsClient.listIndices(esLearningStorePattern(store))
    val filters = List(Option(action).map(x => termQuery("action", x.toString)),
                       customer.map(x => termQuery("segment", x)),
                       Option(since).map(
                           x => rangeQuery("dateCreated").gte(since.getTime.toString).lte(new Date().getTime.toString)
                           //        gte(new SimpleDateFormat("yyyy-MM-dd").format(since)).
                           //        lte(new SimpleDateFormat("yyyy-MM-dd").format(new Date()))
                       )).flatten

    val filterReq = search(indices.toSeq) types "UserItemAction" query {
      boolQuery().must(filters)
    }

    val popularReq: SearchDefinition = filterReq.aggregations {
      if (!withQuantity)
        termsAggregation("top-itemid").field("itemid").size(count).order(Terms.Order.count(false))
      else {
        termsAggregation("top-itemid")
          .field("itemid")
          .subAggregations(
              sumAggregation("total-count").field("count")
          )
          .size(count)
          .order(Terms.Order.aggregation("total-count", false))
      }
    }
    val terms = EsClient.submit(popularReq).await.aggregations.get("top-itemid").asInstanceOf[Terms]
    import collection.JavaConverters._
    terms.getBuckets.asScala.map { bucket =>
      logger.debug(bucket.getKey + "/" + bucket.getDocCount)
      bucket.getKey.toString
    } toList
  }

  def register(store: String, trackingid: String, itemids: Seq[String]): Unit = {
    CartRegistration.register(store, trackingid, itemids)
  }
}

// Logging elements of a stream
// mysource.transform(() => new LoggingStage(name))
class LoggingStage[T](private val name: String)(implicit system: ActorSystem) extends PushStage[T, T] {
  private val log = Logging(system, name)

  override def onPush(elem: T, ctx: Context[T]): Directive = {
    log.info(s"$name -> Element flowing through: {}", elem)
    ctx.push(elem)
  }

  override def onUpstreamFailure(cause: Throwable, ctx: Context[T]): TerminationDirective = {
    log.error(cause, s"$name -> Upstream failed.")
    super.onUpstreamFailure(cause, ctx)
  }

  override def onUpstreamFinish(ctx: Context[T]): TerminationDirective = {
    log.info(s"$name -> Upstream finished")
    super.onUpstreamFinish(ctx)
  }
}

object LearningHandler extends App {

  import UserAction._

  val l = new LearningHandler()
  l.popular("mogobiz", UserAction.Purchase, new Date(), 10, true, None)
  val res = l.cooccurences("mogobiz", "718", Purchase, None)
  l.browserHistory("mogobiz", "119", Purchase, 10, 20, 3, None)
}
