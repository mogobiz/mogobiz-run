package com.mogobiz.actors

import akka.actor.{ActorSystem, Props}
import com.mogobiz.mail.EmailService
import com.mogobiz.jobs._

/**
  * Core is type containing the ``system: ActorSystem`` member. This enables us to use it in our
  * apps as well as in our tests.
  */
trait MogobizSystem {
   implicit def system: ActorSystem
 }

/**
 * This trait implements ``System`` by starting the required ``ActorSystem`` and registering the
 * termination handler to stop the system when the JVM exits.
 */
trait BootedMogobizSystem extends MogobizSystem {
  /**
   * Construct the ActorSystem we will use in our application
   */
  implicit lazy val system = ActorSystem("mogobiz")

  /**
   * Ensure that the constructed ActorSystem is shut down when the JVM shuts down
   */
  sys.addShutdownHook(system.shutdown())
}

/**
 * This trait contains the actors that make up our application; it can be mixed in with
 * ``BootedCore`` for running code or ``TestKit`` for unit and integration tests.
 */
trait MogobizActors {
  this: MogobizSystem =>

  val tagActor = system.actorOf(Props[TagActor])
  val brandActor = system.actorOf(Props[BrandActor])
  val langActor = system.actorOf(Props[LangActor])
  val countryActor = system.actorOf(Props[CountryActor])
  val currencyActor = system.actorOf(Props[CurrencyActor])
  val categoryActor = system.actorOf(Props[CategoryActor])
  val productActor = system.actorOf(Props[ProductActor])
  val preferenceActor = system.actorOf(Props[PreferenceActor])
  val cartActor = system.actorOf(Props[CartActor])
  val promotionActor = system.actorOf(Props[PromotionActor])
  val wishlistActor =system.actorOf(Props[WishlistActor])

  //init the email service with the system Actor
  EmailService(system,"emailService")

  //init jobs
  CleanCartJob.start(system)
}

