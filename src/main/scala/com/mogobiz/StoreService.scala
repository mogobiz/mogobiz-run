package com.mogobiz

import spray.http.DateTime
import scala.util.{Success, Failure}
import akka.actor.Actor
import spray.routing.HttpService
import spray.http.MediaTypes._
import spray.httpx.Json4sSupport
import org.json4s._
import scala.concurrent.ExecutionContext
import org.json4s.native.JsonMethods._
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
          addToVisitorHistoryRoute ~
          visitorHistoryRoute

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
        parameters('hidden?false,'lang).as(BrandRequest) { brandRequest =>

          onSuccess(esClient.queryBrands(storeCode,brandRequest)){ response =>

            complete(response)
          }

/*TODO
            esClient.queryBrands(storeCode, brandRequest) onSuccess {
              case response =>           complete{
                val json = parse(response.entity.asString)
                val subset = json \ "hits" \ "hits" \ "_source"

            }
          }*/
        }
      }
    }
  }

  def tagsRoutes(storeCode: String) = path("tags") {
    respondWithMediaType(`application/json`) {
      get{
        //TODO hidden and inactive ???
        parameters('hidden?false,'inactive?false,'lang).as(TagRequest) { tagRequest =>
          onSuccess(esClient.queryTags(storeCode, tagRequest)){ response =>
            val json = parse(response.entity.asString)
            val subset = json \ "hits" \ "hits" \ "_source"
            complete(subset)
          }

          /*
          complete {
            val tags = Tag(1, "basket", Nil)::Tag(2, "chaussure",Nil)::Tag(3,"vetement",Nil)::Nil
            tags
          }
          */
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
        parameters('lang).as(CountryRequest) { countryReq =>
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
        parameters('lang).as(CurrencyRequest) { currencyReq =>
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
        parameters('hidden ? false, 'parentId.?, 'lang).as(CategoryRequest) { categoryReq =>
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
        parameters('maxItemPerPage.?, 'pageOffset.?, 'xtype.?, 'name.?, 'code.?, 'categoryId.?, 'brandId.?,'path.?, 'tagName.?, 'priceMin.?, 'priceMax.?, 'orderBy.?, 'orderDirection.?,'featured.?, 'lang, 'currency, 'country).as(ProductRequest) {
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
        parameters('lang,'currency,'country, 'query).as(FulltextSearchProductParameters) {
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
          , 'lang).as(ProductDetailsRequest) {
          pdr =>

            onSuccess(esClient.queryProductById(storeCode,productId.toLong, pdr)){ response =>
              complete(response)
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
            complete {
              onSuccess(esClient.queryProductDates(storeCode,productId.toLong, pdr)){ response =>
                complete(response)
                //val dates = DateTime.fromIsoDateTimeString("2014-04-18T11:23:00Z") :: DateTime.fromIsoDateTimeString("2014-04-30T11:23:00Z") :: Nil
              }
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
            complete {
              //TODO search with ES
              val dates = DateTime.fromIsoDateTimeString("2014-04-18T11:00:00Z") :: DateTime.fromIsoDateTimeString("2014-04-18T23:00:00Z") :: Nil
              dates
            }
        }
      }
    }
  }


  val addToVisitorHistoryRoute = path("addToVisitorHistory") {
    respondWithMediaType(`application/json`) {
      parameters(
        'productId
        , 'visitorId
        , 'storeCode
        , 'currencyCode
        , 'countryCode
        , 'lang).as(AddToVisitorHistoryRequest) {
        avhr =>
          complete {
            //TODO search with ES
            val products = Product("1", "Nike Air", "", 100L) :: Product("2", "Rebook 5230", "", 140L) :: Product("3", "New Balance 1080", "", 150L) :: Product("4", "Mizuno Wave Legend", "", 60L) :: Nil
            products
          }
      }
    }
  }


  val visitorHistoryRoute = path("visitorHistory") {
    respondWithMediaType(`application/json`) {
      parameters(
        'visitorId
        , 'storeCode
        , 'currencyCode
        , 'countryCode
        , 'lang).as(VisitorHistoryRequest) {
        avhr =>
          complete {
            //TODO search with ES
            val products = Product("1", "Nike Air", "", 100L) :: Product("2", "Rebook 5230", "", 140L) :: Product("3", "New Balance 1080", "", 150L) :: Product("4", "Mizuno Wave Legend", "", 60L) :: Nil
            products
          }
      }
    }
  }
  //  def convertPrice (value:Double, rate:Double) : Double  = value * rate

 }
