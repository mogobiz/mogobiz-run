package com.mogobiz

import akka.actor.Actor
import spray.routing.HttpService
import spray.http.MediaTypes._
import spray.httpx.Json4sSupport
import org.json4s._

/**
 * Created by Christophe on 17/02/14.
 */
class StoreControllerActor extends Actor with StoreController  {

  def actorRefFactory = context

  def receive = runRoute(allRoutes)

}

object Json4sProtocol extends Json4sSupport {

  implicit def json4sFormats: Formats = DefaultFormats
}

trait StoreController extends HttpService {

  import Json4sProtocol._

  val storeRoutes =
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

  val countriesRoutes = path("countries") {
    respondWithMediaType(`application/json`) {
      parameters('store, 'lang).as(CountryRequest) { cr =>
        complete {
          val countries = Country(1,"FR","France",None)::Country(2,"EN","Royaumes Unis",None)::Nil
          countries
        }
      }
    }
  }


  val currenciesRoutes = path("currencies") {
    respondWithMediaType(`application/json`) {
      parameters('store).as(CurrencyRequest) { cr =>
        complete {
          val  currencies = Currency(1,"EUR")::Currency(2,"USD")::Nil
          currencies
        }
      }
    }
  }


  val categoriesRoutes = path("categories") {
    respondWithMediaType(`application/json`) {
      parameters('hidden?false,'parentId.?,'lang,'store).as(CategoryRequest) { cr =>
        complete {
          val  categories = Category(1,"CNM","Cinéma",Nil)::Category(2,"HBG","Habillage",Nil)::Nil
          categories
        }
      }
    }
  }


  val brandsRoutes = path("brands") {
    respondWithMediaType(`application/json`) {
        parameters('hidden?false,'category.?,'inactive?false).as(BrandRequest) { brandRequest =>
        complete {
//          println("hidden="+brandRequest.hidden+" categoryOption="+brandRequest.category+" inactive="+brandRequest.inactive)

          //TODO search with ES
          val brands = Brand(1,"nike",Nil)::Brand(2,"rebook",Nil)::Brand(3,"addidas",Nil)::Nil
          brands
        }
      }
     }
  }

  val tagsRoutes = path("tags") {
    respondWithMediaType(`application/json`) {
      parameters('hidden?false,'category,'inactive?false,'lang,'store).as(TagRequest) { tagRequest =>
        complete {

          //TODO search with ES
          val tags = Tag(1, "basket", Nil)::Tag(2, "chaussure",Nil)::Tag(3,"vetement",Nil)::Nil
          tags
        }
      }
    }
  }



  val allRoutes = storeRoutes ~ brandsRoutes ~ tagsRoutes
  val allRoutes = storeRoutes ~ brandsRoutes ~ countriesRoutes ~ currenciesRoutes ~ categoriesRoutes
}
