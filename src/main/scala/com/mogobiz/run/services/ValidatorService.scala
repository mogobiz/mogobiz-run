package com.mogobiz.run.services

import java.io.File

import akka.actor.ActorRefFactory
import com.mogobiz.run.config.DefaultComplete
import com.mogobiz.run.config.MogobizHandlers._
import com.mogobiz.utils.MimeTypeTools
import spray.http.{ContentType, MediaType, HttpHeaders}
import spray.routing.{RoutingSettings, Directives}

class ValidatorService (implicit settings:RoutingSettings, refFactory:ActorRefFactory) extends Directives with DefaultComplete {

  val route = {
    path(Segment / "download" / Segment) { (storeCode, key) =>
      get {
        handleCall(validatorHandler.download(storeCode, key),
          (res: (String, File)) => {
            val mediaTypeOpt = MediaType.custom(MimeTypeTools.detectMimeType(res._2).getOrElse("application/octet-stream"))
            respondWithHeader(HttpHeaders.`Content-Disposition`.apply("attachment", Map("filename" -> res._1))) {
              getFromFile(res._2, ContentType(mediaTypeOpt))
            }
          }
        )
      }
    }
  }
}