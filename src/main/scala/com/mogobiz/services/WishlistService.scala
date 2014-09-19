package com.mogobiz.services

import akka.actor.ActorRef
import spray.routing.Directives

import scala.concurrent.ExecutionContext

/**
 * Created by hayssams on 19/09/14.
 */
class WishlistService(storeCode: String, actor: ActorRef)(implicit executionContext: ExecutionContext) extends Directives {

}
