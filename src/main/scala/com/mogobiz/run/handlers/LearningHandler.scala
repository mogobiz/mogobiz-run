package com.mogobiz.run.handlers

import akka.actor.ActorSystem
import akka.event.Logging
//import akka.stream.ActorFlowMaterializer
//import akka.stream.scaladsl._
import akka.stream.stage.{TerminationDirective, Directive, Context, PushStage}
import com.mogobiz.es.EsClient
import com.mogobiz.es.EsClient._
import com.mogobiz.run.model.Learning.UserAction.UserAction

import com.mogobiz.run.model.Learning._
import com.mogobiz.system.BootedMogobizSystem
//import com.sksamuel.elastic4s.ElasticDsl.index
import com.sksamuel.elastic4s.ElasticDsl.{search => esearch4s, _}
//import com.sksamuel.elastic4s.IndexesTypes
import com.typesafe.scalalogging.slf4j.LazyLogging
import org.elasticsearch.search.sort.SortOrder

//import scala.concurrent.Future

class LearningHandler extends BootedMogobizSystem with LazyLogging {
  def cooccurences(store: String, productId: String, action: UserAction, customer: Option[String]): Seq[String] = {
    val index = action match {
      case UserAction.Purchase => esPurchasePredictions _
      case UserAction.View => esViewPredictions _
    }
    EsClient.load[Prediction](index(store, customer), productId).map { p =>
      action match {
        case UserAction.Purchase => p.purchase
        case UserAction.View => p.view
      }
    }.getOrElse(Nil)
  }

  def browserHistory(store: String, uuid: String, action: UserAction, historyCount: Int, count: Int, matchCount: Int, customer: Option[String]): Seq[String] = {
    val indices = EsClient.listIndices(esLearningStorePattern(store))
    //s"${store}_learning"
    val historyReq = esearch4s in "*" types "UserItemAction" limit historyCount from 0 postFilter {
      and(
        termFilter("action", action.toString),
        termFilter("uuid", uuid)
      )
    } sort {
      by field "dateCreated" order SortOrder.DESC
    }

    val itemids = (EsClient searchAllRaw historyReq).getHits map (_.sourceAsMap().get("itemid").toString)

    if (matchCount > -1) {
      val req = esearch4s in esViewPredictions(store, customer) -> "Prediction" limit count query {
        bool {
          must(
            termsQuery(action.toString, itemids: _*)
              minimumShouldMatch matchCount
          )
        }
      }
      logger.debug(req._builder.toString)
      val predictions = EsClient.searchAll[Prediction](req).map(_.uid)
      predictions.foreach(p => logger.debug(p))
      predictions
    }
    else {
      itemids
    }
  }

  def fis(store: String, productId: String, frequency: Double = 0.2, segment: Option[String]): (Seq[String], Seq[String]) = {

    import com.mogobiz.run.learning._

//    implicit val _ = ActorFlowMaterializer()
//    val source = Source.single((productId, frequency))
//
//    val extract = Flow[(Option[CartCombination], Option[CartCombination])].map((x) => {
//      (
//        x._1.map(_.combinations.toSeq).getOrElse(Seq.empty),
//        x._2.map(_.combinations.toSeq).getOrElse(Seq.empty))
//    })
//
//    val exclude = Flow[(Seq[String], Seq[String])].map(x => (x._1.filter(_ != productId), x._2.filter(_ != productId)))
//
//    val head = Sink.head[(Seq[String], Seq[String])]
//
//    val runnable: RunnableFlow = source
//      .transform(() => new LoggingStage[(String, Double)]("Learning"))
//      .via(frequentItemSets(store, segment))
//      .via(extract)
//      .via(exclude)
//      .to(head)
//
//    runnable.run().get(head)

    val supportThreshold: Option[(String, Long)] = load[CartCombination](
      esFISStore(store, segment), productId
    )
    .map(
      x => Some(x.uuid, Math.ceil(x.counter * frequency).toLong)
    ).getOrElse(None)

    val lmfis: Seq[String] = supportThreshold.map(x => searchAll[CartCombination](cartCombinations(store, segment, x) sort {
      by field "counter" order SortOrder.DESC
    }).headOption).getOrElse(None).map(_.combinations.toSeq).getOrElse(Seq.empty).filter(_ != productId)

    val llfis: Seq[String] = supportThreshold.map(x => searchAll[CartCombination](cartCombinations(store, segment, x) sort {
      by script "doc['combinations'].values.size()" order SortOrder.DESC
    }).headOption).getOrElse(None).map(_.combinations.toSeq).getOrElse(Seq.empty).filter(_ != productId)

    (lmfis, llfis)

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

  override def onUpstreamFailure(cause: Throwable,
                                 ctx: Context[T]): TerminationDirective = {
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
  val res = l.cooccurences("mogobiz", "718", Purchase, None)
  l.browserHistory("mogobiz", "119", Purchase, 10, 20, 3, None)
}
