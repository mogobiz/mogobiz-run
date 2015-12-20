package com.mogobiz.run.config

import com.mogobiz.pay.handlers.shipping.EasyPostHandler
import com.mogobiz.run.handlers._
import com.mogobiz.run.handlers.sql.ForUpdateHandler

trait MogobizCake {

  def tagHandler: TagHandler
  def brandHandler: BrandHandler
  def langHandler: LangHandler
  def countryHandler: CountryHandler
  def currencyHandler: CurrencyHandler
  def categoryHandler: CategoryHandler
  def productHandler: ProductHandler
  def skuHandler: SkuHandler
  def preferenceHandler: PreferenceHandler
  def cartHandler: CartHandler
  def wishlistHandler: WishlistHandler
  def promotionHandler: PromotionHandler
  def facetHandler: FacetHandler
  def stockHandler: StockHandler
  def salesHandler: SalesHandler

  def resourceHandler: ResourceHandler
  def taxRateHandler: TaxRateHandler
  def couponHandler: CouponHandler
  def forUpdateHandler: ForUpdateHandler
  def templateHandler: TemplateHandler
  def backofficeHandler: BackofficeHandler

  def learningHandler: LearningHandler
  def validatorHandler: ValidatorHandler
  def easyPostHander: EasyPostHandler

  def logoHandler: LogoHandler

}
