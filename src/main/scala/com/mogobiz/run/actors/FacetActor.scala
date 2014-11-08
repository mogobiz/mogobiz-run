package com.mogobiz.run.actors

import akka.actor.Actor
import com.mogobiz.run.actors.FacetActor.QueryGetFacetRequest
import com.mogobiz.run.config.HandlersConfig
import HandlersConfig._
import com.mogobiz.run.model.FacetRequest

import scala.util.Try

object FacetActor {

  case class QueryGetFacetRequest(storeCode: String, req: FacetRequest)

}

class FacetActor extends Actor {

  def receive = {
    case q: QueryGetFacetRequest =>
      sender ! Try(facetHandler.getProductCriteria(q.storeCode, q.req))
  }
}
