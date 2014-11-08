package com.mogobiz.run.services

import akka.actor.ActorRef
import com.mogobiz.run.actors.PromotionActor.QueryPromotionRequest
import com.mogobiz.run.implicits.Json4sProtocol._
import com.mogobiz.run.model.Promotion._
import org.json4s._
import spray.routing.Directives

import scala.concurrent.ExecutionContext

class PromotionService(storeCode: String, actor: ActorRef)(implicit executionContext: ExecutionContext) extends Directives {

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
        , 'lang ? "_all").as(PromotionRequest) {
        params =>
          val request = QueryPromotionRequest(storeCode, params)
          complete {
            (actor ? request).mapTo[JValue]
          }
      }
    }
  }
}
