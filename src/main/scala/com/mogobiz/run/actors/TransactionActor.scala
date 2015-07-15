package com.mogobiz.run.actors

import akka.actor.{Actor, ActorSystem, Props}
import com.mogobiz.pay.common.CartContentMessage
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.slf4j.Logger
import org.slf4j.LoggerFactory
import com.mogobiz.run.config.MogobizHandlers.cartHandler

object TransactionActor {
  def start() {
    val system = ActorSystem("MogobizTransactionSystem", ConfigFactory.parseString("akka.remote.netty.tcp.port=2560")
      .withFallback(ConfigFactory.load()))
    system.actorOf(Props[TransactionActor], name = "TransactionActor")
  }
}

class TransactionActor extends Actor {
  private val logger = Logger(LoggerFactory.getLogger("TransactionActor"))

  def receive = {
    case msg: CartContentMessage =>
      val keys = msg.cartKeys.split("\\|\\|\\|")
      sender ! cartHandler.getCartForPay(keys(0), keys(1), if (!keys(2).isEmpty) Some(keys(2)) else None)
  }
}
