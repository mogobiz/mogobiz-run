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
      last ~
        cooccurrence ~
        similarity
    }
  }

  lazy val similarity = path(Segment / "history") { uuid =>
    get {
      parameters('action, 'history_count.as[Int], 'count.as[Int], 'match_count.as[Int], 'customer.?) {
        (action, historyCount, count, matchCount, customer) =>
          handleCall(learningHandler.browserHistory(storeCode, uuid, UserAction.withName(action), historyCount, count, matchCount, customer),
            (res: Seq[String]) => complete(StatusCodes.OK, res)
          )
      }
    }
  }

  lazy val last = path(Segment / "last") { uuid =>
    get {
      parameter('count.as[Int]) {
        count =>
          handleCall(learningHandler.browserHistory(storeCode, uuid, UserAction.View, count, -1, -1, None),
            (res: Seq[String]) => complete(StatusCodes.OK, res)
          )
      }
    }
  }

  lazy val cooccurrence = path(Segment / "cooccurrence") { productId =>
    get {
      parameters('action, 'customer.?) { (action, customer) =>
        handleCall(learningHandler.cooccurences(storeCode, productId, UserAction.withName(action), customer),
          (res: Seq[String]) => {
            complete(StatusCodes.OK, res)
          }
        )
      }
    }
  }

  lazy val fis = path(Segment / "fis") { productId =>
    get {
      parameters('frequency.as[Double], 'customer.?) {
        (frequency, customer) =>
          handleCall(learningHandler.fis(storeCode, productId, frequency, customer),
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
