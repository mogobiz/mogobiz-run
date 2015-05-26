package com.mogobiz.run.services

import com.mogobiz.run.config.DefaultComplete
import com.mogobiz.run.model.Learning.UserAction
import com.mogobiz.run.config.HandlersConfig._
import com.mogobiz.run.implicits.Json4sProtocol
import Json4sProtocol._
import org.json4s._
import spray.http.StatusCodes
import spray.routing.Directives

import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Failure, Success}
import com.mogobiz.run.config.HandlersConfig._


class LearningService(storeCode: String)(implicit executionContext: ExecutionContext) extends Directives with DefaultComplete {
  val route = {
    pathPrefix("learning") {
      fis ~
        cooccurrence ~
        similarity
    }
  }

  lazy val similarity = path(Segment / "history") { uuid =>
    get {
      parameters('action, 'history_count.as[Int], 'count.as[Int], 'match_count.as[Int]) {
        (action, historyCount, count, matchCount) =>
          handleCall(learningHandler.browserHistory(storeCode, uuid, UserAction.withName(action), historyCount, count, matchCount),
            (res: Seq[String]) => complete(StatusCodes.OK, res)
          )
      }
    }
  }

  lazy val cooccurrence = path(Segment / "cooccurrence") { productId =>
    get {
      parameter('action) { action =>
        handleCall(learningHandler.cooccurences(storeCode, productId, UserAction.withName(action)),
          (res: Seq[String]) => {
            complete(StatusCodes.OK, res)
          }
        )
      }
    }
  }

  lazy val fis = path(Segment / "fis") { productId =>
    get {
      parameter('frequency.as[Double]) {
        frequency =>
          handleCall(learningHandler.fis(storeCode, productId, frequency),
            (res: Future[(Seq[String], Seq[String])]) =>
              onComplete(res) {
                case Success(tuple) => complete(StatusCodes.OK, tuple)
                case Failure(e) => complete(StatusCodes.InternalServerError, e)
              }
          )
      }
    }
  }
}
