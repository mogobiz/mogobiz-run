package com.mogobiz

import spray.routing.HttpService
import spray.http.MediaTypes._
import spray.httpx.Json4sSupport
import org.json4s._
import spray.http.DateTime

/**
 * Created by Christophe on 17/02/14.
 */




trait CartService extends HttpService {

  import Json4sProtocol._



  val cartRoutes = ???
 }
