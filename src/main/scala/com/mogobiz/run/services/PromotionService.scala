package com.mogobiz.run.services

import akka.actor.ActorRef
import com.mogobiz.run.actors.PromotionActor.QueryPromotionRequest
import com.mogobiz.run.implicits.Json4sProtocol._
import com.mogobiz.run.model.RequestParameters.PromotionRequest
import org.json4s._
import spray.http.StatusCodes
import spray.routing.Directives

import scala.concurrent.ExecutionContext
import scala.util.Try

class PromotionService(storeCode: String, actor: ActorRef)(implicit executionContext: ExecutionContext) extends Directives with DefaultComplete {

  import akka.pattern.ask
  import akka.util.Timeout
  
import scala.concurrent.duration._

  implicit val timeout = Timeout(2.seconds)

  val route = {
    pathPrefix("promotions") {
      promotions
    }
  }

  lazy val promotions = pathEnd {
    get {
      parameters(
        'maxItemPerPage.?
        , 'pageOffset.?
        , 'orderBy.?
        , 'orderDirection.?
        , 'categoryPath.?
        , 'lang ? "_all").as(PromotionRequest) { params =>
        //(maxItemPerPage, pageOffset, orderBy, orderDirection, categoryPath, lang) =>
          onComplete((actor ? QueryPromotionRequest(storeCode, params)).mapTo[Try[JValue]]) { call =>
            handleComplete(call, (json: JValue) => complete(StatusCodes.OK, json))
          }
      }
    }
  }
}
