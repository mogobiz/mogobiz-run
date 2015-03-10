package com.mogobiz.run.jobs

import akka.actor.{Actor, ActorSystem, Props}
import com.mogobiz.run.config.{Settings, HandlersConfig}
import HandlersConfig._
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
    case _ => Settings.cart.cleanJob.storeCodeList.map {storeCode: String =>  Try(cartHandler.cleanup(storeCode)) }
  }
}
