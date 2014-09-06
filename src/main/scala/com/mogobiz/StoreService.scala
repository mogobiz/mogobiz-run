package com.mogobiz
/*
import com.mogobiz.session.SessionCookieDirectives._
import com.mogobiz.session.Session
*/

import com.mogobiz.actors.TagActor
import com.mogobiz.actors.TagActor.QueryTagRequest
import com.mogobiz.handlers.TagHandler
import com.typesafe.scalalogging.slf4j.Logger
import org.slf4j.LoggerFactory
import spray.http.{StatusCodes, HttpCookie, DateTime}
import spray.routing.{Directives, HttpService}
import spray.http.MediaTypes._
import org.json4s._
import scala.concurrent.{Future, Await, ExecutionContext}
import org.json4s.native.JsonMethods._
import java.util.{Locale, UUID}
import com.mogobiz.cart._
import scala.util.Failure
import com.mogobiz.vo.CommentPutRequest
import com.mogobiz.vo.MogoError
import scala.util.Success
import com.mogobiz.cart.AddToCartCommand
import com.mogobiz.vo.CommentRequest
import com.mogobiz.vo.CommentGetRequest

import akka.actor.{ActorSystem, Props, ActorRef}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import com.mogobiz.actors.TagActor

trait StoreService extends HttpService {
  /// This section to moved elsewhere
  implicit val timeout = Timeout(10.seconds)
  //val tagActor = system.actorOf(Props[TagActor])
  /// END OF This section to moved elsewhere


  import Json4sProtocol._
  import ExecutionContext.Implicits.global

  //private val log = Logger(LoggerFactory.getLogger("StoreService"))

  def storeRoutes(storeCode: String, uuid:String) = {
        pathEnd {
          complete("the store code is " + storeCode)
        } ~ langsRoutes ~
          brandsRoutes(storeCode) ~
          tagsRoutes(storeCode) ~
          countriesRoutes(storeCode) ~
          currenciesRoutes(storeCode) ~
          categoriesRoutes(storeCode) ~
          productsRoutes(storeCode,uuid) ~
          visitedProductsRoute(storeCode,uuid) ~
          preferencesRoute(storeCode, uuid) ~
          cartRoute(storeCode,uuid)
          //testCookie(storeCode)
  }

  val storeRoutesWithCookie = {
    pathPrefix("store" / Segment) {
      storeCode => {
        optionalCookie("mogobiz_uuid") {
          case Some(mogoCookie) => {
            println(s"mogoCookie=${mogoCookie.content}")
            storeRoutes(storeCode, mogoCookie.content)
          }
          case None =>
          {
            val id = UUID.randomUUID.toString
            println(s"new uuid=${id}")
            setCookie(HttpCookie("mogobiz_uuid",content=id,path=Some("/store/" + storeCode))) {
              storeRoutes(storeCode, id)
            }
          }
        }
      }
    }
  }

  def testCookie(storeCode:String) = path("cookie") {
    get{
      optionalCookie("mogobiz_uuid") {
        case Some(mogoCookie) => complete(s"mogoCookie='${mogoCookie.content}")
        case None => //complete("no cookie defined")
        {
          val id = UUID.randomUUID.toString
          setCookie(HttpCookie("mogobiz_uuid",content=id)) {
            complete(s"mogobiz_uuid cookie set to '${id}'")
          }
        }
      }
    }~put{
      val id = UUID.randomUUID.toString
      setCookie(HttpCookie("mogobiz_uuid",content=id)) {
        complete(s"mogobiz_uuid cookie set to '${id}'")
      }
    }~delete{
      deleteCookie("mogobiz_uuid"){
        complete("mogobiz_uuid cookie deleted")
      }
    }
  }

  /**
   *
   *
   *
   * @param storeCode
   * @return
   */
  def brandsRoutes(storeCode: String) = path("brands") {
    respondWithMediaType(`application/json`) {
      get {
        parameters('hidden ? false, 'categoryPath.?, 'lang ? "_all").as(BrandRequest) {
          brandRequest =>

            onSuccess(ElasticSearchClient.queryBrands(storeCode, brandRequest)) {
              response =>

                complete(response)
            }
        }
      }
    }
  }

  def tagsRoutes(storeCode: String) = get {
    path("tags") {
      //TODO hidden and inactive ???
      parameters('hidden ? false, 'inactive ? false, 'lang ? "_all") {
        (hidden, inactive, lang) =>
        // Objet intermediaire permettant de typer les messages à l'acteur
        // pourpermettre au pattern match de fonctionner dans le receive de l'acteur
          val tagRequest = QueryTagRequest(storeCode, hidden, inactive, lang)
          complete {
            "" //(tagActor ? tagRequest).mapTo[JValue]
          }
      }
    }
  }

  val langsRoutes  = get {
    path("langs") {
      respondWithMediaType(`application/json`) {
        complete {
          val langs = List("fr", "en", "de", "it", "es")
          langs
        }
      }
    }
  }

  def countriesRoutes(storeCode: String) = path("countries") {
    respondWithMediaType(`application/json`) {
      get{
        parameters('lang?"_all").as(CountryRequest) { countryReq =>
          onSuccess(
            Future{//TODO call ref to CountryActor
              ElasticSearchClient.queryCountries(storeCode, countryReq.lang)
            }){countries =>
            complete(countries)
          }
        }
      }
    }
  }

  /**
   * http://localhost:8082/store/mogobiz/product/38?currency=EUR&country=FR&lang=FR
   * @param storeCode
   * @return
   */
  def currenciesRoutes(storeCode: String) = path("currencies") {
    respondWithMediaType(`application/json`) {
      get{
        parameters('lang?"_all").as(CurrencyRequest) { currencyReq =>
          onSuccess(
            Future{//TODO call ref to CurrencyActor
              ElasticSearchClient.queryCurrencies(storeCode, currencyReq.lang)
            }){countries =>
            complete(countries)
          }
        }
      }
    }
  }


  def categoriesRoutes(storeCode: String) = path("categories") {
    respondWithMediaType(`application/json`) {
      get{
        parameters('hidden ? false, 'parentId.?, 'brandId.?, 'categoryPath.?, 'lang?"_all").as(CategoryRequest) { categoryReq: CategoryRequest =>
          onSuccess(ElasticSearchClient.queryCategories(storeCode,categoryReq)){ response =>
            val json = parse(response.entity.asString)
            //TODO renvoyer les fils directs si parentId renseigné
            val subset = if (categoryReq.brandId.isDefined) json \ "hits" \ "hits" \ "_source" \ "category" else json \ "hits" \ "hits" \ "_source"
            val result = subset match {
              case JNothing => JArray(List())
              case o:JObject => JArray(List(o))
              case a:JArray => JArray(a.children.distinct)
              case _ => subset
            }
            complete(result)
          }
        }
      }
    }
  }

  /**
   *
   * ex : http://localhost:8082/store/mogobiz/products?currency=EUR&country=FR&lang=FR
   *
   * products?lang=fr&currency=EUR&country=FR&hidden=false&orderBy=price&orderDirection=asc&offset=0&maxItemsPerPage=10
   * products?lang=fr&currency=EUR&country=FR&hidden=false&brandId=1&categoryId=1&code=SHOE&priceMin=0&priceMax=1000&name=puma&xtype=basket&tagName=running&featured=true&orderBy=price&orderDirection=asc&offset=0&maxItemsPerPage=10
   * @param storeCode
   * @return
   */
  def productsRoutes(storeCode:String,uuid:String) = pathPrefix("products") {
    pathEnd{
      respondWithMediaType(`application/json`) {
        //TODO gestion des erreurs si lang,country,currency présent mais null ou vide
        //FIXME currency=eur error

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
          , 'lang?"_all"
          , 'currency.?
          , 'country.?).as(ProductRequest) {

          productRequest =>

            val f = ElasticSearchClient.queryProductsByCriteria(storeCode, productRequest)

            onComplete(f) {
              case Success(products) => complete(products)
              case Failure(t) => {
                t.printStackTrace()
                complete("error", t.getMessage)
              }
            }
        }

      }
    } ~ findRoute(storeCode) ~
      productCompareRoute (storeCode) ~
      productDetailsRoute(storeCode,uuid)

  }

  /**
   * Recherche de produit fulltext
   * @param storeCode
   * @return
   */
  def findRoute(storeCode:String) = path("find") {
    respondWithMediaType(`application/json`) {
      parameters('lang?"_all",'currency.?,'country.?, 'query, 'highlight ? false).as(FullTextSearchProductParameters) {
        req =>
          onSuccess(ElasticSearchClient.queryProductsByFulltextCriteria(storeCode,req)){ products =>
            complete(products)
          }
      }
    }
  }

  /**
   * Compare product within the same category
   * @param storeCode
   * @return
   */
  def productCompareRoute(storeCode:String) = path("compare") {
    respondWithMediaType(`application/json`) {
      parameters('lang?"_all",'currency.?,'country.?, 'ids).as(CompareProductParameters) {
        req =>
          onSuccess(ElasticSearchClient.getProductsFeatures(storeCode, req)){ features =>
            complete(features)
          }
      }
    }
  }


  /**
   * {id}?lang=fr&currency=historize=false&visitorId=1
   * @param storeCode
   * @return
   */
  def productDetailsRoute(storeCode: String, uuid: String) = pathPrefix(Segment) {
    productId => pathEnd {
      respondWithMediaType(`application/json`) {
        get {
          parameters(
            'historize ? false
            , 'visitorId.?
            , 'currency.?
            , 'country.?
            , 'lang ? "_all").as(ProductDetailsRequest) {
            pdr =>
              onSuccess(ElasticSearchClient.queryProductDetails(storeCode, pdr, productId.toLong, uuid)) {
                details =>
                  complete(details)
              }

          }
        }
      } ~
        productDatesRoute(storeCode, productId.toLong) ~
        productTimesRoute(storeCode, productId.toLong) ~
        commentsRoute(storeCode, productId.toLong)
    }
  }

  def productDatesRoute(storeCode:String, productId: Long) = path("dates") {
    respondWithMediaType(`application/json`) {
      get{
        parameters('date.?).as(ProductDatesRequest) {
          pdr =>
            onSuccess(ElasticSearchClient.queryProductDates(storeCode,productId.toLong, pdr)){ response =>
              complete(response)
            }
        }
      }
    }
  }

  def productTimesRoute(storeCode:String, productId: Long) = path("times") {
    respondWithMediaType(`application/json`) {
      get{
        parameters('date.?).as(ProductTimesRequest) {
          pdr =>
            onSuccess(ElasticSearchClient.queryProductTimes(storeCode,productId.toLong, pdr)){ response =>
              complete(response)
              //DateTime.fromIsoDateTimeString("2014-04-18T11:00:00Z") :: DateTime.fromIsoDateTimeString("2014-04-18T23:00:00Z") :: Nil

            }
        }
      }
    }
  }

  def visitedProductsRoute(storeCode:String,uuid:String) = path("history") {
    respondWithMediaType(`application/json`) {
      get {
        parameters('currency.?, 'country.?, 'lang ? "_all").as(VisitorHistoryRequest) {
          req =>
          /*cookie("mogobiz_uuid") { cookie =>
            val uuid = cookie.content
            */
            println(s"visitedProductsRoute with mogobiz_uuid=${uuid}")
            onComplete(ElasticSearchClient.getProductHistory(storeCode,uuid)){
              case Success(ids) => {
                if(ids.isEmpty){
                  complete(List()) //return empty list
                }else{
                  onSuccess(ElasticSearchClient.getProductsByIds(storeCode,ids,ProductDetailsRequest(false,None,req.currency,req.country,req.lang))){ products =>
                    println("visitedProductsRoute returned results",products.length)
                    complete(products)
                  }

                }
              }
              case Failure(t) => complete(t)
            }
          //            }
        }
      }
    }
  }

  /**
   * Path store/(store code)/prefs
   * @param store
   * @param uuid
   * @return
   */
  def preferencesRoute(store:String,uuid:String) = path("prefs") {
    respondWithMediaType(`application/json`) {
      post {
        parameters('productsNumber ? 10).as(Prefs) { prefs =>
          onComplete(ElasticSearchClient.savePreferences(store, uuid, prefs)) {
            case Success(result) => complete(Map("code" -> true))
            case Failure(result) => complete(Map("code" -> false))
          }
        }
      } ~
        get {
          onComplete(
            Future{// TODO call ref to PreferenceActor
              ElasticSearchClient.getPreferences(store, uuid)
            }
          ) { prefs =>
              complete(prefs)
          }
        }
    }
  }

  def commentsRoute(storeCode:String, productId:Long) = pathPrefix("comments"){
    respondWithMediaType(`application/json`) {
      pathEnd{
        post{
          entity(as[CommentRequest]){ req =>
          //TODO check userId in mogopay before inserting
            onComplete(ElasticSearchClient.createComment(storeCode, productId,req)){ //resp =>
              case Success(resp) => complete(resp)
              case Failure(t) => t match {
                case CommentException(code,message) => complete(StatusCodes.BadRequest,(MogoError(code,message)))
                case _ => complete(StatusCodes.InternalServerError,t.getMessage)
              }
            }
          }
        } ~ get{
          parameters('maxItemPerPage.?, 'pageOffset.?).as(CommentGetRequest){ req =>
            onSuccess(ElasticSearchClient.getComments(storeCode,productId,req)){ comments =>
              complete(comments)
            }
          }
        }
      } ~ path(Segment) {
        id => {
          put {
            entity(as[CommentPutRequest]) {
              req =>
                onSuccess(ElasticSearchClient.updateComment(storeCode, productId, id, req.note == 1)) { res =>
                  complete("")
                }
            }
          }
        }
      }
    }
  }

  val cartService = CartBoService
  val cartRenderService = CartRenderService

  //CART ROUTES
  def cartRoute(storeCode:String,uuid:String) = pathPrefix("cart"){
    respondWithMediaType(`application/json`) {
      pathEnd{
        get{
          parameters('currency.?, 'country.?, 'lang ? "_all").as(CartParameters) { params =>
            //TODO get from ES first and if none init from BO
            val cart = cartService.initCart(uuid)

            val lang:String = if(params.lang=="_all") "fr" else params.lang //FIX with default Lang
            //val locale = Locale.forLanguageTag(lang)
            val country = params.country.getOrElse("FR") //FIXME trouver une autre valeur par défaut ou refuser l'appel
            val locale = new Locale(lang,country)

            val currency = ElasticSearchClient.getCurrency(storeCode,params.currency,lang)
            complete(cartRenderService.renderCart(cart, currency,locale))
          }
        }~delete{
          parameters('currency.?, 'country.?, 'lang ? "_all").as(CartParameters) { params =>
            val cart = cartService.initCart(uuid)

            val lang:String = if(params.lang=="_all") "fr" else params.lang //FIX with default Lang
            //val locale = Locale.forLanguageTag(lang)
            val country = params.country.getOrElse("FR") //FIXME trouver une autre valeur par défaut ou refuser l'appel
            val locale = new Locale(lang,country)

            val currency = ElasticSearchClient.getCurrency(storeCode,params.currency,lang)
            val updatedCart = cartService.clear(locale,currency.code,cart)
            complete(cartRenderService.renderCart(updatedCart, currency,locale))
          }
        }
      }~path("items"){
        post{
          parameters('currency.?, 'country.?, 'lang ? "_all").as(CartParameters) { params =>
            entity(as[AddToCartCommand]){
              cmd => {
                val cart = cartService.initCart(uuid)

                val lang:String = if(params.lang=="_all") "fr" else params.lang //FIXME with default Lang
                //val locale = Locale.forLanguageTag(lang)

                val country = params.country.getOrElse("FR") //FIXME trouver une autre valeur par défaut ou refuser l'appel
                val locale = new Locale(lang,country)

                val currency = ElasticSearchClient.getCurrency(storeCode,params.currency,lang)
                try{
                  val updatedCart = cartService.addItem(locale, currency.code, cart, cmd.skuId,cmd.quantity,cmd.dateTime,cmd.registeredCartItems )
                  val data = cartRenderService.renderCart(updatedCart, currency,locale)
                  val response = Map(
                    ("success"->true),
                    ("data"->data),
                    ("errors"->List())
                  )

                  complete(response)

                }catch{
                  case e:AddCartItemException => {
                    val response = Map(
                      ("success"->false),
                      ("data"->cart),
                      ("errors"->e.getErrors(locale))
                    )
                    complete(response)
                  }
                }
              }
            }
          }
        }
      }~path("item" / Segment){
          cartItemId => pathEnd {
            put{
              parameters('currency.?, 'country.?, 'lang ? "_all").as(CartParameters) { params =>
                entity(as[UpdateCartItemCommand]){
                  cmd => {
                    val cart = cartService.initCart(uuid)

                    val lang:String = if(params.lang=="_all") "fr" else params.lang //FIXME with default Lang
                    //val locale = Locale.forLanguageTag(lang)
                    val country = params.country.getOrElse("FR") //FIXME trouver une autre valeur par défaut ou refuser l'appel
                    val locale = new Locale(lang,country)


                    val currency = ElasticSearchClient.getCurrency(storeCode,params.currency,lang)
                    try{
                      val updatedCart = cartService.updateItem(locale, currency.code, cart, cartItemId, cmd.quantity)
                      val data = cartRenderService.renderCart(updatedCart, currency,locale)
                      val response = Map(
                        ("success"->true),
                        ("data"->data),
                        ("errors"->List())
                      )

                      complete(response)
                    }catch{
                      case e:UpdateCartItemException => {
                        val response = Map(
                          ("success"->false),
                          ("data"->cart),
                          ("errors"->e.getErrors(locale))
                        )
                        complete(response)
                      }
                    }
                  }
                }
              }
            }~delete{
              parameters('currency.?, 'country.?, 'lang ? "_all").as(CartParameters) { params =>
                    val cart = cartService.initCart(uuid)

                    val lang:String = if(params.lang=="_all") "fr" else params.lang //FIX with default Lang
                    //val locale = Locale.forLanguageTag(lang)
                    val country = params.country.getOrElse("FR") //FIXME trouver une autre valeur par défaut ou refuser l'appel
                    val locale = new Locale(lang,country)

                    val currency = ElasticSearchClient.getCurrency(storeCode,params.currency,lang)
                    try {
                      val updatedCart = cartService.removeItem(locale, currency.code, cart, cartItemId)
                      val data = cartRenderService.renderCart(updatedCart, currency,locale)
                      val response = Map(
                        ("success"->true),
                        ("data"->data),
                        ("errors"->List())
                      )
                      complete(response)
                    }catch{
                      case e: RemoveCartItemException => {
                        val response = Map(
                          ("success"->false),
                          ("data"->cart),
                          ("errors"->e.getErrors(locale))
                        )
                        complete(response)
                      }
                    }
              }
            }
          }
      }~path("coupons" / Segment){
          couponCode => pathEnd {
            post {
              parameters('currency.?, 'country.?, 'lang ? "_all").as(CouponParameters) { params =>
                println("evaluate coupon parameters")
                val lang:String = if(params.lang=="_all") "fr" else params.lang //FIX with default Lang

                //val locale = Locale.forLanguageTag(lang)
                val country = params.country.getOrElse("FR") //FIXME trouver une autre valeur par défaut ou refuser l'appel
                val locale = new Locale(lang,country)

                val currency = ElasticSearchClient.getCurrency(storeCode,params.currency,lang)
                val cart = cartService.initCart(uuid)

                try{
                  val updatedCart = cartService.addCoupon(storeCode,couponCode,cart,locale,currency.code)
                  val data = cartRenderService.renderCart(updatedCart, currency,locale)
                  val response = Map(
                    ("success"->true),
                    ("data"->data),
                    ("errors"->List())
                  )
                  complete(response)
                }catch{
                  case e: AddCouponToCartException => {
                    val response = Map(
                      ("success"->false),
                      ("data"->cart),
                      ("errors"->e.getErrors(locale))
                    )
                    complete(response)
                  }
                }

                //complete("add coupon")
              }
            } ~
            delete {
              parameters('currency.?, 'country.?, 'lang ? "_all").as(CouponParameters) { params =>
                println("evaluate coupon parameters")
                val lang:String = if(params.lang=="_all") "fr" else params.lang //FIX with default Lang
                //val locale = Locale.forLanguageTag(lang)
                val country = params.country.getOrElse("FR") //FIXME trouver une autre valeur par défaut ou refuser l'appel
                val locale = new Locale(lang,country)


                val currency = ElasticSearchClient.getCurrency(storeCode,params.currency,lang)
                val cart = cartService.initCart(uuid)

                try{
                  val updatedCart = cartService.removeCoupon(storeCode,couponCode,cart,locale,currency.code)
                  val data = cartRenderService.renderCart(updatedCart, currency,locale)
                  val response = Map(
                    ("success"->true),
                    ("data"->data),
                    ("errors"->List())
                  )
                  complete(response)
                }catch{
                  case e: RemoveCouponFromCartException => {
                    val response = Map(
                      ("success"->false),
                      ("data"->cart),
                      ("errors"->e.getErrors(locale))
                    )
                    complete(response)
                  }
                }
                //complete("remove coupon")
              }
            }
          }
      }~pathPrefix("payment"){
        post{
            path("prepare") {
              parameters('currency.?, 'country.?,'state.?, 'lang ? "_all").as(PrepareTransactionParameters) { params =>
                val lang: String = if (params.lang == "_all") "fr" else params.lang //FIX with default Lang


                //val locale = Locale.forLanguageTag(lang)
                //val country = params.country.getOrElse(locale.getCountry)
                val country = params.country.getOrElse("FR") //FIXME trouver une autre valeur par défaut ou refuser l'appel
                val locale = new Locale(lang,country)

                /*
                println(s"locale=${locale}")
                println(s"params.country=${params.country}")
                println(s"locale.getCountry=${locale.getCountry}")
                */

                val currency = ElasticSearchClient.getCurrency(storeCode, params.currency, lang)
                val cart = cartService.initCart(uuid)

                try{
                  val data = cartService.prepareBeforePayment(storeCode, country, params.state, currency.code, cart, currency)

                  val response = Map(
                    ("success"->true),
                    ("data"->data),
                    ("errors"->List())
                  )
                  complete(response)
                }catch{
                  case e: CartException => {
                    val response = Map(
                      ("success"->false),
                      ("data"->cart),
                      ("errors"->e.getErrors(locale))
                    )
                    complete(response)
                  }
                }
                //complete("prepare")
              }
            } ~ path("commit") {
                parameters('transactionUuid).as(CommitTransactionParameters) { params =>
                  val cart = cartService.initCart(uuid)
                  val locale = Locale.getDefault
                    try {
                    val emailingData = cartService.commit(cart, params.transactionUuid)
                    val response = Map(
                      ("success" -> true),
                      ("data" -> emailingData),
                      ("errors" -> List())
                    )
                    complete(response)
                  } catch {
                    case e: CartException => {
                      val response = Map(
                        ("success" -> false),
                        ("data" -> cart),
                        ("errors" -> e.getErrors(locale))
                      )
                      complete(response)
                    }
                  }
                  //complete("commit")
                }
            } ~ path("cancel") {
              parameters('currency.?, 'country.?, 'lang ? "_all").as(CancelTransactionParameters) { params =>
                val lang: String = if (params.lang == "_all") "fr" else params.lang //FIX with default Lang
                val locale = Locale.forLanguageTag(lang)
                val currency = ElasticSearchClient.getCurrency(storeCode, params.currency, lang)
                val cart = cartService.initCart(uuid)
                try {
                  val updatedCart = cartService.cancel(cart)
                  val data = cartRenderService.renderCart(updatedCart, currency, locale)
                  val response = Map(
                    ("success" -> true),
                    ("data" -> data),
                    ("errors" -> List())
                  )
                  complete(response)
                } catch {
                  case e: CartException => {
                    val response = Map(
                      ("success" -> false),
                      ("data" -> cart),
                      ("errors" -> e.getErrors(locale))
                    )
                    complete(response)
                  }
                }
              }
              //complete("cancel")
            }

        }
      }
    }
  }
}
