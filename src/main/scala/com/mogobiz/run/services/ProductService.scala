package com.mogobiz.run.services

import akka.actor.ActorRef
import com.mogobiz.run.implicits.Json4sProtocol
import Json4sProtocol._
import com.mogobiz.run.actors.ProductActor.{QueryCompareProductRequest, QueryFindProductRequest, QueryProductRequest, _}
import com.mogobiz.run.model.RequestParameters._
import com.mogobiz.run.model._
import com.mogobiz.session.Session
import com.mogobiz.session.SessionESDirectives._
import com.mogobiz.pay.implicits.Implicits
import com.mogobiz.pay.implicits.Implicits.MogopaySession
import spray.http.StatusCodes
import spray.routing.Directives
import com.mogobiz.pay.config.MogopayHandlers.accountHandler

import scala.concurrent.ExecutionContext
import scala.util.{Try}

class ProductService(storeCode: String, uuid: String, actor: ActorRef)(implicit executionContext: ExecutionContext) extends Directives with DefaultComplete {

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
      parameters('currency.?, 'country.?, 'lang ? "_all"){
        (currency: Option[String], country: Option[String], lang: String) => {
          onComplete((actor ? QueryVisitedProductRequest(storeCode, uuid, currency, country, lang)).mapTo[Try[List[JValue]]]) { call =>
            handleComplete(call, (products: List[JValue]) => complete(StatusCodes.OK, products))
          }
        }
      }
    }
  }

  import shapeless._

  lazy val products = pathEnd {
    get {
      val productsParams = parameters(

        'maxItemPerPage.?.as[Option[Int]] ::
          'pageOffset.?.as[Option[Int]] ::
          'xtype.? ::
          'name.? ::
          'code.? ::
          'categoryPath.? ::
          'brandId.? ::
          'tagName.? ::
          'notations.? ::
          'priceRange.? ::
          'creationDateMin.? ::
          'featured.?.as[Option[Boolean]] ::
          'orderBy.? ::
          'orderDirection.? ::
          'lang ? "_all" ::
          'currency.? ::
          'country.? ::
          'promotionId.? ::
          'hasPromotion.?.as[Option[Boolean]] ::
          'property.? ::
          'feature.? ::
          'variations.? :: HNil
      )

      productsParams.happly{
          case (maxItemPerPage :: pageOffset  :: xtype :: name :: code :: categoryPath :: brandId :: tagName :: notations :: priceRange :: creationDateMin
          :: featured :: orderBy :: orderDirection :: lang :: currencyCode :: countryCode :: promotionId :: hasPromotion :: property :: feature :: variations :: HNil) =>

        val params = new ProductRequest(
          maxItemPerPage,pageOffset,xtype,name,code,categoryPath,
          brandId, tagName, notations, priceRange, creationDateMin,
          featured, orderBy, orderDirection, lang, currencyCode, countryCode, promotionId, hasPromotion, property, feature, variations
        )
          onComplete ((actor ? QueryProductRequest(storeCode, params)).mapTo[Try[JValue]]){ call =>
            handleComplete(call, (json:JValue) => complete(StatusCodes.OK, json))
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
        , 'size ? 10
        , 'categoryPath.?).as(FullTextSearchProductParameters) { params =>
          onComplete ((actor ? QueryFindProductRequest(storeCode, params)).mapTo[Try[JValue]]){ call =>
            handleComplete(call, (json:JValue) => complete(StatusCodes.OK, json))
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
        , 'ids).as(CompareProductParameters) { params =>
          onComplete ((actor ? QueryCompareProductRequest(storeCode, params)).mapTo[Try[JValue]]){ call =>
            handleComplete(call, (json:JValue) => complete(StatusCodes.OK, json))
          }
      }
    }
  }

  lazy val notation = path("notation") {
    get {
      parameters('lang ? "_all") { lang =>
        onComplete((actor ? QueryNotationProductRequest(storeCode, lang)).mapTo[Try[List[JValue]]]) { call =>
          handleComplete(call, (list: List[JValue]) => complete(StatusCodes.OK, list))
        }
      }
    }
  }

  lazy val product = pathPrefix(Segment) {
    productId =>
      comments(productId.toLong) ~
      suggestions(productId.toLong) ~
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
        , 'lang ? "_all").as(ProductDetailsRequest) { params =>
          onComplete ((actor ? QueryProductDetailsRequest(storeCode, params, productId.toLong, uuid)).mapTo[Try[JValue]]){ call =>
            handleComplete(call, (json:JValue) => complete(StatusCodes.OK, json))
          }
      }
    }
  }

  def dates (productId: Long) = path("dates") {
    get {
      parameters('date.?) { date =>
        onComplete ((actor ? QueryProductDatesRequest(storeCode, date, productId.toLong, uuid)).mapTo[Try[JValue]]){ call =>
          handleComplete(call, (json:JValue) => complete(StatusCodes.OK, json))
        }
      }
    }
  }

  def times (productId: Long)= path("times") {
    get {
      parameters('date.?) { date =>
        onComplete ((actor ? QueryProductTimesRequest(storeCode, date, productId.toLong, uuid)).mapTo[Try[JValue]]){ call =>
          handleComplete(call, (json:JValue) => complete(StatusCodes.OK, json))
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
        entity(as[CommentPutRequest]) { req =>
          onComplete((actor ? QueryUpdateCommentRequest(storeCode, productId, commentId, req.note == 1)).mapTo[Try[Boolean]]) { call =>
            handleComplete(call, (res:Boolean) => complete(StatusCodes.OK, res))
          }
        }
      }
    }
  }

  def getComment(productId: Long) = get {
    parameters('maxItemPerPage.?, 'pageOffset.?).as(CommentGetRequest) { req =>
      onComplete ((actor ? QueryGetCommentRequest(storeCode, productId, req)).mapTo[Try[JValue]]) { call =>
        handleComplete(call, (json:JValue) => complete(StatusCodes.OK, json))
      }
    }
  }

  def createComment(productId: Long) = post {
      entity(as[CommentRequest]) { req =>
        optionalSession { optSession =>
          val accountId = optSession.flatMap { session: Session => session.sessionData.accountId}
          val account = if (accountId.isDefined) accountHandler.find(accountId.get) else None
          onComplete((actor ? QueryCreateCommentRequest(storeCode, productId, req, account)).mapTo[Try[Comment]]) { call =>
            handleComplete(call, (comment: Comment) => complete(StatusCodes.OK, comment))
          }
        }
      }
    }

  def suggestions(productId: Long) = pathPrefix("suggestions") {
    pathEnd {
      get {
        parameters('lang ? "_all") { lang =>
          onComplete((actor ? QuerySuggestionsRequest(storeCode, productId, lang)).mapTo[Try[JValue]]) { call =>
            handleComplete(call, (list: JValue) => complete(StatusCodes.OK, list))
          }
        }
      }
    }
  }
}
