package com.mogobiz

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
class StoreControllerActor extends Actor with StoreController  {

  def actorRefFactory = context

  def receive = runRoute(allRoutes)

}

object Json4sProtocol extends Json4sSupport {

  implicit def json4sFormats: Formats = DefaultFormats
}

trait StoreController extends HttpService {

  import Json4sProtocol._
  import ExecutionContext.Implicits.global


//  import org.json4s._
//  import org.json4s.native.JsonMethods._

  private val esClient = new ESClient()

  val storeRoutes = {
    pathPrefix("store" / Segment){ storeCode => {
        pathEnd{
          complete("the store code is "+storeCode)
        }~langsRoutes ~ brandsRoutes(storeCode) ~ tagsRoutes(storeCode)

    }
    }
  }

  def brandsRoutes(storeCode:String) = path("brands") {
    respondWithMediaType(`application/json`) {
      get {
        parameters('hidden?false,'category.?,'inactive?false).as(BrandRequest) { brandRequest =>
          //TODO manque la lang
          onSuccess(esClient.queryBrands(storeCode)){ response =>
            val json = parse(response.entity.asString)
            val subset = json \ "hits" \ "hits" \ "_source"
            complete(subset)
          }
        }
      }
    }
  }

  def tagsRoutes(storeCode: String) = path("tags") {
    respondWithMediaType(`application/json`) {
      get{
      parameters('hidden?false,'category,'inactive?false,'lang).as(TagRequest) { tagRequest =>
        onSuccess(esClient.queryTags(storeCode)){ response =>
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

  val testES = pathPrefix("elastic"){
    pathEnd {
      get {
        onSuccess(new ESClient().queryRoot()){ response =>
          complete(response) //.entity.asString
        }
      }
    } ~
    path("brands"){
      get {
        onSuccess(new ESClient().queryBrands("mogobiz_v12")){ response =>
          val res = response.entity.asString;
          println(res)

          val json = parse(res)
          val subset = json \ "hits" \ "hits" \ "_source"
          complete(subset)
        }
      }
    }

    /*
    complete{
      println("elastic route")
      new ESClient().execute()
    }*/
  }

  val allRoutes = testES ~ storeRoutes ~ langsRoutes ~countriesRoutes ~ currenciesRoutes ~ categoriesRoutes ~ productsRoutes ~ featuredProductsRoutes ~ findRoute
}
