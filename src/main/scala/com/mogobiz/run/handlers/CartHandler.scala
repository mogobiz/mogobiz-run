package com.mogobiz.run.handlers

import java.util.Locale

import com.mogobiz.es.EsClient
import com.mogobiz.pay.model.Mogopay
import com.mogobiz.run.cart._
import com.mogobiz.run.config.Settings
import com.mogobiz.run.es._
import com.mogobiz.run.exceptions._
import com.mogobiz.run.implicits.Json4sProtocol
import com.mogobiz.run.learning.{CartRegistration, UserActionRegistration}
import com.mogobiz.run.model.MogoLearn.UserAction
import com.mogobiz.run.model.RequestParameters._
import com.mogobiz.run.model._
import com.sksamuel.elastic4s.ElasticDsl._
import org.joda.time.DateTime


class CartHandler {
  val cartService = CartBoService
  val cartRenderService = CartRenderService


  private def _buildLocal(lang: String, country: Option[String]) : Locale = {
    val defaultLocal = Locale.getDefault
    val l = if (lang == "_all") defaultLocal.getLanguage else lang
    val c = if (country.isEmpty) defaultLocal.getCountry else country.get
    new Locale(l, c)
  }

  def queryCartInit(storeCode: String, uuid: String, params: CartParameters, accountId:Option[Mogopay.Document]): Map[String, Any] = {
    val cart = cartService.initCart(storeCode, uuid, accountId)

    val locale = _buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val computeCart = cartService.computeStoreCart(cart, params.country, params.state)
    cartRenderService.renderCart(computeCart, currency, locale)
  }

  def queryCartClear(storeCode: String, uuid: String, params: CartParameters, accountId:Option[Mogopay.Document]): Map[String, Any] = {
    val cart = cartService.initCart(storeCode, uuid, accountId)

    val locale = _buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val updatedCart = cartService.clear(cart)
    val computeCart = cartService.computeStoreCart(updatedCart, params.country, params.state)
    cartRenderService.renderCart(computeCart, currency, locale)
  }

  @throws[NotFoundException]
  @throws[MinMaxQuantityException]
  @throws[DateIsNullException]
  @throws[UnsaleableDateException]
  @throws[NotEnoughRegisteredCartItemException]
  def queryCartItemAdd(storeCode: String, uuid: String, params: CartParameters, cmd: AddToCartCommand, accountId:Option[Mogopay.Document]): Map[String, Any] = {
    val cart = cartService.initCart(storeCode, uuid, accountId)

    val locale = _buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val updatedCart = cartService.addItem(cart, cmd.skuId, cmd.quantity, cmd.dateTime, cmd.registeredCartItems)
    val computeCart = cartService.computeStoreCart(updatedCart, params.country, params.state)
    cartRenderService.renderCart(computeCart, currency, locale)
  }

  def queryCartValidate(storeCode: String, uuid: String, params: CartParameters, accountId:Option[Mogopay.Document]): Map[String, Any] = {

    val cart = cartService.initCart(storeCode, uuid, accountId)

    val locale = _buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    try {
      val updatedCart = cartService.validateCart(cart)
      val computeCart = cartService.computeStoreCart(updatedCart, params.country, params.state)
      val data = cartRenderService.renderCart(computeCart, currency, locale)
      val response = Map(
        "success" -> true,
        "data" -> data,
        "errors" -> List()
      )

      response

    } catch {
      case e: ValidateCartException =>
        val response = Map(
          "success" -> false,
          "data" -> cart,
          "errors" -> e.getErrors(locale)
        )
        response
      case t: Throwable =>
        t.printStackTrace()
        throw t
    }
  }

  def queryCartItemUpdate(storeCode: String, uuid: String, cartItemId: String, params: CartParameters, cmd: UpdateCartItemCommand, accountId:Option[Mogopay.Document]): Map[String, Any] = {

    val cart = cartService.initCart(storeCode, uuid, accountId)

    val locale = _buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    try {
      val updatedCart = cartService.updateItem(cart, cartItemId, cmd.quantity)
      val computeCart = cartService.computeStoreCart(updatedCart, params.country, params.state)
      val data = cartRenderService.renderCart(computeCart, currency, locale)
      val response = Map(
        "success" -> true,
        "data" -> data,
        "errors" -> List()
      )
      response
    } catch {
      case e: UpdateCartItemException =>
        val response = Map(
          "success" -> false,
          "data" -> cart,
          "errors" -> e.getErrors(locale)
        )
        response
    }
  }

  def queryCartItemRemove(storeCode: String, uuid: String, cartItemId: String, params: CartParameters, accountId:Option[Mogopay.Document]): Map[String, Any] = {
    val cart = cartService.initCart(storeCode, uuid, accountId)

    val locale = _buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    try {
      val updatedCart = cartService.removeItem(cart, cartItemId)
      val computeCart = cartService.computeStoreCart(updatedCart, params.country, params.state)
      val data = cartRenderService.renderCart(computeCart, currency, locale)
      val response = Map(
        "success" -> true,
        "data" -> data,
        "errors" -> List()
      )
      response
    } catch {
      case e: RemoveCartItemException =>
        val response = Map(
          "success" -> false,
          "data" -> cart,
          "errors" -> e.getErrors(locale)
        )
        response
    }
  }

  def queryCartCouponAdd(storeCode: String, uuid: String, couponCode: String, params: CouponParameters, accountId:Option[Mogopay.Document]): Map[String, Any] = {

    val locale = _buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val cart = cartService.initCart(storeCode, uuid, accountId)

    try {
      val updatedCart = cartService.addCoupon(couponCode, cart)
      val computeCart = cartService.computeStoreCart( updatedCart, params.country, params.state)
      val data = cartRenderService.renderCart(computeCart, currency, locale)
      val response = Map(
        "success" -> true,
        "data" -> data,
        "errors" -> List()
      )
      response
    } catch {
      case e: AddCouponToCartException =>
        val response = Map(
          "success" -> false,
          "data" -> cart,
          "errors" -> e.getErrors(locale)
        )
        response
    }
  }

  def queryCartCouponDelete(storeCode: String, uuid: String, couponCode: String, params: CouponParameters, accountId:Option[Mogopay.Document]): Map[String, Any] = {
    val locale = _buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val cart = cartService.initCart(storeCode, uuid, accountId)

    try {
      val updatedCart = cartService.removeCoupon(couponCode, cart)
      val computeCart = cartService.computeStoreCart(updatedCart, params.country, params.state)
      val data = cartRenderService.renderCart(computeCart, currency, locale)
      val response = Map(
        "success" -> true,
        "data" -> data,
        "errors" -> List()
      )
      response
    } catch {
      case e: RemoveCouponFromCartException =>
        val response = Map(
          "success" -> false,
          "data" -> cart,
          "errors" -> e.getErrors(locale)
        )
        response
    }
    //complete("remove coupon")
  }

  def queryCartPaymentPrepare(storeCode: String, uuid: String, params: PrepareTransactionParameters, accountId:Option[Mogopay.Document]): Map[String, Any] = {

    val locale = _buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val cart = cartService.initCart(storeCode, uuid, accountId)

    try {
      val data = cartService.prepareBeforePayment(params.country, params.state, params.shippingAddress, currency, cart, params.buyer, locale)

      val response = Map(
        "success" -> true,
        "data" -> data,
        "errors" -> List()
      )
      response
    } catch {
      case e: Exception =>
        val response = Map(
          "success" -> false,
          "data" -> cart,
          "errors" -> List(e.getMessage)
        )
        response
    }
  }

  def queryCartPaymentCommit(storeCode: String, uuid: String, params: CommitTransactionParameters, accountId:Option[Mogopay.Document]): Map[String, Any] = {
    val locale = _buildLocal(params.lang, params.country)
    val cart = cartService.initCart(storeCode, uuid, accountId)
    val productIds = cart.cartItems.map { item =>
        UserActionRegistration.register(storeCode, uuid, item.productId.toString, UserAction.Purchase)
        item.productId.toString
    }
    CartRegistration.register(storeCode, uuid, productIds)

    try {
      cartService.commit(cart, params.transactionUuid, locale)
      val response = Map(
        "success" -> true,
        "errors" -> List()
      )
      response
    } catch {
      case e: Exception =>
        val response = Map(
          "success" -> false,
          "errors" -> List(e.getMessage)
        )
        response
    }
  }

  def queryCartPaymentCancel(storeCode: String, uuid: String, params: CancelTransactionParameters, accountId:Option[Mogopay.Document]): Map[String, Any] = {
    val locale = _buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val cart = cartService.initCart(storeCode, uuid, accountId)
    try {
      val updatedCart = cartService.cancel(cart)
      val computeCart = cartService.computeStoreCart(updatedCart, params.country, params.state)
      val data = cartRenderService.renderCart(computeCart, currency, locale)
      val response = Map(
        "success" -> true,
        "data" -> data,
        "errors" -> List()
      )
      response
    } catch {
      case e: CartException =>
        val response = Map(
          "success" -> false,
          "data" -> cart,
          "errors" -> e.getErrors(locale)
        )
        response
    }
  }

  def cleanup(): Unit ={
    //println("cleanup cart")
    cartService.cleanExpiredCart
  }

}

object StoreCartDao {

  val index = Settings.cart.EsIndex

  def findByDataUuidAndUserUuid(dataUuid: String, userUuid: Option[Mogopay.Document]): Option[StoreCart] = {
    val uuid = dataUuid + "--" + userUuid.getOrElse("None")
    EsClient.load[StoreCart](index, uuid)
  }

  def save(entity: StoreCart): Boolean = {
    EsClient.update[StoreCart](index, entity.copy(expireDate = DateTime.now.plusSeconds(60 * Settings.cart.lifetime)), true, false)
  }

  def delete(cart: StoreCart) : Unit = {
    EsClient.delete[StoreCart](index, cart.uuid, false)
  }

  def getExpired() : List[StoreCart] = {
    val req = search in index -> "StoreCart" filter and (
      rangeFilter("expireDate") lt "now"
    )

    EsClient.searchAll[StoreCart](req).toList
  }
}
