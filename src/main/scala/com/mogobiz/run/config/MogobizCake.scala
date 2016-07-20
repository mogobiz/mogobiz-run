package com.mogobiz.run.config

import com.mogobiz.pay.handlers.AccountHandler
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

  def backofficeHandler: BackofficeHandler

  def learningHandler: LearningHandler

  def validatorHandler: ValidatorHandler

  def easyPostHandler: EasyPostHandler

  def logoHandler: LogoHandler

  def miraklHandler: MiraklHandler

}

class DefaultMogobizCake extends MogobizCake {
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
  val backofficeHandler = new BackofficeHandler

  val learningHandler = new LearningHandler
  val validatorHandler = new ValidatorHandler
  val easyPostHandler = new EasyPostHandler

  val logoHandler = new LogoHandler

  val miraklHandler = if (Settings.Externals.mirakl) new MiraklHandlerImpl else new MiraklHandlerUndef
  val accountHandler = new AccountHandler

}
