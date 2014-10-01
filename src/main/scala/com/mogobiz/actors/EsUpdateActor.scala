package com.mogobiz.actors

import akka.actor.Actor
import com.mogobiz.actors.EsUpdateActor._
import com.mogobiz.handlers.StockHandler

/**
 * Actor in charge of every updates operations on ES data
 */
object EsUpdateActor {

  case class StockUpdateRequest(storeCode: String, uuid: String,sold: Long)

}

class EsUpdateActor extends Actor {

  val stockHandler = new StockHandler

  def receive = {
    case q: StockUpdateRequest => stockHandler.update(q.storeCode, q.uuid, q.sold)
  }
}
