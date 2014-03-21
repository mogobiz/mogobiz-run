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
//          featuredProductsRoutes ~
          findRoute ~
          productDetailsRoute(storeCode) ~
          productDatesRoute ~
          productTimesRoute ~
          addToVisitorHistoryRoute ~
          visitorHistoryRoute

      }
    }
  }

  def brandsRoutes(storeCode:String) = path("brands") {
    respondWithMediaType(`application/json`) {
      get {
        parameters('hidden?false,'lang).as(BrandRequest) { brandRequest =>

          onSuccess(esClient.queryBrands(storeCode,brandRequest)){ response =>
            val json = parse(response.entity.asString)
            val subset = json \ "hits" \ "hits" \ "_source"
            complete(subset)
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

  /* avant
  def currenciesRoutes(storeCode: String) = path("currencies") {
    respondWithMediaType(`application/json`) {
      get{
        parameters('lang).as(CurrencyRequest) { currencyReq =>
          onSuccess(esClient.queryCurrencies(storeCode, currencyReq.lang)){ response =>
            val json = parse(response.entity.asString)
            val subset = json \ "hits" \ "hits" \ "_source"
            complete(subset)
          }
        }
      }
    }
  }*/
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
   * @param storeCode
   * @return
   */
  def productsRoutes(storeCode:String) = path("products") {
    respondWithMediaType(`application/json`) {
      parameters('maxItemPerPage.?, 'pageOffset.?, 'xtype.?, 'name.?, 'code.?, 'categoryId.?, 'brandId.?,'path.?, 'tagName.?, 'priceMin.?, 'priceMax.?, 'orderBy.?, 'orderDirection.?, 'lang, 'currency, 'country).as(ProductRequest) {
        productRequest =>
          onSuccess(esClient.queryProductsByCriteria(storeCode,productRequest)){ products =>
            complete(products)
          }
      }
    }
  }

  /*
  val featuredProductsRoutes = path("featuredProducts") {
    respondWithMediaType(`application/json`) {
      parameters('maxItemPerPage.?, 'pageOffset.?, 'xtype.?, 'name.?, 'code.?, 'categoryId.?, 'brandId.?, 'tagName.?, 'priceMin.?, 'priceMax.?, 'creationDateMin.?, 'orderBy.?, 'orderDirection.?, 'lang, 'store, 'currency, 'country).as(ProductRequest) {
        productRequest =>
          complete {

            //TODO search with ES
            val products = Product("1", "Nike Air", "", 100L) :: Product("2", "Rebook 5230", "", 140L) :: Product("3", "New Balance 1080", "", 150L) :: Product("4", "Mizuno Wave Legend", "", 60L) :: Nil
            products
          }
      }
    }
  }
  */
  val findRoute/*(storeCode:String)*/ = path("find") {
    respondWithMediaType(`application/json`) {
      parameters('query, 'lang, 'store) {
        (query, lang, storeCode) =>
          complete {

            //TODO search with ES
            val results = List(query, lang, storeCode)
            results
          }
      }
    }
  }




  def productDetailsRoute(storeCode:String) = path("product"/Segment) { productId =>
    respondWithMediaType(`application/json`) {
      parameters(
        'historize ? false
//        , 'productId
        , 'visitorId.?
//        , 'storeCode
        , 'currencyCode
        , 'countryCode
        , 'lang).as(ProductDetailsRequest) {
        pdr =>

          onSuccess(esClient.queryProductById(storeCode,productId.toLong, pdr)){ response =>
            val json = parse(response.entity.asString)
            println(pdr)
            //TODO calcul prix


            val lang = pdr.lang
            val currency = pdr.currencyCode
            esClient.getCurrencies(storeCode,lang) onSuccess {
              case currencies => {

                val rate = for{
                  cur <- currencies
                  if(cur.code==currency)
                } yield cur
                println(rate)

              }
            }

            val subset = json \ "_source"
            complete(subset)
          }
      }
    }
  }
/*
  // formatting price
  float taxRate = taxRateService.findTaxRateByProduct(product, locale?.country)
  long endPrice = taxRateService.calculateEndPrix(product.price, taxRate)
  Map price = [:]
  price["price"] = rateService.format(product.price, currencyCode, locale);
  price["taxRate"] = taxRate
  price["endPrice"] = rateService.format(endPrice, currencyCode, locale);
  */

  def convertPrice (value:Double, rate:Double) : Double  = value * rate


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


  val productDatesRoute = path("productDates") {
    respondWithMediaType(`application/json`) {
      parameters(
        'productId
        , 'startDate
        , 'endDate
        , 'storeCode
        , 'lang).as(ProductDatesRequest) {
        pdr =>
          complete {
            //TODO search with ES
            val dates = DateTime.fromIsoDateTimeString("2014-04-18T11:23:00Z") :: DateTime.fromIsoDateTimeString("2014-04-30T11:23:00Z") :: Nil
            dates
          }
      }
    }
  }

  val productTimesRoute = path("productTimes") {
    respondWithMediaType(`application/json`) {
      parameters(
        'productId
        , 'date
        , 'storeCode
        , 'lang).as(ProductTimesRequest) {
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
