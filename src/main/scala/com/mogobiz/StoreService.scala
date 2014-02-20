package com.mogobiz

import spray.routing.HttpService
import spray.http.MediaTypes._
import spray.httpx.Json4sSupport
import org.json4s._
import spray.http.DateTime

/**
 * Created by Christophe on 17/02/14.
 */

trait StoreService extends HttpService {

  import Json4sProtocol._

  val langRoutes =
    path("langs") {
      get {
        respondWithMediaType(`application/json`) {
          complete {
            val langs = List("fr", "en", "de", "it", "es")
            langs
          }
        }
      }
    }

  val countriesRoutes = path("countries") {
    respondWithMediaType(`application/json`) {
      parameters('store, 'lang).as(CountryRequest) {
        cr =>
          complete {
            val countries = Country(1, "FR", "France", None) :: Country(2, "EN", "Royaumes Unis", None) :: Nil
            countries
          }
      }
    }
  }


  val currenciesRoutes = path("currencies") {
    respondWithMediaType(`application/json`) {
      parameters('store).as(CurrencyRequest) {
        cr =>
          complete {
            val currencies = Currency(1, "EUR") :: Currency(2, "USD") :: Nil
            currencies
          }
      }
    }
  }


  val categoriesRoutes = path("categories") {
    respondWithMediaType(`application/json`) {
      parameters('hidden ? false, 'parentId.?, 'lang, 'store).as(CategoryRequest) {
        cr =>
          complete {
            val categories = Category(1, "CNM", "Cinéma", Nil) :: Category(2, "HBG", "Habillage", Nil) :: Nil
            categories
          }
      }
    }
  }


  val brandsRoutes = path("brands") {
    respondWithMediaType(`application/json`) {
      parameters('hidden ? false, 'category.?, 'inactive ? false).as(BrandRequest) {
        brandRequest =>
          complete {
            //          println("hidden="+brandRequest.hidden+" categoryOption="+brandRequest.category+" inactive="+brandRequest.inactive)

            //TODO search with ES
            val brands = Brand(1, "nike", Nil) :: Brand(2, "rebook", Nil) :: Brand(3, "addidas", Nil) :: Nil
            brands
          }
      }
    }
  }

  val tagsRoutes = path("tags") {
    respondWithMediaType(`application/json`) {
      parameters('hidden ? false, 'category, 'inactive ? false, 'lang, 'store).as(TagRequest) {
        tagRequest =>
          complete {

            //TODO search with ES
            val tags = Tag(1, "basket", Nil) :: Tag(2, "chaussure", Nil) :: Tag(3, "vetement", Nil) :: Nil
            tags
          }
      }
    }
  }

  val productsRoutes = path("products") {
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

  val findRoute = path("find") {
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


  val productDetailsRoute = path("productDetails") {
    respondWithMediaType(`application/json`) {
      parameters(
        'addToHistory ? false
        , 'productId
        , 'visitorId
        , 'storeCode
        , 'currencyCode
        , 'countryCode
        , 'lang).as(ProductDetailsRequest) {
        pdr =>
          complete {
            //TODO search with ES
            val product = Product("1", "Nike Air", "", 100L)
            product
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

  val storeRoutes =
    countriesRoutes ~
      currenciesRoutes ~
      categoriesRoutes ~
      langRoutes ~
      brandsRoutes ~
      tagsRoutes ~
      productsRoutes ~
      featuredProductsRoutes ~
      findRoute ~
      productDetailsRoute ~
      addToVisitorHistoryRoute ~
      visitorHistoryRoute ~
      productDatesRoute ~
      productTimesRoute
 }
