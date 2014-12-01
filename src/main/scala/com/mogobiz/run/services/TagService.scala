package com.mogobiz.run.services

import akka.actor.ActorRef
import com.mogobiz.run.implicits.Json4sProtocol
import Json4sProtocol._
import com.mogobiz.run.actors.TagActor.QueryTagRequest
import org.json4s._
import spray.http.StatusCodes
import spray.routing.Directives

import scala.concurrent.ExecutionContext
import scala.util.Try

class TagService(storeCode: String, actor: ActorRef)(implicit executionContext: ExecutionContext) extends Directives with DefaultComplete {

  import akka.pattern.ask
  import akka.util.Timeout

import scala.concurrent.duration._

  implicit val timeout = Timeout(2.seconds)

  val route = {
    pathPrefix("tags") {
      tags
    }
  }

  lazy val tags = pathEnd {
    get {
      parameters('hidden ? false, 'inactive ? false, 'lang ? "_all", 'size.as[Option[Int]]) {
        (hidden, inactive, lang, size) =>
          onComplete((actor ? QueryTagRequest(storeCode, hidden, inactive, lang, size)).mapTo[Try[JValue]]){ call =>
            handleComplete(call, (json:JValue) => complete(StatusCodes.OK, json))
          }
      }
    }
  }
}
