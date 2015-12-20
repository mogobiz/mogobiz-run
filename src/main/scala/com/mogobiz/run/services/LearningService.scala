/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import java.text.SimpleDateFormat

import com.mogobiz.run.config.DefaultComplete
import com.mogobiz.run.model.Learning.UserAction
import com.mogobiz.run.config.MogobizHandlers.handlers._
import com.mogobiz.run.implicits.Json4sProtocol
import Json4sProtocol._
import org.json4s._
import spray.http.StatusCodes
import spray.routing.Directives

import scala.concurrent.{ Future, ExecutionContext }
import scala.util.{ Failure, Success }
import com.mogobiz.run.config.MogobizHandlers.handlers._

class LearningService(implicit executionContext: ExecutionContext) extends Directives with DefaultComplete {
  val route = {
    pathPrefix(Segment / "learning") { implicit storeCode =>
      fis ~
        last ~
        cooccurrence ~
        similarity ~
        popular
    }
  }

  def similarity(implicit storeCode: String) = path(Segment / "history") { uuid =>
    get {
      parameters('action, 'history_count.as[Int], 'count.as[Int], 'match_count.as[Int], 'customer.?) {
        (action, historyCount, count, matchCount, customer) =>
          handleCall(learningHandler.browserHistory(storeCode, uuid, UserAction.withName(action), historyCount, count, matchCount, customer),
            (res: Seq[String]) => complete(StatusCodes.OK, res)
          )
      }
    }
  }

  def last(implicit storeCode: String) = path(Segment / "last") { uuid =>
    get {
      parameter('count.as[Int]) {
        count =>
          handleCall(learningHandler.browserHistory(storeCode, uuid, UserAction.View, count, -1, -1, None),
            (res: Seq[String]) => complete(StatusCodes.OK, res)
          )
      }
    }
  }

  def cooccurrence(implicit storeCode: String) = path(Segment / "cooccurrence") { productId =>
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

  def fis(implicit storeCode: String) = path(Segment / "fis") { productId =>
    get {
      parameters('frequency.as[Double], 'customer.?) {
        (frequency, customer) =>
          handleCall(learningHandler.fis(storeCode, productId, frequency, customer),
            (res: (Seq[String], Seq[String])) =>
              complete(StatusCodes.OK, res)
          )
      }
    }
  }

  def popular(implicit storeCode: String) = path("popular") {
    get {
      parameters('action, 'since, 'count.as[Int], 'customer.?) {
        (action, since, count, customer) =>
          handleCall(learningHandler.popular(storeCode, UserAction.withName(action), new SimpleDateFormat("yyyyMMdd").parse(since), count, customer),
            (res: List[String]) => complete(StatusCodes.OK, res)
          )
      }
    }
  }

}
