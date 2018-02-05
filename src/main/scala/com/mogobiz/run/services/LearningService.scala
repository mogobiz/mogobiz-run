/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import java.util.UUID

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.HttpCookie
import com.mogobiz.run.config.DefaultComplete
import com.mogobiz.run.config.MogobizHandlers.handlers._
import com.mogobiz.run.config.Settings._
import com.mogobiz.run.model.Learning.UserAction
import org.joda.time.format.DateTimeFormat
import akka.http.scaladsl.server.Directives
import de.heikoseeberger.akkahttpjson4s.Json4sSupport._

import scala.concurrent.ExecutionContext

class LearningService(implicit executionContext: ExecutionContext)
    extends Directives
    with DefaultComplete {
  val route = {
    pathPrefix(Segment / "learning") { implicit storeCode =>
      fis ~
        last ~
        cooccurrence ~
        similarity ~
        popular ~
        commit
    }
  }

  private val format = DateTimeFormat.forPattern("yyyyMMdd")

  def similarity(implicit storeCode: String) = path(Segment / "history") {
    uuid =>
      get {
        parameters('action,
                   'history_count.as[Int],
                   'count.as[Int],
                   'match_count.as[Int],
                   'customer.?) {
          (action, historyCount, count, matchCount, customer) =>
            handleCall(
              learningHandler.browserHistory(storeCode,
                                             uuid,
                                             UserAction.withName(action),
                                             historyCount,
                                             count,
                                             matchCount,
                                             customer),
              (res: Seq[String]) => complete(StatusCodes.OK, res)
            )
        }
      }
  }

  def last(implicit storeCode: String) = path(Segment / "last") { uuid =>
    get {
      parameter('count.as[Int]) { count =>
        handleCall(learningHandler.browserHistory(storeCode,
                                                  uuid,
                                                  UserAction.View,
                                                  count,
                                                  -1,
                                                  -1,
                                                  None),
                   (res: Seq[String]) => complete(StatusCodes.OK, res))
      }
    }
  }

  def cooccurrence(implicit storeCode: String) =
    path(Segment / "cooccurrence") { productId =>
      get {
        parameters('action, 'customer.?) { (action, customer) =>
          handleCall(learningHandler.cooccurences(storeCode,
                                                  productId,
                                                  UserAction.withName(action),
                                                  customer),
                     (res: Seq[String]) => {
                       complete(StatusCodes.OK, res)
                     })
        }
      }
    }

  def fis(implicit storeCode: String) = path(Segment / "fis") { productId =>
    get {
      parameters('frequency.as[Double], 'customer.?) { (frequency, customer) =>
        handleCall(
          learningHandler.fis(storeCode, productId, frequency, customer),
          (res: (Seq[String], Seq[String])) => complete(StatusCodes.OK, res))
      }
    }
  }

  def popular(implicit storeCode: String) = path("popular") {
    get {
      parameters('action,
                 'since,
                 'count.as[Int],
                 'with_quantity.as[Boolean],
                 'customer.?) {
        (action, since, count, with_quantity, customer) =>
          handleCall(
            learningHandler.popular(storeCode,
                                    UserAction.withName(action),
                                    format.parseDateTime(since).toDate,
                                    count,
                                    with_quantity,
                                    customer),
            (res: List[String]) => complete(StatusCodes.OK, res)
          )
      }
    }
  }

  def commit(implicit storeCode: String) = path("commit") {
    optionalCookie(CookieTracking) {
      case Some(mogoCookie) =>
        register(mogoCookie.value)
      case None =>
        val id = UUID.randomUUID.toString
        setCookie(
          HttpCookie(CookieTracking,
                     value = id,
                     path = Some("/api/store/" + storeCode))) {
          register(id)
        }
    }
  }

  def register(uuid: String)(implicit storeCode: String) = {
    get {
      parameters('itemids) { (itemids) =>
        handleCall(
          learningHandler.register(storeCode, uuid, itemids.split(",")),
          (res: Unit) => complete(StatusCodes.OK))
      }
    }
  }
}
