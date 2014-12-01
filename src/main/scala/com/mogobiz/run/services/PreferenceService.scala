package com.mogobiz.run.services

import akka.actor.ActorRef
import com.mogobiz.run.actors.PreferenceActor.{QueryGetPreferenceRequest, QuerySavePreferenceRequest}
import com.mogobiz.run.implicits.JsonSupport
import JsonSupport._
import com.mogobiz.run.model.Prefs
import spray.http.StatusCodes
import spray.routing.Directives

import scala.concurrent.ExecutionContext
import scala.util.{Try, Failure, Success}


class PreferenceService(storeCode: String, uuid: String, actor: ActorRef)(implicit executionContext: ExecutionContext) extends Directives with DefaultComplete {

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
    onComplete((actor ? QueryGetPreferenceRequest(storeCode, uuid)).mapTo[Try[Prefs]]){ call =>
      handleComplete(call, (prefs:Prefs) => complete(StatusCodes.OK,prefs))
    }
  }

  lazy val savePrefs = post {
    parameters('productsNumber ? 10).as(Prefs) { params =>
      onComplete(actor ? QuerySavePreferenceRequest(storeCode, uuid, params)) {
        case Success(result) => complete(StatusCodes.OK -> Map("code" -> true))
        case Failure(result) => complete(StatusCodes.OK -> Map("code" -> false))
      }
    }
  }
}
