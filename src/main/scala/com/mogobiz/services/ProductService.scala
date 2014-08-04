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
import com.mogobiz.vo.{CommentPutRequest, CommentGetRequest, MogoError, CommentRequest}
import spray.http.StatusCodes

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
      product
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

  lazy val product = pathPrefix(Segment) {
    productId =>
      details(productId.toLong) ~
        dates(productId.toLong) ~
        times(productId.toLong) ~
        comments(productId.toLong)
  }

  def details (productId: Long) = get {
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

  def dates (productId: Long) = path("dates") {
    get {
      parameters('date.?).as(ProductDatesRequest) {
        params =>
          val request = QueryProductDatesRequest(storeCode, params, productId.toLong, uuid)
          complete {
            (actor ? request).mapTo[JValue]
          }
      }
    }
  }

  def times (productId: Long)= path("times") {
    get {
      parameters('date.?).as(ProductTimesRequest) {
        params =>
          val request = QueryProductTimesRequest(storeCode, params, productId.toLong, uuid)
          complete {
            (actor ? request).mapTo[JValue]
          }
      }
    }
  }

  def comments(productId: Long) = path("comments") {
    pathEnd {
      createComment(productId) ~
        getComment(productId)
    } ~ updateComment(productId)
  }


  def updateComment(productId: Long) = {
    path(Segment) {
      commentId => {
        put {
          entity(as[CommentPutRequest]) {
            req =>
              val request = QueryUpdateCommentRequest(storeCode, productId, commentId, req.note == 1)
              onComplete((actor ? request).mapTo[JValue]) {
                case Success(resp) => complete(resp)
                case Failure(t) => t match {
                  case CommentException(code, message) => complete(StatusCodes.BadRequest, (MogoError(code, message)))
                  case _ => complete(StatusCodes.InternalServerError, t.getMessage)
                }
              }
          }
        }
      }
    }
  }

  def getComment(productId: Long) = {
    get {
      parameters('maxItemPerPage.?, 'pageOffset.?).as(CommentGetRequest) {
        req =>
          val request = QueryGetCommentRequest(storeCode, productId, req)
          complete {
            (actor ? request).mapTo[JValue]
          }
      }
    }
  }

  def createComment(productId: Long) = {
    post {
      entity(as[CommentRequest]) {
        req =>
          val request = QueryCreateCommentRequest(storeCode, productId, req)
          onComplete((actor ? request).mapTo[JValue]) {
            case Success(resp) => complete(resp)
            case Failure(t) => t match {
              case CommentException(code, message) => complete(StatusCodes.BadRequest, (MogoError(code, message)))
              case _ => complete(StatusCodes.InternalServerError, t.getMessage)
            }
            //TODO check userId in mogopay before inserting
          }
      }
    }
  }
}
