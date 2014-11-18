package com.mogobiz.run.services

import akka.actor.ActorRef
import com.mogobiz.run.implicits.Json4sProtocol
import Json4sProtocol._
import com.mogobiz.run.actors.CountryActor.QueryCountryRequest
import org.json4s._
import spray.http.StatusCodes
import spray.routing.Directives

import scala.concurrent.ExecutionContext
import scala.util.Try

class CountryService(storeCode: String, actor: ActorRef)(implicit executionContext: ExecutionContext) extends Directives with DefaultComplete {

  import akka.pattern.ask
  import akka.util.Timeout

import scala.concurrent.duration._

  implicit val timeout = Timeout(2.seconds)

  val route = {
    pathPrefix("countries") {
      countries
    }
  }

  lazy val countries = pathEnd {
    get {
      parameters('lang ? "_all") {
        lang =>
          onComplete((actor ? QueryCountryRequest(storeCode, lang)).mapTo[Try[JValue]]) { call =>
            handleComplete(call, (json:JValue) => complete(StatusCodes.OK, json))
          }
      }
    }
  }
}
