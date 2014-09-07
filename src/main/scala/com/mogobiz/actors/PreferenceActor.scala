package com.mogobiz.actors

import akka.actor.Actor
import com.mogobiz.config.HandlersConfig._
import com.mogobiz.actors.PreferenceActor.{QueryGetPreferenceRequest, QuerySavePreferenceRequest}
import com.mogobiz.model.Prefs

object PreferenceActor {

  case class QuerySavePreferenceRequest(storeCode: String, uuid: String, params: Prefs)

  case class QueryGetPreferenceRequest(storeCode: String, uuid: String)

}

class PreferenceActor extends Actor {
  def receive = {
    case q: QuerySavePreferenceRequest => {
      sender ! preferenceHandler.savePreference(q.storeCode, q.uuid, q.params)
    }

      case q: QueryGetPreferenceRequest => {
      sender ! preferenceHandler.getPreferences(q.storeCode, q.uuid)
    }



  }
}
