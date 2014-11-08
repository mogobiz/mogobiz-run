package com.mogobiz.run.actors

import akka.actor.Actor
import com.mogobiz.run.config.HandlersConfig
import HandlersConfig._
import PromotionActor._
import com.mogobiz.run.model.Promotion._

/**
 *
 * Created by smanciot on 20/09/14.
 */
object PromotionActor {

  case class QueryPromotionRequest(storeCode: String, params:PromotionRequest)

}

class PromotionActor extends Actor {
  def receive = {
    case q: QueryPromotionRequest =>
      sender ! promotionHandler.queryPromotion(q.storeCode, q.params)
  }
}
