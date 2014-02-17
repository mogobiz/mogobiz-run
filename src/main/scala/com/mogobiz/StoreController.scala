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

  val productsRoutes = path("products"){
    respondWithMediaType(`application/json`){
      parameters('maxItemPerPage.?,'pageOffset.?,'xtype.?,'name.?,'code.?,'categoryId.?,'brandId.?,'tagName.?,'priceMin.?,'priceMax.?,'creationDateMin.?,'orderBy.?,'orderDirection.?,'lang,'store,'currency,'country).as(ProductRequest){ productRequest =>
        complete {

          //TODO search with ES
          val products = Product("1","Nike Air","",100L)::Product("2","Rebook 5230","",140L)::Product("3","New Balance 1080","",150L)::Product("4","Mizuno Wave Legend","",60L)::Nil
          products
        }
      }
    }
  }

  val featuredProductsRoutes = path("featured-products"){
    respondWithMediaType(`application/json`){
      parameters('maxItemPerPage.?,'pageOffset.?,'xtype.?,'name.?,'code.?,'categoryId.?,'brandId.?,'tagName.?,'priceMin.?,'priceMax.?,'creationDateMin.?,'orderBy.?,'orderDirection.?,'lang,'store,'currency,'country).as(ProductRequest){ productRequest =>
        complete {

          //TODO search with ES
          val products = Product("1","Nike Air","",100L)::Product("2","Rebook 5230","",140L)::Product("3","New Balance 1080","",150L)::Product("4","Mizuno Wave Legend","",60L)::Nil
          products
        }
      }
    }
  }

  val findRoute = path("find") {
    respondWithMediaType(`application/json`) {
      parameters('query,'lang,'store) { (query, lang, storeCode) =>
        complete {

          //TODO search with ES
          val results = List(query,lang,storeCode)
          results
        }
      }
    }
  }


  val allRoutes = storeRoutes ~ brandsRoutes ~ tagsRoutes ~ productsRoutes ~ featuredProductsRoutes ~ findRoute
}
