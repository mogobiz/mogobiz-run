package com.mogobiz.actors

import akka.actor.Actor
import com.mogobiz.actors.ProductActor.QueryProductRequest
import com.mogobiz.config.HandlersConfig._
import com.mogobiz.ProductRequest

object ProductActor {

  case class QueryProductRequest(storeCode: String, productRequest: ProductRequest)


}

class ProductActor extends Actor {
  def receive = {
    case q: QueryProductRequest => {
      sender ! productHandler.queryProductsByCriteria(q.storeCode, q.productRequest)
    }
  }
}
