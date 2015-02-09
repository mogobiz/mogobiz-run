package com.mogobiz.run.services

import akka.actor.ActorRef
import com.mogobiz.run.config.HandlersConfig._
import com.mogobiz.run.exceptions.MogobizException
import com.mogobiz.run.implicits.JsonSupport
import JsonSupport._
import com.mogobiz.run.model.Prefs
import spray.http.StatusCodes
import spray.routing.Directives

import scala.concurrent.ExecutionContext
import scala.util.{Try, Failure, Success}


class PreferenceService(storeCode: String, uuid: String)(implicit executionContext: ExecutionContext) extends Directives with DefaultComplete {

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
    handleCall(preferenceHandler.getPreferences(storeCode, uuid), (prefs: Prefs) => complete(StatusCodes.OK, prefs))
  }

  lazy val savePrefs = post {
    parameters('productsNumber ? 10).as(Prefs) {
      params =>
        Try(preferenceHandler.savePreference(storeCode, uuid, params)) match {
          case Success(result) => complete(StatusCodes.OK -> Map("code" -> true))
          case Failure(result) => complete(StatusCodes.OK -> Map("code" -> false))
        }
    }
  }
}
