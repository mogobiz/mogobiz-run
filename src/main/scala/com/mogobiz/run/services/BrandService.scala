package com.mogobiz.run.services

import akka.actor.ActorRef
import com.mogobiz.run.implicits.Json4sProtocol
import Json4sProtocol._
import com.mogobiz.run.actors.BrandActor.QueryBrandRequest
import org.json4s._
import spray.routing.Directives

import scala.concurrent.ExecutionContext

class BrandService(storeCode: String, actor: ActorRef)(implicit executionContext: ExecutionContext) extends Directives {

  import akka.pattern.ask
  import akka.util.Timeout

import scala.concurrent.duration._

  implicit val timeout = Timeout(2.seconds)

  val route = {
    pathPrefix("brands") {
        brands
    }
  }

  lazy val brands = pathEnd {
    get {
      parameters('hidden ? false, 'categoryPath.?, 'lang ? "_all", 'promotionId.?, 'size.as[Option[Int]]) {
        (hidden, categoryPath, lang, promotionId, size) =>
          val request = QueryBrandRequest(storeCode, hidden, categoryPath, lang, promotionId, size)
          complete {
            (actor ? request).mapTo[JValue]
          }
      }
    }
  }
}
