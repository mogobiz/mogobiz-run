package com.mogobiz.services

import akka.actor.ActorRef
import com.mogobiz.Json4sProtocol._
import com.mogobiz.actors.ProductActor._
import spray.routing.Directives
import org.json4s._

import scala.concurrent.ExecutionContext
import com.mogobiz._
import spray.http.MediaTypes._
import com.mogobiz.ProductRequest
import com.mogobiz.CompareProductParameters
import com.mogobiz.FullTextSearchProductParameters
import scala.util.{Failure, Success}
import com.mogobiz.actors.ProductActor.QueryFindProductRequest
import com.mogobiz.ProductRequest
import com.mogobiz.CompareProductParameters
import com.mogobiz.FullTextSearchProductParameters
import com.mogobiz.ProductDetailsRequest
import com.mogobiz.actors.ProductActor.QueryProductRequest
import com.mogobiz.actors.ProductActor.QueryCompareProductRequest

class ProductService(storeCode: String, uuid: String, actor: ActorRef)(implicit executionContext: ExecutionContext) extends Directives {

  import akka.pattern.ask
  import akka.util.Timeout
  import scala.concurrent.duration._

  implicit val timeout = Timeout(2.seconds)

  val route = {
    pathPrefix("products") {
      products ~
      find ~
      compare ~
      details
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
    get {
      parameters(
        'lang ? "_all"
        , 'currency.?
        , 'country.?
        , 'query
        , 'highlight ? false).as(FullTextSearchProductParameters) {
        params =>
          val request = QueryFindProductRequest(storeCode, params)
          complete {
            (actor ? request).mapTo[JValue]
          }
      }
    }
  }

  lazy val compare = path("compare") {
    get {
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

  lazy val details = pathPrefix(Segment) {
    productId => pathEnd {
      get {
        parameters(
          'historize ? false
          , 'visitorId.?
          , 'currency.?
          , 'country.?
          , 'lang ? "_all").as(ProductDetailsRequest) {
          params =>
            val request = QueryProductDetailsRequest(storeCode, params, productId.toLong, uuid)
            complete {
              (actor ? request).mapTo[JValue]
            }
        }
      }
    }
  }
}
