package com.mogobiz.run.services

import java.io.File

import akka.actor.ActorRefFactory
import com.mogobiz.run.config.HandlersConfig._
import spray.http.HttpHeaders
import spray.routing.{RoutingSettings, Directives}

/**
 * Created by yoannbaudy on 16/03/2015.
 */
class ValidatorService(storeCode: String)(implicit settings:RoutingSettings, refFactory:ActorRefFactory) extends Directives with DefaultComplete {

  val route = {
    path("download" / Segment) { key =>
      get {
        handleCall(validatorHandler.download(storeCode, key),
          (res: (String, File)) =>
            respondWithHeader(HttpHeaders.`Content-Disposition`.apply("attachment", Map("filename" -> res._1))) {
              getFromFile(res._2)
            }
          )
      }
    }
  }
}