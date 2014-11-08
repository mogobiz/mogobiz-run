package com.mogobiz.run.actors

import akka.actor.Actor
import com.mogobiz.run.actors.PreferenceActor.{QueryGetPreferenceRequest, QuerySavePreferenceRequest}
import com.mogobiz.run.config.HandlersConfig
import HandlersConfig._
import com.mogobiz.run.model.Prefs

object PreferenceActor {

  case class QuerySavePreferenceRequest(storeCode: String, uuid: String, params: Prefs)

  case class QueryGetPreferenceRequest(storeCode: String, uuid: String)

}

class PreferenceActor extends Actor {
  def receive = {
    case q: QuerySavePreferenceRequest =>
      sender ! preferenceHandler.savePreference(q.storeCode, q.uuid, q.params)

    case q: QueryGetPreferenceRequest =>
      sender ! preferenceHandler.getPreferences(q.storeCode, q.uuid)


  }
}
