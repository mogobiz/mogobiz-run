package com.mogobiz.handlers

import java.util.Locale

import com.mogobiz.cart._
import com.mogobiz._
import org.json4s.JsonAST.JValue


class CartHandler {
  val esClient = new ElasticSearchClient
  val cartService = CartBoService
  val cartRenderService = CartRenderService


  def queryCartInit(storeCode: String, uuid: String, params: CartParameters): Map[String, Any] = {

    val cart = cartService.initCart(uuid)

    val lang: String = if (params.lang == "_all") "fr" else params.lang //FIXME with default Lang
    //val locale = Locale.forLanguageTag(lang)
    val country = params.country.getOrElse("FR") //FIXME trouver une autre valeur par défaut ou refuser l'appel
    val locale = new Locale(lang, country)

    val currency = esClient.getCurrency(storeCode, params.currency, lang)
    cartRenderService.renderCart(cart, currency, locale)
  }

  def queryCartClear(storeCode: String, uuid: String, params: CartParameters): Map[String, Any] = {
    val cart = cartService.initCart(uuid)

    val lang: String = if (params.lang == "_all") "fr" else params.lang //FIX with default Lang
    //val locale = Locale.forLanguageTag(lang)
    val country = params.country.getOrElse("FR") //FIXME trouver une autre valeur par défaut ou refuser l'appel
    val locale = new Locale(lang, country)

    val currency = esClient.getCurrency(storeCode, params.currency, lang)
    val updatedCart = cartService.clear(locale, currency.code, cart)
    cartRenderService.renderCart(updatedCart, currency, locale)
  }

  def queryCartItemAdd(storeCode: String, uuid: String, params: CartParameters, cmd: AddToCartCommand): Map[String, Any] = {

    val cart = cartService.initCart(uuid)

    val lang: String = if (params.lang == "_all") "fr" else params.lang //FIXME with default Lang
    //val locale = Locale.forLanguageTag(lang)

    val country = params.country.getOrElse("FR") //FIXME trouver une autre valeur par défaut ou refuser l'appel
    val locale = new Locale(lang, country)

    val currency = esClient.getCurrency(storeCode, params.currency, lang)
    try {
      val updatedCart = cartService.addItem(locale, currency.code, cart, cmd.skuId, cmd.quantity, cmd.dateTime, cmd.registeredCartItems)
      val data = cartRenderService.renderCart(updatedCart, currency, locale)
      val response = Map(
        ("success" -> true),
        ("data" -> data),
        ("errors" -> List())
      )

      response

    } catch {
      case e: AddCartItemException => {
        val response = Map(
          ("success" -> false),
          ("data" -> cart),
          ("errors" -> e.getErrors(locale))
        )
        response
      }
    }
  }

  def queryCartItemUpdate(storeCode: String, uuid: String, cartItemId: String, params: CartParameters, cmd: UpdateCartItemCommand): Map[String, Any] = {

    val cart = cartService.initCart(uuid)

    val lang: String = if (params.lang == "_all") "fr" else params.lang //FIXME with default Lang
    //val locale = Locale.forLanguageTag(lang)
    val country = params.country.getOrElse("FR") //FIXME trouver une autre valeur par défaut ou refuser l'appel
    val locale = new Locale(lang, country)

    val currency = esClient.getCurrency(storeCode, params.currency, lang)
    try {
      val updatedCart = cartService.updateItem(locale, currency.code, cart, cartItemId, cmd.quantity)
      val data = cartRenderService.renderCart(updatedCart, currency, locale)
      val response = Map(
        ("success" -> true),
        ("data" -> data),
        ("errors" -> List())
      )
      response
    } catch {
      case e: UpdateCartItemException => {
        val response = Map(
          ("success" -> false),
          ("data" -> cart),
          ("errors" -> e.getErrors(locale))
        )
        response
      }
    }
  }

  def queryCartItemRemove(storeCode: String, uuid: String, cartItemId: String, params: CartParameters): Map[String, Any] = {
    val cart = cartService.initCart(uuid)

    val lang: String = if (params.lang == "_all") "fr" else params.lang //FIX with default Lang
    //val locale = Locale.forLanguageTag(lang)
    val country = params.country.getOrElse("FR") //FIXME trouver une autre valeur par défaut ou refuser l'appel
    val locale = new Locale(lang, country)

    val currency = esClient.getCurrency(storeCode, params.currency, lang)
    try {
      val updatedCart = cartService.removeItem(locale, currency.code, cart, cartItemId)
      val data = cartRenderService.renderCart(updatedCart, currency, locale)
      val response = Map(
        ("success" -> true),
        ("data" -> data),
        ("errors" -> List())
      )
      response
    } catch {
      case e: RemoveCartItemException => {
        val response = Map(
          ("success" -> false),
          ("data" -> cart),
          ("errors" -> e.getErrors(locale))
        )
        response
      }
    }
  }

  def queryCartCouponAdd(storeCode: String, uuid: String, couponCode: String, params: CouponParameters): Map[String, Any] = {

    println("evaluate coupon parameters")
    val lang: String = if (params.lang == "_all") "fr" else params.lang //FIX with default Lang

    //val locale = Locale.forLanguageTag(lang)
    val country = params.country.getOrElse("FR") //FIXME trouver une autre valeur par défaut ou refuser l'appel
    val locale = new Locale(lang, country)

    val currency = esClient.getCurrency(storeCode, params.currency, lang)
    val cart = cartService.initCart(uuid)

    try {
      val updatedCart = cartService.addCoupon(storeCode, couponCode, cart, locale, currency.code)
      val data = cartRenderService.renderCart(updatedCart, currency, locale)
      val response = Map(
        ("success" -> true),
        ("data" -> data),
        ("errors" -> List())
      )
      response
    } catch {
      case e: AddCouponToCartException => {
        val response = Map(
          ("success" -> false),
          ("data" -> cart),
          ("errors" -> e.getErrors(locale))
        )
        response
      }
    }
    //complete("add coupon")
  }

  def queryCartCouponDelete(storeCode: String, uuid: String, couponCode: String, params: CouponParameters): Map[String, Any] = {
    println("evaluate coupon parameters")
    val lang: String = if (params.lang == "_all") "fr" else params.lang //FIX with default Lang
    //val locale = Locale.forLanguageTag(lang)
    val country = params.country.getOrElse("FR") //FIXME trouver une autre valeur par défaut ou refuser l'appel
    val locale = new Locale(lang, country)
    val currency = esClient.getCurrency(storeCode, params.currency, lang)
    val cart = cartService.initCart(uuid)

    try {
      val updatedCart = cartService.removeCoupon(storeCode, couponCode, cart, locale, currency.code)
      val data = cartRenderService.renderCart(updatedCart, currency, locale)
      val response = Map(
        ("success" -> true),
        ("data" -> data),
        ("errors" -> List())
      )
      response
    } catch {
      case e: RemoveCouponFromCartException => {
        val response = Map(
          ("success" -> false),
          ("data" -> cart),
          ("errors" -> e.getErrors(locale))
        )
        response
      }
    }
    //complete("remove coupon")
  }

  def queryCartPaymentPrepare(storeCode: String, uuid: String, params: PrepareTransactionParameters): Map[String, Any] = {

    val lang: String = if (params.lang == "_all") "fr" else params.lang
    //FIXME with default Lang
    //val locale = Locale.forLanguageTag(lang)
    //val country = params.country.getOrElse(locale.getCountry)
    val country = params.country.getOrElse("FR")
    //FIXME trouver une autre valeur par défaut ou refuser l'appel
    val locale = new Locale(lang, country)

    /*
    println(s"locale=${locale}")
    println(s"params.country=${params.country}")
    println(s"locale.getCountry=${locale.getCountry}")
    */

    val currency = esClient.getCurrency(storeCode, params.currency, lang)
    val cart = cartService.initCart(uuid)

    try {
      val data = cartService.prepareBeforePayment(storeCode, country, params.state, currency.code, cart, currency)

      val response = Map(
        ("success" -> true),
        ("data" -> data),
        ("errors" -> List())
      )
      response
    } catch {
      case e: CartException => {
        val response = Map(
          ("success" -> false),
          ("data" -> cart),
          ("errors" -> e.getErrors(locale))
        )
        response
      }
    }
    //complete("prepare")
  }

  def queryCartPaymentCommit(storeCode: String, uuid: String, params: CommitTransactionParameters): Map[String, Any] = {
    val cart = cartService.initCart(uuid)

    try {
      val emailingData = cartService.commit(cart, params.transactionUuid)
      val response = Map(
        ("success" -> true),
        ("data" -> emailingData),
        ("errors" -> List())
      )
      response
    } catch {
      case e: Exception => {
        val response = Map(
          ("success" -> false),
          ("data" -> cart),
          ("errors" -> e.getMessage)
        )
        response
      }
    }
    //complete("commit")
  }

  def queryCartPaymentCancel(storeCode: String, uuid: String, params: CancelTransactionParameters): Map[String, Any] = {
    val lang: String = if (params.lang == "_all") "fr" else params.lang //FIX with default Lang
    val locale = Locale.forLanguageTag(lang)
    val currency = esClient.getCurrency(storeCode, params.currency, lang)
    val cart = cartService.initCart(uuid)
    try {
      val updatedCart = cartService.cancel(cart)
      val data = cartRenderService.renderCart(updatedCart, currency, locale)
      val response = Map(
        ("success" -> true),
        ("data" -> data),
        ("errors" -> List())
      )
      response
    } catch {
      case e: CartException => {
        val response = Map(
          ("success" -> false),
          ("data" -> cart),
          ("errors" -> e.getErrors(locale))
        )
        response
      }
    }
  }

}
