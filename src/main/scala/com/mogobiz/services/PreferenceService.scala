package com.mogobiz.services

import akka.actor.ActorRef
import com.mogobiz.Prefs
import com.mogobiz.actors.PreferenceActor.{QueryGetPreferenceRequest, QuerySavePreferenceRequest}
import spray.http.StatusCodes
import spray.httpx.marshalling.ToResponseMarshallable
import spray.httpx.Json4sJacksonSupport

import spray.routing.Directives
import org.json4s._
import scala.concurrent.ExecutionContext
import scala.util.{Try, Failure, Success}


class PreferenceService(storeCode: String, uuid: String, actor: ActorRef)(implicit executionContext: ExecutionContext) extends Directives {

  import akka.pattern.ask
  import akka.util.Timeout

import scala.concurrent.duration._
  implicit val timeout = Timeout(2.seconds)
  implicit val json4sJacksonFormats = DefaultFormats

  val route = {

    pathPrefix("prefs") {
      getPrefs ~
        savePrefs
    }
  }

  lazy val getPrefs = get {
    val request = QueryGetPreferenceRequest(storeCode, uuid)
    complete {
      (actor ? request).mapTo[Prefs]
    }
  }


  lazy val savePrefs = post {
    parameters('productsNumber ? 10).as(Prefs) { params =>
      val request = QuerySavePreferenceRequest(storeCode, uuid, params)
      onComplete((actor ? request)) {
        case Success(result) => complete(Map("code" -> true))
        case Failure(result) => complete(Map("code" -> false))
      }
    }
  }

}
