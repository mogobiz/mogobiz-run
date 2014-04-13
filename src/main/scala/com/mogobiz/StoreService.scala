package com.mogobiz
/*
import com.mogobiz.session.SessionCookieDirectives._
import com.mogobiz.session.Session
*/
import spray.http.{HttpCookie, DateTime}
import scala.util.{Success, Failure}
import akka.actor.Actor
import spray.routing.HttpService
import spray.http.MediaTypes._
import spray.httpx.Json4sSupport
import org.json4s._
import scala.concurrent.ExecutionContext
import org.json4s.native.JsonMethods._
import java.util.UUID

/**
 * Created by Christophe on 17/02/14.
 */

trait StoreService extends HttpService {

  import Json4sProtocol._
  import ExecutionContext.Implicits.global

  val esClient = new ElasticSearchClient

  val storeRoutes = {
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
          productsRoutes(storeCode) ~
          visitedProductsRoute(storeCode)
          //testCookie(storeCode)
      }
    }
  }

  val storeRoutesWithCookie = {
    optionalCookie("mogobiz_uuid") {
      case Some(mogoCookie) => {
        println(s"mogoCookie=${mogoCookie.content}")
        storeRoutes
      }
      case None =>
      {
        val id = UUID.randomUUID.toString
        println(s"new uuid=${id}")
        setCookie(HttpCookie("mogobiz_uuid",content=id)) {
          storeRoutes
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
  def brandsRoutes(storeCode:String) = path("brands") {
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
   * http://localhost:8082/store/mogobiz/product/38?currencyCode=EUR&countryCode=FR&lang=FR
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
  def productsRoutes(storeCode:String) = pathPrefix("products") {
    pathEnd{
      respondWithMediaType(`application/json`) {
        //TODO gestion des erreurs si lang,country,currency présent mais null ou vide
        //FIXME currency=eur error
        parameters('maxItemPerPage.?, 'pageOffset.?, 'xtype.?, 'name.?, 'code.?, 'categoryId.?, 'brandId.?,'path.?, 'tagName.?, 'priceMin.?, 'priceMax.?, 'orderBy.?, 'orderDirection.?,'featured.?, 'lang?"_all", 'currency, 'country).as(ProductRequest) {
          productRequest =>
            onSuccess(esClient.queryProductsByCriteria(storeCode,productRequest)){ products =>
              complete(products)
            }
        }
      }
    } ~ findRoute(storeCode) ~
      productDetailsRoute(storeCode)
  }

  /**
   * Recherche de produit fulltext
   * @param storeCode
   * @return
   */
  def findRoute(storeCode:String) = path("find") {
    respondWithMediaType(`application/json`) {
        parameters('lang?"_all",'currency,'country, 'query).as(FulltextSearchProductParameters) {
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
  def productDetailsRoute(storeCode:String) = pathPrefix(Segment) {
    productId => pathEnd {
      respondWithMediaType(`application/json`) {
        get{
        parameters(
          'historize ? false
          , 'visitorId.?
          , 'currency
          , 'country
          , 'lang?"_all").as(ProductDetailsRequest) {
          pdr => cookie("mogobiz_uuid") { cookie =>
            val uuid = cookie.content
            /*
            com.mogobiz.session.SessionCookieDirectives.session {
              sessionCookie =>
                println("sessionCookie.id="+sessionCookie.id)
                val uuid = sessionCookie.id
                */
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
        }
        }
      }
    } ~ productDatesRoute(storeCode,productId.toLong) ~ productTimesRoute(storeCode,productId.toLong)
  }

  def productDatesRoute(storeCode:String, productId: Long) = path("dates") {
    respondWithMediaType(`application/json`) {
      get{
        parameters('date.?,'startDate.?, 'endDate.?).as(ProductDatesRequest) {
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

  def visitedProductsRoute(storeCode:String) = path("history") {
    respondWithMediaType(`application/json`) {
      get {
        parameters(
          'currencyCode
          , 'countryCode
          , 'lang ? "_all").as(VisitorHistoryRequest) {
          req =>
            cookie("mogobiz_uuid") { cookie =>
              val uuid = cookie.content
            println(s"visitedProductsRoute with mogobiz_uuid=${uuid}")
            /*
            com.mogobiz.session.SessionCookieDirectives.session {
              sessionCookie =>
                println("sessionCookie.id="+sessionCookie.id)
              val uuid = req.sessionId.getOrElse(sessionCookie.id)*/
                onSuccess(esClient.getProductHistory(storeCode,uuid)){ ids =>

                  onSuccess(esClient.getProducts(storeCode,ids,ProductDetailsRequest(false,None,req.currencyCode,req.countryCode,req.lang))){ products =>
                    println("visitedProductsRoute returned results",products)
                    complete(products)
                  }
                }
            }
        }
      }
    }
  }

 }
