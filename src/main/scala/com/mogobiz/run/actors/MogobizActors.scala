package com.mogobiz.run.actors

import akka.actor.Props
import akka.routing.FromConfig
import com.mogobiz.system.MogobizSystem

trait MogobizActors {
  this: MogobizSystem =>
  val tagActor = system.actorOf(Props[TagActor].withRouter(FromConfig()), "tag")
  val brandActor = system.actorOf(Props[BrandActor].withRouter(FromConfig()), "brand")
  val langActor = system.actorOf(Props[LangActor].withRouter(FromConfig()), "lang")
  val countriesActor = system.actorOf(Props[CountryActor].withRouter(FromConfig()), "countries")
  val currencyActor = system.actorOf(Props[CurrencyActor].withRouter(FromConfig()), "currency")
  val categoryActor = system.actorOf(Props[CategoryActor].withRouter(FromConfig()), "category")
  val productActor = system.actorOf(Props[ProductActor].withRouter(FromConfig()), "product")
  val preferenceActor = system.actorOf(Props[PreferenceActor].withRouter(FromConfig()), "preference")
  val cartActor = system.actorOf(Props[CartActor].withRouter(FromConfig()), "cart")
  val promotionActor = system.actorOf(Props[PromotionActor].withRouter(FromConfig()), "promotion")
  val wishlistActor = system.actorOf(Props[WishlistActor].withRouter(FromConfig()), "wishlist")
  val facetActor = system.actorOf(Props[FacetActor].withRouter(FromConfig()), "facet")
}

