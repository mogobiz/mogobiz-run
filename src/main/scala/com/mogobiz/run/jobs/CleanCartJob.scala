package com.mogobiz.run.jobs

import akka.actor.{Actor, ActorSystem, Props}
import com.mogobiz.run.config.HandlersConfig
import HandlersConfig._
import scala.concurrent.duration._

object CleanCartJob{
  def start(system:ActorSystem): Unit ={
    import system.dispatcher
    val job = system.actorOf(Props[CleanCartJob])
    system.scheduler.schedule(2 seconds, 60 seconds, job, "")
  }
}

class CleanCartJob extends Actor {

  def receive = {
    case _ => cartHandler.cleanup()
  }
}
