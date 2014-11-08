package com.mogobiz.run.services

import akka.actor.ActorRef
import com.mogobiz.run.implicits.Json4sProtocol
import Json4sProtocol._
import com.mogobiz.run.actors.ProductActor.{QueryCompareProductRequest, QueryFindProductRequest, QueryProductRequest, _}
import com.mogobiz.run.model._
import com.mogobiz.run.vo.MogoError
import spray.http.StatusCodes
import spray.routing.Directives

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class ProductService(storeCode: String, uuid: String, actor: ActorRef)(implicit executionContext: ExecutionContext) extends Directives {

  import akka.pattern.ask
  import akka.util.Timeout
  import org.json4s._

import scala.concurrent.duration._

  implicit val timeout = Timeout(2.seconds)

  val route = {
    pathPrefix("products") {
      products ~
      find ~
      compare ~
      notation ~
      product
    } ~
      history
  }

  lazy val history = path("history") {
    get {
      parameters('currency.?, 'country.?, 'lang ? "_all").as(VisitorHistoryRequest) {
        req => {
          val request = QueryVisitedProductRequest(storeCode, req, uuid)
          complete {
            (actor ? request).mapTo[List[JValue]]
          }
        }
      }
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
        , 'notations.?
        , 'priceMin.?
        , 'priceMax.?
        , 'creationDateMin.?
        , 'featured.?
        , 'orderBy.?
        , 'orderDirection.?
        , 'lang ? "_all"
        , 'currency.?
        , 'country.?
        , 'promotionId.?
        , 'property.?
        , 'feature.?
        , 'variations.?).as(ProductRequest) {
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
        , 'highlight ? false
        , 'categoryPath.?).as(FullTextSearchProductParameters) {
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

  lazy val notation = path("notation") {
    get {
      parameters(
        'lang ? "_all") {
        params =>
          val request = QueryNotationProductRequest(storeCode, params)
          complete {
            (actor ? request).mapTo[List[JValue]]
          }
      }
    }
  }

  lazy val product = pathPrefix(Segment) {
    productId =>
      comments(productId.toLong) ~
      details(productId.toLong) ~
      dates(productId.toLong) ~
      times(productId.toLong)
  }

  def details (productId: Long) = get {
    get {
      parameters(
        'historize ? false // historize is set to true when accessed by end user. Else this may be a technical call to display the product
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

  def comments(productId: Long) = pathPrefix("comments") {

      pathEnd {
        createComment(productId) ~
          getComment(productId)
      } ~ updateComment(productId)

  }


  def updateComment(productId: Long) = path(Segment) {
    commentId => {
      put {
        entity(as[CommentPutRequest]) {
          req =>
            val request = QueryUpdateCommentRequest(storeCode, productId, commentId, req.note == 1)
            onSuccess(actor ? request) {
              res =>
                complete("")
            }
        }
      }
    }
  }


  def getComment(productId: Long) = get {
      parameters('maxItemPerPage.?, 'pageOffset.?).as(CommentGetRequest) {
        req =>
          val request = QueryGetCommentRequest(storeCode, productId, req)
          complete {
            (actor ? request).mapTo[JValue]
          }
      }
    }


  def createComment(productId: Long) = post {
      entity(as[CommentRequest]) {
        req =>
          val request = QueryCreateCommentRequest(storeCode, productId, req)
          complete{
            (actor ? request) map {
              case Success(r) =>  r
              case Failure(t) => t match {
                case CommentException(code, message) => complete(StatusCodes.BadRequest, MogoError(code, message))
                case _ => complete(StatusCodes.InternalServerError, t.getMessage)
              }
              //TODO check userId in mogopay before inserting
            }
          }
      }
    }

}
