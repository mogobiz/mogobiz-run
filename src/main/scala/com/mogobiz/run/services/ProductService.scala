package com.mogobiz.run.services

import com.mogobiz.run.config.HandlersConfig._
import com.mogobiz.run.implicits.Json4sProtocol
import Json4sProtocol._
import com.mogobiz.run.model.RequestParameters._
import com.mogobiz.run.model._
import com.mogobiz.session.Session
import com.mogobiz.session.SessionESDirectives._
import com.mogobiz.pay.implicits.Implicits
import com.mogobiz.pay.implicits.Implicits.MogopaySession
import spray.http.StatusCodes
import spray.routing.Directives
import com.mogobiz.pay.config.MogopayHandlers.accountHandler

import scala.util.{Try}

class ProductService(storeCode: String, uuid: String) extends Directives with DefaultComplete {
  import org.json4s._

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
      parameters('currency.?, 'country.?, 'lang ? "_all") {
        (currency: Option[String], country: Option[String], lang: String) => {
          handleCall(productHandler.getProductHistory(storeCode, uuid, currency, country, lang),
            (products: List[JValue]) => complete(StatusCodes.OK, products))
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
          'id.? ::
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

      productsParams.happly {
        case (maxItemPerPage :: pageOffset :: id :: xtype :: name :: code :: categoryPath :: brandId :: tagName :: notations :: priceRange :: creationDateMin
          :: featured :: orderBy :: orderDirection :: lang :: currencyCode :: countryCode :: promotionId :: hasPromotion :: property :: feature :: variations :: HNil) =>

          val promotionIds = hasPromotion.map(v => {
            if (v) {
              val ids = promotionHandler.getPromotionIds(storeCode)
              if (ids.isEmpty) None
              else Some(ids.mkString("|"))
            } else None
          }) match {
            case Some(s) => s
            case _ => promotionId
          }

          val params = new ProductRequest(
            maxItemPerPage, pageOffset, id, xtype, name, code, categoryPath,
            brandId, tagName, notations, priceRange, creationDateMin,
            featured, orderBy, orderDirection, lang, currencyCode, countryCode, promotionIds, hasPromotion, property, feature, variations
          )
          handleCall(productHandler.queryProductsByCriteria(storeCode, params),
            (json: JValue) => complete(StatusCodes.OK, json))
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
        handleCall(productHandler.queryProductsByFulltextCriteria(storeCode, params),
          (json: JValue) => complete(StatusCodes.OK, json))
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
        handleCall(productHandler.getProductsFeatures(storeCode, params),
          (json: JValue) => complete(StatusCodes.OK, json))
      }
    }
  }

  lazy val notation = path("notation") {
    get {
      parameters('lang ? "_all") { lang =>
        handleCall(productHandler.getProductsByNotation(storeCode, lang),
          (list: List[JValue]) => complete(StatusCodes.OK, list))
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

  def details(productId: Long) = get {
    get {
      parameters(
        'historize ? false // historize is set to true when accessed by end user. Else this may be a technical call to display the product
        , 'visitorId.?
        , 'currency.?
        , 'country.?
        , 'lang ? "_all").as(ProductDetailsRequest) { params =>
        handleCall(productHandler.getProductDetails(storeCode, params, productId.toLong, uuid),
          (json: JValue) => complete(StatusCodes.OK, json))
      }
    }
  }

  def dates(productId: Long) = path("dates") {
    get {
      parameters('date.?) { date =>
        handleCall(productHandler.getProductDates(storeCode, date, productId.toLong, uuid),
          (json: JValue) => complete(StatusCodes.OK, json))
      }
    }
  }

  def times(productId: Long) = path("times") {
    get {
      parameters('date.?) { date =>
        handleCall(productHandler.getProductTimes(storeCode, date, productId.toLong, uuid),
          (json: JValue) => complete(StatusCodes.OK, json))
      }
    }
  }

  def comments(productId: Long) = pathPrefix("comments") {
    pathEnd {
      createComment(productId) ~
        getComment(productId)
    } ~
    path(Segment) {
      commentId => {
        updateComment(productId, commentId) ~
        noteComment(productId, commentId) ~
        deleteComment(productId, commentId)
      }
    }
  }

  def updateComment(productId: Long, commentId: String) = put {
    optionalSession { optSession =>
      entity(as[CommentPutRequest]) { req =>
        val accountId = optSession.flatMap { session: Session => session.sessionData.accountId}
        val account = accountId.map{id => accountHandler.load(id)}.getOrElse(None)
        handleCall(productHandler.updateComment(storeCode, productId, account, commentId, req),
          (res: Unit) => complete(StatusCodes.OK))
      }
    }
  }

  def noteComment(productId: Long, commentId: String) = post {
    entity(as[NoteCommentRequest]) { req =>
      handleCall(productHandler.noteComment(storeCode, productId, commentId, req),
        (res: Unit) => complete(StatusCodes.OK))
    }
  }

  def deleteComment(productId: Long, commentId: String) = delete {
    optionalSession { optSession =>
      val accountId = optSession.flatMap { session: Session => session.sessionData.accountId}
      val account = accountId.map{id => accountHandler.load(id)}.getOrElse(None)
      handleCall(productHandler.deleteComment(storeCode, productId, account, commentId),
        (res: Unit) => complete(StatusCodes.OK))
    }
  }

  def getComment(productId: Long) = get {
    parameters('maxItemPerPage.?, 'pageOffset.?).as(CommentGetRequest) { req =>
      handleCall(productHandler.getComment(storeCode, productId, req),
        (json: JValue) => complete(StatusCodes.OK, json))
    }
  }

  def createComment(productId: Long) = post {
    entity(as[CommentRequest]) { req =>
      optionalSession { optSession =>
        val accountId = optSession.flatMap { session: Session => session.sessionData.accountId}
        val account = if (accountId.isDefined) accountHandler.load(accountId.get) else None
        handleCall(productHandler.createComment(storeCode, productId, req, account),
          (comment: Comment) => complete(StatusCodes.OK, comment))
      }
    }
  }

  def suggestions(productId: Long) = pathPrefix("suggestions") {
    pathEnd {
      get {
        parameters('lang ? "_all") { lang =>
          handleCall(productHandler.querySuggestions(storeCode, productId, lang),
            (list: JValue) => complete(StatusCodes.OK, list))
        }
      }
    }
  }
}
