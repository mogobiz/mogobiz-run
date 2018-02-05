/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import java.io.File

import akka.actor.ActorRefFactory
import akka.http.scaladsl.model.{ContentType, ContentTypes, MediaType}
import akka.http.scaladsl.model.headers.ContentDispositionTypes.attachment
import akka.http.scaladsl.model.headers.`Content-Disposition`
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.settings.RoutingSettings
import com.mogobiz.run.config.DefaultComplete
import com.mogobiz.run.config.MogobizHandlers.handlers._
import com.mogobiz.utils.MimeTypeTools
import de.heikoseeberger.akkahttpjson4s.Json4sSupport._

class ValidatorService(implicit settings: RoutingSettings,
                       refFactory: ActorRefFactory)
    extends Directives
    with DefaultComplete {

  val route = {
    path(Segment / "download" / Segment) { (storeCode, key) =>
      get {
        handleCall(
          validatorHandler.download(storeCode, key),
          (res: (String, File)) => {
            ContentTypes.`application/octet-stream`

            val mediaTypeOpt: MediaType =
              MediaType.custom(value = MimeTypeTools
                                 .detectMimeType(res._2)
                                 .getOrElse("application/octet-stream"),
                               binary = true)
            respondWithHeader(
              `Content-Disposition`(attachment, Map("filename" -> res._1))) {
              getFromFile(res._2, ContentType(mediaTypeOpt))
            }
          }
        )
      }
    }
  }
}
