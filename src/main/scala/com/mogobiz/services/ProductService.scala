package com.mogobiz.services

import akka.actor.ActorRef
import com.mogobiz.Json4sProtocol._
import com.mogobiz.actors.ProductActor.QueryProductRequest
import spray.routing.Directives
import org.json4s._

import scala.concurrent.ExecutionContext
import com.mogobiz.ProductRequest

class ProductService(storeCode: String, actor: ActorRef)(implicit executionContext: ExecutionContext) extends Directives {

  import akka.pattern.ask
  import akka.util.Timeout
  import scala.concurrent.duration._

  implicit val timeout = Timeout(2.seconds)

  val route = {
    pathPrefix("products") {
      products
    }
  }

  lazy val products = pathEnd {
    get {
      parameters('maxItemPerPage.?
        , 'pageOffset.?
        , 'xtype.?
        , 'name.?
        , 'code.?
        , 'categoryPath.?
        , 'brandId.?
        , 'tagName.?
        , 'priceMin.?
        , 'priceMax.?
        , 'creationDateMin.?
        , 'featured.?
        , 'orderBy.?
        , 'orderDirection.?
        , 'lang ? "_all"
        , 'currency.?
        , 'country.?).as(ProductRequest) {
        productRequest =>
          val request = QueryProductRequest(storeCode, productRequest)
          complete {
            (actor ? request).mapTo[JValue]
          }
      }
    }
  }
}
