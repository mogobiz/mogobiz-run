package com.mogobiz.config

import com.mogobiz.handlers._

object HandlersConfig {
  val tagHandler = new TagHandler
  val brandHandler = new BrandHandler
  val langHandler = new LangHandler
  val countryHandler = new CountryHandler
  val currencyHandler = new CurrencyHandler
  val categoryHandler = new CategoryHandler
  val productHandler = new ProductHandler
  val preferenceHandler = new PreferenceHandler
  val cartHandler = new CartHandler
  val wishlistHandler = new WishlistHandler
  val promotionHandler = new PromotionHandler
  val facetHandler = new FacetHandler
}
