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


  //        parameters('hidden.as[Boolean],'category.as[Int],'inactive.as[Boolean]).as(BrandRequest){
  //          brandReq => {


  val brandsRoutes = path("brands") {
    respondWithMediaType(`application/json`) {
//      parameters('hidden?false,'category.?,'inactive?false) { (hidden,categoryOption,inactive) =>
        parameters('hidden?false,'category.?,'inactive?false).as(BrandRequest) { brandRequest =>
        complete {
//          println("hidden="+hidden+" categoryOption="+categoryOption+"inactive="+inactive)
          println("hidden="+brandRequest.hidden+" categoryOption="+brandRequest.category+" inactive="+brandRequest.inactive)

          val brands = Brand(1,"nike",Nil)::Brand(2,"rebook",Nil)::Brand(3,"addidas",Nil)::Nil
          brands
        }
      }
     }
  }

  val allRoutes = storeRoutes ~ brandsRoutes
}
