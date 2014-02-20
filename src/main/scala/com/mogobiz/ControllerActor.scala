package com.mogobiz

import akka.actor.Actor
import org.json4s.{Formats, DefaultFormats}
import spray.httpx.Json4sSupport

/**
 * Created by dach on 18/02/2014.
 */
class ControllerActor extends Actor with StoreService {

  def actorRefFactory = context

  def receive = runRoute(storeRoutes)

}

object Json4sProtocol extends Json4sSupport {

  implicit def json4sFormats: Formats = DefaultFormats
}