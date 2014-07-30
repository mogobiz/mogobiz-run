package com.mogobiz.services

import akka.actor.ActorRef
import com.mogobiz.Json4sProtocol._
import com.mogobiz.actors.ProductActor.{QueryCompareProductRequest, QueryFindProductRequest, QueryProductRequest}
import spray.routing.Directives
import org.json4s._

import scala.concurrent.ExecutionContext
import com.mogobiz.{CompareProductParameters, FullTextSearchProductParameters, ProductRequest}

class ProductService(storeCode: String, actor: ActorRef)(implicit executionContext: ExecutionContext) extends Directives {

  import akka.pattern.ask
  import akka.util.Timeout
  import scala.concurrent.duration._

  implicit val timeout = Timeout(2.seconds)

  val route = {
    pathPrefix("products") {
      products ~
      find ~
      compare
    }
  }

  lazy val products = pathEnd {
    get {
      parameters(
        'maxItemPerPage.?
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
        params =>
          val request = QueryProductRequest(storeCode, params)
          complete {
            (actor ? request).mapTo[JValue]
          }
      }
    }
  }

  lazy val find = path("find") {
    parameters(
      'lang ? "_all"
      , 'currency.?
      , 'country.?
      , 'query
      , 'highlight ? false).as(FullTextSearchProductParameters) {
      params =>
        val request = QueryFindProductRequest(storeCode,params)
        complete {
          (actor ? request).mapTo[JValue]
        }
    }
  }

  lazy val compare = path("compare") {
    parameters(
      'lang ? "_all"
      , 'currency.?
      , 'country.?
      , 'ids).as(CompareProductParameters) {
      params =>
        val request = QueryCompareProductRequest(storeCode, params)
        complete {
          (actor ? request).mapTo[JValue]
        }
    }
  }
}
