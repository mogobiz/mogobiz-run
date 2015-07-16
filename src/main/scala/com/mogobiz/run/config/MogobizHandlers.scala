/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.config

import com.mogobiz.pay.handlers.shipping.EasyPostHandler
import com.mogobiz.run.handlers._
import com.mogobiz.run.handlers.sql.ForUpdateHandler

object MogobizHandlers {
  val tagHandler = new TagHandler
  val brandHandler = new BrandHandler
  val langHandler = new LangHandler
  val countryHandler = new CountryHandler
  val currencyHandler = new CurrencyHandler
  val categoryHandler = new CategoryHandler
  val productHandler = new ProductHandler
  val skuHandler = new SkuHandler
  val preferenceHandler = new PreferenceHandler
  val cartHandler = new CartHandler
  val wishlistHandler = new WishlistHandler
  val promotionHandler = new PromotionHandler
  val facetHandler = new FacetHandler
  val stockHandler = new StockHandler
  val salesHandler = new SalesHandler

  val resourceHandler = new ResourceHandler
  val taxRateHandler = new TaxRateHandler
  val couponHandler = new CouponHandler
  val forUpdateHandler = new ForUpdateHandler
  val templateHandler = new TemplateHandler
  val backofficeHandler = new BackofficeHandler

  val learningHandler = new LearningHandler
  val validatorHandler = new ValidatorHandler
  val easyPostHander = new EasyPostHandler

}
