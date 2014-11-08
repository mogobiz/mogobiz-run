package com.mogobiz.run.services

import akka.actor.ActorRef
import com.mogobiz.run.actors.PreferenceActor.{QueryGetPreferenceRequest, QuerySavePreferenceRequest}
import com.mogobiz.run.implicits.JsonSupport
import JsonSupport._
import com.mogobiz.run.model.Prefs
import spray.http.StatusCodes
import spray.routing.Directives

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}


class PreferenceService(storeCode: String, uuid: String, actor: ActorRef)(implicit executionContext: ExecutionContext) extends Directives {

  import akka.pattern.ask
  import akka.util.Timeout

import scala.concurrent.duration._

  implicit val timeout = Timeout(2.seconds)

  val route = {

    pathPrefix("prefs") {
      getPrefs ~
        savePrefs
    }
  }

  lazy val getPrefs = get {
    val request = QueryGetPreferenceRequest(storeCode, uuid)
    complete {
      (actor ? request).mapTo[Prefs] map { prefs =>
        prefs
      }
    }
  }


  lazy val savePrefs = post {
    parameters('productsNumber ? 10).as(Prefs) { params =>
      val request = QuerySavePreferenceRequest(storeCode, uuid, params)
      onComplete(actor ? request) {
        case Success(result) => complete(StatusCodes.OK -> Map("code" -> true))
        case Failure(result) => complete(StatusCodes.OK -> Map("code" -> false))
      }
    }
  }

}
