package com.mogobiz.jobs

import akka.actor.{Actor, ActorSystem, Props}
import com.mogobiz.config.HandlersConfig._
import scala.concurrent.duration._

object CleanCartJob{
  def start(system:ActorSystem): Unit ={
    import system.dispatcher
    val job = system.actorOf(Props[CleanCartJob])
    system.scheduler.schedule(0 second, 60 seconds, job, "")
  }
}

class CleanCartJob extends Actor {

  def receive = {
    case _ => cartHandler.cleanup()
  }
}
