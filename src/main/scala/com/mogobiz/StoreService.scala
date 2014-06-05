package com.mogobiz
/*
import com.mogobiz.session.SessionCookieDirectives._
import com.mogobiz.session.Session
*/
import spray.http.{StatusCodes, HttpCookie, DateTime}
import spray.routing.HttpService
import spray.http.MediaTypes._
import org.json4s._
import scala.concurrent.ExecutionContext
import org.json4s.native.JsonMethods._
import java.util.{Locale, UUID}
import com.mogobiz.cart._
import scala.util.Failure
import scala.Some
import com.mogobiz.vo.CommentPutRequest
import com.mogobiz.vo.MogoError
import scala.util.Success
import com.mogobiz.cart.AddToCartCommand
import com.mogobiz.vo.CommentRequest
import com.mogobiz.vo.CommentGetRequest

/**
 * Created by Christophe on 17/02/14.
 */

trait StoreService extends HttpService {

  import Json4sProtocol._
  import ExecutionContext.Implicits.global

  val esClient = new ElasticSearchClient

  def storeRoutes(uuid:String) = {
    pathPrefix("store" / Segment) {
      storeCode => {
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
    }
  }

  val storeRoutesWithCookie = {
    optionalCookie("mogobiz_uuid") {
      case Some(mogoCookie) => {
        println(s"mogoCookie=${mogoCookie.content}")
        storeRoutes(mogoCookie.content)
      }
      case None =>
      {
        val id = UUID.randomUUID.toString
        println(s"new uuid=${id}")
        setCookie(HttpCookie("mogobiz_uuid",content=id,path=Some("/store/mogobiz"))) {
          storeRoutes(id)
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
  def   brandsRoutes(storeCode:String) = path("brands") {
    respondWithMediaType(`application/json`) {
      get {
        parameters('hidden?false,'lang?"_all").as(BrandRequest) { brandRequest =>

          onSuccess(esClient.queryBrands(storeCode,brandRequest)){ response =>

            complete(response)
          }
        }
      }
    }
  }

  def tagsRoutes(storeCode: String) = path("tags") {
    respondWithMediaType(`application/json`) {
      get{
        //TODO hidden and inactive ???
        parameters('hidden?false,'inactive?false,'lang?"_all").as(TagRequest) { tagRequest =>
          onSuccess(esClient.queryTags(storeCode, tagRequest)){ response =>
            val json = parse(response.entity.asString)
            val subset = json \ "hits" \ "hits" \ "_source"
            complete(subset)
          }
        }
      }
    }
  }

  val langsRoutes =
    path("langs") {
      get {
        respondWithMediaType(`application/json`) {
          complete {
            val langs = List("fr","en","de","it","es")
            langs
          }
        }
      }
    }

  def countriesRoutes(storeCode: String) = path("countries") {
    respondWithMediaType(`application/json`) {
      get{
        parameters('lang?"_all").as(CountryRequest) { countryReq =>
          onSuccess(esClient.queryCountries(storeCode, countryReq.lang)){ response =>
            val json = parse(response.entity.asString)
            val subset = json \ "hits" \ "hits" \ "_source"
            complete(subset)
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
        onSuccess(esClient.queryCurrencies(storeCode, currencyReq.lang)){ json =>
          complete(json)
        }
      }
    }
    }
  }


  def categoriesRoutes(storeCode: String) = path("categories") {
    respondWithMediaType(`application/json`) {
      get{
        parameters('hidden ? false, 'parentId.?, 'lang?"_all").as(CategoryRequest) { categoryReq =>
          onSuccess(esClient.queryCategories(storeCode,categoryReq)){ response =>
            val json = parse(response.entity.asString)
          //TODO renvoyer les fils directs si parentId renseigné
            val subset = json \ "hits" \ "hits" \ "_source"
            complete(subset)
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
          , 'categoryId.?
          , 'brandId.?
          , 'path.?
          , 'tagName.?
          , 'priceMin.?
          , 'priceMax.?
          , 'orderBy.?
          , 'orderDirection.?
          , 'featured.?
          , 'lang?"_all"
          , 'currency.?
          , 'country.?).as(ProductRequest) {

          productRequest =>

            val f = esClient.queryProductsByCriteria(storeCode, productRequest)

            onComplete(f) {
              case Success(products) => complete(products)
              case Failure(t) => complete("error", "error") //TODO change that
            }
        }

      }
    } ~ findRoute(storeCode) ~
      productDetailsRoute(storeCode,uuid)
  }

  /**
   * Recherche de produit fulltext
   * @param storeCode
   * @return
   */
  def findRoute(storeCode:String) = path("find") {
    respondWithMediaType(`application/json`) {
        parameters('lang?"_all",'currency.?,'country.?, 'query, 'highlight ? false).as(FulltextSearchProductParameters) {
          req =>
            onSuccess(esClient.queryProductsByFulltextCriteria(storeCode,req)){ products =>
              complete(products)
            }
        }
    }
  }


  /**
   * {id}?lang=fr&currency=historize=false&visitorId=1
   * @param storeCode
   * @return
   */
  def productDetailsRoute(storeCode:String,uuid:String) = pathPrefix(Segment) {
    productId => pathEnd {
      respondWithMediaType(`application/json`) {
        get{
        parameters(
          'historize ? false
          , 'visitorId.?
          , 'currency.?
          , 'country.?
          , 'lang?"_all").as(ProductDetailsRequest) {
          pdr => /*cookie("mogobiz_uuid") { cookie =>
            val uuid = cookie.content*/
                if(pdr.historize){
                  val f = esClient.addToHistory(storeCode,productId.toLong,uuid)
                  f onComplete {
                    case Success(res) => if(res)println("addToHistory ok")else println("addToHistory failed")
                    case Failure(t) => {
                      println("addToHistory future failure")
                      t.printStackTrace()
                    }
                  }
                }
                onSuccess(esClient.queryProductById(storeCode,productId.toLong, pdr)){ response =>
                  complete(response)
                }
            }
//        }
        }
      }
    } ~ productDatesRoute(storeCode,productId.toLong) ~ productTimesRoute(storeCode,productId.toLong) ~ commentsRoute(storeCode,productId.toLong)
  }

  def productDatesRoute(storeCode:String, productId: Long) = path("dates") {
    respondWithMediaType(`application/json`) {
      get{
        parameters('date.?).as(ProductDatesRequest) {
          pdr =>
              onSuccess(esClient.queryProductDates(storeCode,productId.toLong, pdr)){ response =>
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
            onSuccess(esClient.queryProductTimes(storeCode,productId.toLong, pdr)){ response =>
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
                onComplete(esClient.getProductHistory(storeCode,uuid)){
                  case Success(ids) => {
                    if(ids.isEmpty){
                      complete(List()) //return empty list
                    }else{
                      onSuccess(esClient.getProducts(storeCode,ids,ProductDetailsRequest(false,None,req.currency,req.country,req.lang))){ products =>
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
          onComplete(esClient.savePreferences(store, uuid, prefs)) {
            case Success(result) => complete(Map("code" -> true))
            case Failure(result) => complete(Map("code" -> false))
          }
        }
      } ~
      get {
        onComplete(esClient.getPreferences(store, uuid)) { prefs =>
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
            onComplete(esClient.createComment(storeCode, productId,req)){ //resp =>
              case Success(resp) => complete(resp)
              case Failure(t) => t match {
                case CommentException(code,message) => complete(StatusCodes.BadRequest,(MogoError(code,message)))
                case _ => complete(StatusCodes.InternalServerError,t.getMessage)
              }
            }
          }
        } ~ get{
          parameters('maxItemPerPage.?, 'pageOffset.?).as(CommentGetRequest){ req =>
            onSuccess(esClient.getComments(storeCode,req)){ comments =>
              complete(comments)
            }
          }
        }
      } ~ path(Segment) {
        id => {
          put {
            entity(as[CommentPutRequest]) {
              req =>
                onSuccess(esClient.updateComment(storeCode, productId, id, req.note == 1)) { res =>
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
            val locale = Locale.forLanguageTag(lang)
            val currency = esClient.getCurrency(storeCode,params.currency,lang)
            complete(cartRenderService.render(cart, currency,locale))
          }
        }~delete{
          parameters('currency.?, 'country.?, 'lang ? "_all").as(CartParameters) { params =>
            val cart = cartService.initCart(uuid)

            val lang:String = if(params.lang=="_all") "fr" else params.lang //FIX with default Lang
            val locale = Locale.forLanguageTag(lang)
            val currency = esClient.getCurrency(storeCode,params.currency,lang)
            val updatedCart = cartService.clear(locale,currency.code,cart)
            complete(cartRenderService.render(updatedCart, currency,locale))
          }
        }
      }~path("items"){
        post{
          parameters('currency.?, 'country.?, 'lang ? "_all").as(CartParameters) { params =>
            entity(as[AddToCartCommand]){
              cmd => {
                val cart = cartService.initCart(uuid)

                val lang:String = if(params.lang=="_all") "fr" else params.lang //FIX with default Lang
                val locale = Locale.forLanguageTag(lang)
                val currency = esClient.getCurrency(storeCode,params.currency,lang)
                try{
                  val updatedCart = cartService.addItem(locale, currency.code, cart, cmd.ticketTypeId,cmd.quantity,cmd.dateTime,cmd.registeredCartItems )
                  val data = cartRenderService.render(updatedCart, currency,locale)
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
                      ("errors"->e.errors)
                    )
                    complete(response)
                  }
                }
              }
            }
        }
        }~put{
          parameters('currency.?, 'country.?, 'lang ? "_all").as(CartParameters) { params =>
            entity(as[UpdateCartItemCommand]){
              cmd => {
                val cart = cartService.initCart(uuid)

                val lang:String = if(params.lang=="_all") "fr" else params.lang //FIX with default Lang
                val locale = Locale.forLanguageTag(lang)
                val currency = esClient.getCurrency(storeCode,params.currency,lang)
                try{
                  val updatedCart = cartService.updateItem(locale, currency.code, cart, cmd.cartItemId, cmd.quantity)
                  val data = cartRenderService.render(updatedCart, currency,locale)
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
                      ("errors"->e.errors)
                    )
                    complete(response)
                  }
                }
              }
            }
          }
        }~delete{
          parameters('currency.?, 'country.?, 'lang ? "_all").as(CartParameters) { params =>
            entity(as[RemoveCartItemCommand]){
              cmd => {
                val cart = cartService.initCart(uuid)

                val lang:String = if(params.lang=="_all") "fr" else params.lang //FIX with default Lang
                val locale = Locale.forLanguageTag(lang)
                val currency = esClient.getCurrency(storeCode,params.currency,lang)
                try {
                  val updatedCart = cartService.removeItem(locale, currency.code, cart, cmd.cartItemId)
                  val data = cartRenderService.render(updatedCart, currency,locale)
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
                      ("errors"->e.errors)
                    )
                    complete(response)
                  }
                }
              }
            }
          }
        }
      }~path("coupons" / Segment){
          couponCode => pathEnd {
            parameters('companyId,'currency.?, 'country.?, 'lang ? "_all").as(CouponParameters) { params =>
              println("evaluate coupon parameters")
              val lang:String = if(params.lang=="_all") "fr" else params.lang //FIX with default Lang
              val locale = Locale.forLanguageTag(lang)
              val currency = esClient.getCurrency(storeCode,params.currency,lang)

              post {
                val cart = cartService.initCart(uuid)

                try{
                  val updatedCart = cartService.addCoupon(params.companyId,couponCode,cart,locale,currency.code)
                  val data = cartRenderService.render(updatedCart, currency,locale)
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
                      ("errors"->e.errors)
                    )
                    complete(response)
                  }
                }

                //complete("add coupon")
              } ~ delete {
                val cart = cartService.initCart(uuid)

                try{
                  val updatedCart = cartService.removeCoupon(params.companyId,couponCode,cart,locale,currency.code)
                  val data = cartRenderService.render(updatedCart, currency,locale)
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
                      ("errors"->e.errors)
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
              parameters('companyId,'currency.?, 'country.?,'state.?, 'lang ? "_all").as(PrepareTransactionParameters) { params =>
                val lang: String = if (params.lang == "_all") "fr" else params.lang //FIX with default Lang
                val locale = Locale.forLanguageTag(lang)
                val country = params.country.getOrElse(locale.getCountry)
                val currency = esClient.getCurrency(storeCode, params.currency, lang)

                val cart = cartService.initCart(uuid)

                try{
                  val updatedCart = cartService.prepareBeforePayment(params.companyId, country, params.state, currency.code, cart, currency)
                  val data = cartRenderService.renderTransactionCart(updatedCart, currency,locale)
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
                      ("errors"->e.errors)
                    )
                    complete(response)
                  }
                }
                //complete("prepare")
              }
            } ~ path("commit") {
                parameters('transactionUuid).as(CommitTransactionParameters) { params =>
                  val cart = cartService.initCart(uuid)

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
                        ("errors" -> e.errors)
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
                val currency = esClient.getCurrency(storeCode, params.currency, lang)
                val cart = cartService.initCart(uuid)
                try {
                  val updatedCart = cartService.cancel(locale, currency.code, cart)
                  val data = cartRenderService.render(updatedCart, currency, locale)
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
                      ("errors" -> e.errors)
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
