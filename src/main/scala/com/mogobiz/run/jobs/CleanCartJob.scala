package com.mogobiz.run.jobs

import akka.actor.{Actor, ActorSystem, Props}
import com.mogobiz.es.EsClient
import com.mogobiz.run.config.{Settings, MogobizHandlers}
import MogobizHandlers._
import scala.concurrent.duration._
import scala.util.Try

object CleanCartJob{
  def start(system:ActorSystem): Unit ={
    import system.dispatcher
    system.scheduler.schedule(
      initialDelay = Settings.cart.cleanJob.delay seconds,
      interval     = Settings.cart.cleanJob.interval seconds,
      receiver     = system.actorOf(Props[CleanCartJob]),
      message      = ""
    )

  }
}

class CleanCartJob extends Actor {

  def receive = {
    case _ => EsClient.getIndexByAlias("mogobiz_carts").map {storeCode: String =>  Try(cartHandler.cleanup(storeCode)) }
  }
}
