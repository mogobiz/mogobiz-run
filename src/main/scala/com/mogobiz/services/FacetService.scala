package com.mogobiz.services

import akka.actor.ActorRef
import com.mogobiz.actors.FacetActor.QueryGetFacetRequest
import spray.routing.Directives
import com.mogobiz.json.Json4sProtocol
import Json4sProtocol._
import org.json4s._


import scala.concurrent.ExecutionContext
import scala.util.Try

class FacetService (storeCode: String, actor: ActorRef)(implicit executionContext: ExecutionContext) extends Directives  {
  import akka.pattern.ask
  import akka.util.Timeout

  import scala.concurrent.duration._

  implicit val timeout = Timeout(2.seconds)

  val route = {

    pathPrefix("facets") {
      getFacets
    }
  }

  lazy val getFacets = pathEnd{
    get {
      parameters('priceInterval, 'lang ? "_all") {
        (priceInterval, lang) =>
          val langparam = if(lang=="_all") "" else lang
          val request = QueryGetFacetRequest(storeCode, langparam, priceInterval.toLong)

          complete {
            (actor ? request).mapTo[Try[JValue]]
          }
      }
    }
  }
}
