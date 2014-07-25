package com.mogobiz.services

import akka.actor.ActorRef
import com.mogobiz.actors.TagActor.QueryTagRequest
import org.json4s.JsonAST.JValue
import spray.routing.Directives

import scala.concurrent.ExecutionContext

class TagService(storeCode: String, tag: ActorRef)(implicit executionContext: ExecutionContext) extends Directives {

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
      parameters('hidden ? false, 'inactive ? false, 'lang ? "_all") { (hidden, inactive, lang) =>
        val tagRequest = QueryTagRequest(storeCode, hidden, inactive, lang)
        complete {
          (tag ? tagRequest).mapTo[JValue]
        }
      }
    }
  }


}
