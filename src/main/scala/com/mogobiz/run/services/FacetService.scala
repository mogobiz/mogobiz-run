package com.mogobiz.run.services

import akka.actor.ActorRef
import com.mogobiz.run.actors.FacetActor.QueryGetFacetRequest
import com.mogobiz.run.model.RequestParameters.FacetRequest
import spray.http.StatusCodes
import spray.routing.Directives
import com.mogobiz.run.implicits.Json4sProtocol
import Json4sProtocol._
import org.json4s._


import scala.concurrent.ExecutionContext
import scala.util.Try

class FacetService (storeCode: String, actor: ActorRef)(implicit executionContext: ExecutionContext) extends Directives with DefaultComplete {
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
      parameters('priceInterval, 'lang ? "_all", 'name.?, 'brandId.?, 'categoryPath.?, 'brandName.?, 'categoryName.?,
        'tags.?, 'notations.?, 'priceMin.?, 'priceMax.?, 'features.?, 'variations.?).as(FacetRequest) {
        param =>
          onComplete ((actor ? QueryGetFacetRequest(storeCode, param)).mapTo[Try[JValue]]){call =>
            handleComplete(call, (json:JValue) => complete(StatusCodes.OK, json))
          }
      }
    }
  }
}
