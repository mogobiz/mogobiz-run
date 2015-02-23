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

    import EsClient._

    val loadProductOccurences: Flow[String, Option[(String, Long)]] = Flow[String].map(load[CartCombination](esInputStore(store), _))
      .map(_.map(x => Some(x.uuid, Math.ceil(x.counter * frequency).toLong)).getOrElse(None))
      .transform(() => new LoggingStage[Option[(String, Long)]]("Learning"))

    import com.mogobiz.run.es._

    def cartCombinations(x: (String, Long)): SearchDefinition = {
      filterRequest(esearch4s in esInputStore(store) -> "CartCombination" query {
        matchall
      }, List(
        createTermFilter("combinations", Some(x._1)),
        createNumericRangeFilter("counter", Some(x._2), None),
        Some(scriptFilter("doc['combinations'].values.size() >= 2"))
      ).flatten) from 0 size 1
    }

    val loadCartCombinationsByFrequency = Flow[Option[(String, Long)]].map(_.map((x) =>
      searchAll[CartCombination](cartCombinations(x) sort {
        by field "counter" order SortOrder.DESC
      }).headOption).getOrElse(None))

    val loadCartCombinationsBySize = Flow[Option[(String, Long)]].map(_.map((x) =>
      searchAll[CartCombination](cartCombinations(x) sort {
        by script "doc['combinations'].values.size()" order SortOrder.DESC
      }).headOption).getOrElse(None))

    val flow = Flow() { implicit builder =>
      import FlowGraphImplicits._

      val undefinedSource = UndefinedSource[String]
      val broadcast = Broadcast[Option[(String, Long)]]
      val zip = Zip[Option[CartCombination], Option[CartCombination]]
      val extractCombinations = Flow[(Option[CartCombination], Option[CartCombination])].map((x) => {
        (x._1.map(_.combinations.toSeq).getOrElse(Seq.empty), x._2.map(_.combinations.toSeq).getOrElse(Seq.empty))
      })
      val undefinedSink = UndefinedSink[(Seq[String], Seq[String])]

      undefinedSource ~> loadProductOccurences ~> broadcast ~> loadCartCombinationsByFrequency ~> zip.left
                                                  broadcast ~> loadCartCombinationsBySize      ~> zip.right
      zip.out ~> extractCombinations ~> undefinedSink

      (undefinedSource, undefinedSink)
    }

    val source = Source.single(productId)

    val sink = Sink.head[(Seq[String], Seq[String])]

    val runnable:RunnableFlow = source.via(flow).to(sink)

    runnable.run().get(sink)
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