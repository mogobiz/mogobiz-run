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

  def receive = runRoute(storeRoutes)

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
}
