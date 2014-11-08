package com.mogobiz.run.actors

import akka.actor.Props
import com.mogobiz.system.MogobizSystem


/**
 * This trait contains the actors that make up our application; it can be mixed in with
 * ``BootedCore`` for running code or ``TestKit`` for unit and integration tests.
 */
trait MogobizActors {
  this: MogobizSystem =>

  val tagActor = system.actorOf(Props[TagActor])
  val brandActor = system.actorOf(Props[BrandActor])
  val langActor = system.actorOf(Props[LangActor])
  val countriesActor = system.actorOf(Props[CountryActor])
  val currencyActor = system.actorOf(Props[CurrencyActor])
  val categoryActor = system.actorOf(Props[CategoryActor])
  val productActor = system.actorOf(Props[ProductActor])
  val preferenceActor = system.actorOf(Props[PreferenceActor])
  val cartActor = system.actorOf(Props[CartActor])
  val promotionActor = system.actorOf(Props[PromotionActor])
  val wishlistActor =system.actorOf(Props[WishlistActor])
  val facetActor = system.actorOf(Props[FacetActor])
}

