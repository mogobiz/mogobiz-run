package com.mogobiz.services

import akka.actor.ActorRef
import com.mogobiz.actors.PreferenceActor.{QueryGetPreferenceRequest, QuerySavePreferenceRequest}
import com.mogobiz.model.Prefs
import spray.http.StatusCodes
import com.mogobiz.config.JsonSupport._

import spray.routing.Directives
import scala.concurrent.ExecutionContext
import scala.util.{Try, Failure, Success}


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
      onComplete((actor ? request)) {
        case Success(result) => complete(StatusCodes.OK -> Map("code" -> true))
        case Failure(result) => complete(StatusCodes.OK -> Map("code" -> false))
      }
    }
  }

}
