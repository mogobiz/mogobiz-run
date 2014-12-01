package com.mogobiz.run.services

import akka.actor.ActorRef
import com.mogobiz.run.implicits.Json4sProtocol
import Json4sProtocol._
import com.mogobiz.run.actors.CategoryActor.QueryCategoryRequest
import org.json4s._
import spray.http.StatusCodes
import spray.routing.Directives

import scala.concurrent.ExecutionContext
import scala.util.Try

class CategoryService(storeCode: String, actor: ActorRef)(implicit executionContext: ExecutionContext) extends Directives with DefaultComplete {

  import akka.pattern.ask
  import akka.util.Timeout

import scala.concurrent.duration._

  implicit val timeout = Timeout(2.seconds)

  val route = {
    pathPrefix("categories") {
      categories
    }
  }

  lazy val categories = pathEnd {
    get {
      parameters('hidden ? false, 'parentId.?, 'brandId.?, 'categoryPath.?, 'lang?"_all", 'promotionId?, 'size.as[Option[Int]]) {
        (hidden, parentId, brandId, categoryPath, lang, promotionId, size) =>
            onComplete((actor ? QueryCategoryRequest(storeCode, hidden, parentId, brandId, categoryPath, lang, promotionId, size)).mapTo[Try[JValue]]) { call =>
              handleComplete(call, (json:JValue) => complete(StatusCodes.OK, json))
            }
      }
    }
  }
}
