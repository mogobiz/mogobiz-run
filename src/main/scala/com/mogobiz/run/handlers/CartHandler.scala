package com.mogobiz.run.handlers

import java.util.Locale

import com.mogobiz.pay.model.Mogopay
import com.mogobiz.run.cart._
import com.mogobiz.run.es._
import com.mogobiz.run.learning.UserActionRegistration
import com.mogobiz.run.model._


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
    val cart = cartService.initCart(uuid, accountId)

    val locale = _buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val computeCart = cartService.computeStoreCart(storeCode, cart, params.country, None)
    cartRenderService.renderCart(computeCart, currency, locale)
  }

  def queryCartClear(storeCode: String, uuid: String, params: CartParameters, accountId:Option[Mogopay.Document]): Map[String, Any] = {
    val cart = cartService.initCart(uuid, accountId)

    val locale = _buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val updatedCart = cartService.clear(cart)
    val computeCart = cartService.computeStoreCart(storeCode, updatedCart, params.country, None)
    cartRenderService.renderCart(computeCart, currency, locale)
  }

  def queryCartItemAdd(storeCode: String, uuid: String, params: CartParameters, cmd: AddToCartCommand, accountId:Option[Mogopay.Document]): Map[String, Any] = {

    val cart = cartService.initCart(uuid, accountId)

    val locale = _buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    try {
      val updatedCart = cartService.addItem(cart, cmd.skuId, cmd.quantity, cmd.dateTime, cmd.registeredCartItems)
      val computeCart = cartService.computeStoreCart(storeCode, updatedCart, params.country, None)
      val data = cartRenderService.renderCart(computeCart, currency, locale)
      val response = Map(
        "success" -> true,
        "data" -> data,
        "errors" -> List()
      )

      response

    } catch {
      case e: AddCartItemException =>
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

  def queryCartValidate(storeCode: String, uuid: String, params: CartParameters, accountId:Option[Mogopay.Document]): Map[String, Any] = {

    val cart = cartService.initCart(uuid, accountId)

    val locale = _buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    try {
      val updatedCart = cartService.validateCart(cart)
      val computeCart = cartService.computeStoreCart(storeCode, updatedCart, params.country, None)
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

    val cart = cartService.initCart(uuid, accountId)

    val locale = _buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    try {
      val updatedCart = cartService.updateItem(cart, cartItemId, cmd.quantity)
      val computeCart = cartService.computeStoreCart(storeCode, updatedCart, params.country, None)
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
    val cart = cartService.initCart(uuid, accountId)

    val locale = _buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    try {
      val updatedCart = cartService.removeItem(cart, cartItemId)
      val computeCart = cartService.computeStoreCart(storeCode, updatedCart, params.country, None)
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

    val cart = cartService.initCart(uuid, accountId)

    try {
      val updatedCart = cartService.addCoupon(storeCode, couponCode, cart)
      val computeCart = cartService.computeStoreCart(storeCode, updatedCart, params.country, None)
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

    val cart = cartService.initCart(uuid, accountId)

    try {
      val updatedCart = cartService.removeCoupon(storeCode, couponCode, cart)
      val computeCart = cartService.computeStoreCart(storeCode, updatedCart, params.country, None)
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

    val cart = cartService.initCart(uuid, accountId)

    try {
      val data = cartService.prepareBeforePayment(storeCode, params.country, params.state, currency, cart, params.buyer)

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
          "errors" -> e.getMessage
        )
        response
    }
  }

  def queryCartPaymentCommit(storeCode: String, uuid: String, params: CommitTransactionParameters, accountId:Option[Mogopay.Document]): Map[String, Any] = {
    val cart = cartService.initCart(uuid, accountId)
    cart.cartItems.foreach { item =>
        UserActionRegistration.register(storeCode, uuid, item.productId.toString, UserAction.Purchase)
    }
    try {
      val emailingData = cartService.commit(cart, params.transactionUuid)
      val response = Map(
        "success" -> true,
        "data" -> emailingData,
        "errors" -> List()
      )
      response
    } catch {
      case e: Exception =>
        val response = Map(
          "success" -> false,
          "data" -> cart,
          "errors" -> e.getMessage
        )
        response
    }
  }

  def queryCartPaymentCancel(storeCode: String, uuid: String, params: CancelTransactionParameters, accountId:Option[Mogopay.Document]): Map[String, Any] = {
    val locale = _buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val cart = cartService.initCart(uuid, accountId)
    try {
      val updatedCart = cartService.cancel(cart)
      val computeCart = cartService.computeStoreCart(storeCode, updatedCart, params.country, None)
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
