package com.mogobiz.actors

import akka.actor.Actor
import com.mogobiz.actors.FacetActor.QueryGetFacetRequest
import com.mogobiz.config.HandlersConfig._

import scala.util.Try

object FacetActor {

  case class QueryGetFacetRequest(storeCode: String, priceInterval: Long)

}

class FacetActor extends Actor {

  def receive = {
    case q: QueryGetFacetRequest =>
      sender ! Try(facetHandler.getProductCriteria(q.storeCode, q.priceInterval))
  }
}
