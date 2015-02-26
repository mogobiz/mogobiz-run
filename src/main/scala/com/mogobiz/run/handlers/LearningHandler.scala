package com.mogobiz.run.handlers

import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl._
import akka.stream.stage.{TerminationDirective, Directive, Context, PushStage}
import com.mogobiz.es.EsClient
import com.mogobiz.run.model.Learning.UserAction.UserAction

import com.mogobiz.run.model.Learning._
import com.mogobiz.system.BootedMogobizSystem
import com.sksamuel.elastic4s.ElasticDsl.{search => esearch4s, _}
import com.typesafe.scalalogging.slf4j.LazyLogging
import org.elasticsearch.search.sort.SortOrder

import scala.concurrent.Future

class LearningHandler extends BootedMogobizSystem  with LazyLogging {
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
      by field "dateCreated" order SortOrder.DESC
    }

    val itemids = (EsClient searchAllRaw historyReq).getHits map (_.sourceAsMap().get("itemid"))

    val req = esearch4s in esStore(store) -> "Prediction" limit count query {
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

  def fis(store: String, productId: String, frequency: Double = 0.2): Future[(Seq[String], Seq[String])] = {
    implicit val _ = ActorFlowMaterializer()

    import com.mogobiz.run.learning._

    val source = Source.single((productId, frequency))

    val extract = Flow[(Option[CartCombination], Option[CartCombination])].map((x) => {
      (
        x._1.map(_.combinations.toSeq).getOrElse(Seq.empty),
        x._2.map(_.combinations.toSeq).getOrElse(Seq.empty))
    })

    val exclusion = Flow[(Seq[String], Seq[String])].map(x => (x._1.filter(_ != productId), x._2.filter(_ != productId)))

    val head = Sink.head[(Seq[String], Seq[String])]

    val runnable:RunnableFlow = source
      .transform(() => new LoggingStage[(String, Double)]("Learning"))
      .via(frequentItemSets(store))
      .via(extract)
      .via(exclusion)
      .to(head)

    runnable.run().get(head)
  }
}


// Logging elements of a stream
// mysource.transform(() => new LoggingStage(name))
class LoggingStage[T](private val name:String)(implicit system:ActorSystem) extends PushStage[T, T] {
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
  val res = l.cooccurences("mogobiz", "718", Purchase)
  l.browserHistory("mogobiz", "119", Purchase, 10, 20, 3)
}