/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import java.io.ByteArrayOutputStream
import java.util.{Date, Locale, UUID}

import akka.actor.Props
import com.mogobiz.es.EsClient
import com.mogobiz.json.JacksonConverter
import com.mogobiz.pay.codes.MogopayConstant
import com.mogobiz.pay.common.{
  Cart => CartPay,
  CartItem => CartItemPay,
  Coupon => CouponPay,
  RegisteredCartItem => RegisteredCartItemPay,
  Shipping => ShippingPay,
  ShopCart => ShopCartPay,
  _
}
import com.mogobiz.pay.config.MogopayHandlers
import com.mogobiz.pay.model.Mogopay
import com.mogobiz.pay.model.Mogopay.{
  AccountAddress,
  SelectShippingCart,
  ShippingCart
}
import com.mogobiz.run.actors.EsUpdateActor
import com.mogobiz.run.actors.EsUpdateActor.StockUpdateRequest
import com.mogobiz.run.config.MogobizHandlers.handlers._
import com.mogobiz.run.config.Settings
import com.mogobiz.run.dashboard.Dashboard
import com.mogobiz.run.es._
import com.mogobiz.run.exceptions._
import com.mogobiz.run.learning.{CartRegistration, UserActionRegistration}
import com.mogobiz.run.model.Learning.UserAction
import com.mogobiz.run.model.Mogobiz._
import com.mogobiz.run.model.Render.{Coupon, RegisteredCartItem}
import com.mogobiz.run.model.RequestParameters._
import com.mogobiz.run.model.{StoreCart, _}
import com.mogobiz.run.services.RateBoService
import com.mogobiz.run.utils.Utils
import com.mogobiz.system.ActorSystemLocator
import com.mogobiz.utils.{QRCodeUtils, SymmetricCrypt}
import com.sun.org.apache.xml.internal.security.utils.Base64
import com.typesafe.scalalogging.StrictLogging
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.json4s._
import org.json4s.ext.JodaTimeSerializers
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization._
import scalikejdbc._

import scala.util.{Failure, Success, Try}

class CartHandler extends StrictLogging {
  val rateService = RateBoService

  def runInTransaction[U](call: DBSession => CartWithChanges,
                          success: StoreCart => U): U = {
    com.mogobiz.utils.GlobalUtil.runInTransaction(call, {
      cartAndChanges: CartWithChanges =>
        val cart = cartAndChanges.cart
        notifyChangesIntoES(cart.storeCode, cartAndChanges.changes, true)
        success(cart)
    })
  }

  def runInTransaction[U](call: => DBSession => CartWithPricesAndChanges,
                          success: StoreCartWithPrices => U): U = {
    com.mogobiz.utils.GlobalUtil.runInTransaction(
      call, { cartAndChanges: CartWithPricesAndChanges =>
        val cart = cartAndChanges.cart
        notifyChangesIntoES(cart.storeCode, cartAndChanges.changes, true)
        success(cart)
      }
    )
  }

  /**
    * Permet de récupérer le contenu du panier<br/>
    * Si le panier n'existe pas, il est créé<br/>
    *
    * @param storeCode : store du code (pour la recherche dans ES)
    * @param uuid : uuid du panier
    * @param params : paramètre du panier (country, currency, etc...)
    * @param accountId : uuid du compte (le cookie)
    * @return
    */
  def queryCartInit(storeCode: String,
                    uuid: String,
                    params: CartParameters,
                    accountId: Option[Mogopay.Document]): Map[String, Any] = {
    val locale = buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)
    val removeUnsalableItem = true
    val cart = initCart(storeCode,
                        uuid,
                        accountId,
                        removeUnsalableItem,
                        params.country,
                        params.state)

    val computeCart = computeStoreCart(cart, params.country, params.state)
    renderCart(computeCart, currency, locale)
  }

  /**
    * Vide le contenu du panier
    */
  def queryCartClear(storeCode: String,
                     uuid: String,
                     params: CartParameters,
                     accountId: Option[Mogopay.Document]): Map[String, Any] = {
    val locale = buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)
    val removeUnsalableItem = true
    val cart = initCart(storeCode,
                        uuid,
                        accountId,
                        removeUnsalableItem,
                        params.country,
                        params.state)

    val updatedCart = clearCart(
      cart, { cart: StoreCart =>
        val updatedCart = StoreCart(storeCode = cart.storeCode,
                                    dataUuid = cart.dataUuid,
                                    userUuid = cart.userUuid)
        StoreCartDao.save(updatedCart)
        updatedCart
      }
    )

    val computeCart =
      computeStoreCart(updatedCart, params.country, params.state)
    renderCart(computeCart, currency, locale)
  }

  /**
    * Ajoute un item au panier
    */
  @throws[NotFoundException]
  @throws[MinMaxQuantityException]
  @throws[DateIsNullException]
  @throws[UnsaleableDateException]
  @throws[NotEnoughRegisteredCartItemException]
  def queryCartItemAdd(
      storeCode: String,
      uuid: String,
      params: CartParameters,
      cmd: AddCartItemRequest,
      accountId: Option[Mogopay.Document]): Map[String, Any] = {
    val locale = buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)
    val removeUnsalableItem = true
    val cart = initCart(storeCode,
                        uuid,
                        accountId,
                        removeUnsalableItem,
                        params.country,
                        params.state)

    val transactionalBloc = { implicit session: DBSession =>
      val productAndSku = ProductDao.getProductAndSku(cart.storeCode, cmd.skuId)
      if (productAndSku.isEmpty) throw NotFoundException("unknown sku")

      if (!checkProductAndSkuSalabality(productAndSku.get,
                                        params.country,
                                        params.state))
        throw UnsaleableProductException()

      val product = productAndSku.get._1
      val sku = productAndSku.get._2
      val startEndDate =
        Utils.verifyAndExtractStartEndDate(product, sku, cmd.dateTime)
      val startDate =
        if (startEndDate.isDefined) Some(startEndDate.get._1) else None
      val endDate =
        if (startEndDate.isDefined) Some(startEndDate.get._2) else None

      if (sku.minOrder > cmd.quantity || (sku.maxOrder < cmd.quantity && sku.maxOrder > -1))
        throw MinMaxQuantityException(sku.minOrder, sku.maxOrder)

      if (cmd.dateTime.isEmpty && !ProductCalendar.NO_DATE.equals(
            product.calendarType))
        throw DateIsNullException()
      else if (cmd.dateTime.isDefined && startEndDate.isEmpty)
        throw UnsaleableDateException()

      if (product.xtype == ProductType.SERVICE && cmd.registeredCartItems.size != cmd.quantity)
        throw NotEnoughRegisteredCartItemException()

      if (!stockHandler.checkStock(cart.storeCode,
                                   product,
                                   sku,
                                   cmd.quantity,
                                   startDate)) {
        throw InsufficientStockCartItemException()
      }

      val newCartItemId = cmd.uuid.getOrElse(UUID.randomUUID().toString)
      val registeredItems = cmd.registeredCartItems.map { item =>
        RegisteredCartItem(
          newCartItemId,
          item.uuid.getOrElse(UUID.randomUUID().toString),
          item.email,
          item.firstname,
          item.lastname,
          item.phone,
          item.birthdate
        )
      }

      val salePrice = Math.max(sku.salePrice, 0)
      val indexEs =
        EsClient.getUniqueIndexByAlias(storeCode).getOrElse(storeCode)
      val cartItem = StoreCartItem(
        indexEs,
        newCartItemId,
        product.id,
        product.name,
        product.picture,
        cmd.productUrl,
        product.xtype,
        product.calendarType,
        sku.id,
        sku.name,
        cmd.quantity,
        sku.price,
        salePrice,
        startDate,
        endDate,
        registeredItems,
        product.shipping,
        None,
        sku.computedExternalCode
      )

      val invalidationResult = invalidateCart(cart)
      val shopId = product.shopId.getOrElse(MogopayConstant.SHOP_MOGOBIZ)
      val shopCart = invalidationResult.cart
        .findShopCart(shopId)
        .getOrElse(StoreShopCart(shopId))
      val newShopCart = shopCart.copy(
        cartItems = addOrMergeCartItem(shopCart.cartItems, cartItem))
      val newCart =
        invalidationResult.cart.copy(
          shopCarts = addOrReplaceShopCart(invalidationResult.cart.shopCarts,
                                           newShopCart))
      invalidationResult.copy(cart = newCart)
    }
    val updatedCart = runInTransaction(transactionalBloc, { cart: StoreCart =>
      StoreCartDao.save(cart)
      cart
    })

    val computeCart =
      computeStoreCart(updatedCart, params.country, params.state)
    renderCart(computeCart, currency, locale)
  }

  protected def checkProductAndSkuSalabality(
      productAndSku: (Mogobiz.Product, Mogobiz.Sku),
      country: Option[String],
      state: Option[String]): Boolean = {
    val now = DateTime.now().toLocalDate
    val sku = productAndSku._2
    val skuStartDate = sku.startDate.getOrElse(DateTime.now()).toLocalDate
    val skuEndDate = sku.stopDate.getOrElse(DateTime.now()).toLocalDate

    !skuStartDate.isAfter(now) && !skuEndDate.isBefore(now) && (state.isEmpty || taxRateHandler
      .findTaxRateByProduct(productAndSku._1, country, state)
      .isDefined)
  }

  /**
    * Met à jour la quantité d'un item du panier
    */
  @throws[InsufficientStockCartItemException]
  @throws[NotFoundException]
  def queryCartItemUpdate(
      storeCode: String,
      uuid: String,
      cartItemId: String,
      params: CartParameters,
      cmd: UpdateCartItemRequest,
      accountId: Option[Mogopay.Document]): Map[String, Any] = {
    val locale = buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)
    val removeUnsalableItem = true
    val cart = initCart(storeCode,
                        uuid,
                        accountId,
                        removeUnsalableItem,
                        params.country,
                        params.state)
    findShopCartByCartItemId(cart, cartItemId)
      .map { shopCart =>
        shopCart.cartItems
          .find { item =>
            item.id == cartItemId
          }
          .map { existCartItem =>
            val updatedCart =
              if (ProductType.SERVICE != existCartItem.xtype && existCartItem.quantity != cmd.quantity) {
                val productAndSku =
                  ProductDao.getProductAndSku(cart.storeCode,
                                              existCartItem.skuId)
                val product = productAndSku.get._1
                val sku = productAndSku.get._2

                runInTransaction(
                  { implicit session: DBSession =>
                    if (!stockHandler.checkStock(cart.storeCode,
                                                 product,
                                                 sku,
                                                 cmd.quantity,
                                                 existCartItem.startDate)) {
                      throw InsufficientStockCartItemException()
                    }

                    val invalidationResult = invalidateCart(cart)
                    val newCartItems = shopCart.cartItems.map { item =>
                      if (item == existCartItem)
                        item.copy(quantity = cmd.quantity)
                      else item
                    }
                    invalidationResult.copy(
                      cart =
                        buildNewCartByReplaceCartItems(invalidationResult.cart,
                                                       shopCart,
                                                       newCartItems))
                  }, { cart: StoreCart =>
                    StoreCartDao.save(cart)
                    cart
                  }
                )
              } else {
                logger.debug("silent op")
                cart
              }
            val computeCart =
              computeStoreCart(updatedCart, params.country, params.state)
            renderCart(computeCart, currency, locale)
          }
          .getOrElse(throw NotFoundException(""))
      }
      .getOrElse(throw NotFoundException(""))
  }

  /**
    * Supprime un item du panier
    */
  def queryCartItemRemove(
      storeCode: String,
      uuid: String,
      cartItemId: String,
      params: CartParameters,
      accountId: Option[Mogopay.Document]): Map[String, Any] = {
    val removeUnsalableItem = true
    val locale = buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val cart = initCart(storeCode,
                        uuid,
                        accountId,
                        removeUnsalableItem,
                        params.country,
                        params.state)

    val updatedCart = runInTransaction(
      { implicit session: DBSession =>
        val invalidationResult = invalidateCart(cart)
        findShopCartByCartItemId(cart, cartItemId)
          .map { shopCart =>
            val newCartItems = shopCart.cartItems.filterNot { item =>
              item.id == cartItemId
            }
            invalidationResult.copy(
              cart = buildNewCartByReplaceCartItems(invalidationResult.cart,
                                                    shopCart,
                                                    newCartItems))
          }
          .getOrElse(invalidationResult)
      }, { cart: StoreCart =>
        StoreCartDao.save(cart)
        cart
      }
    )

    val computeCart =
      computeStoreCart(updatedCart, params.country, params.state)
    renderCart(computeCart, currency, locale)
  }

  /**
    * Ajoute un coupon au panier
    */
  @throws[DuplicateException]
  @throws[InsufficientStockCouponException]
  def queryCartCouponAdd(
      storeCode: String,
      uuid: String,
      couponCode: String,
      params: CouponParameters,
      accountId: Option[Mogopay.Document]): Map[String, Any] = {
    val removeUnsalableItem = true
    val locale = buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val cart = initCart(storeCode,
                        uuid,
                        accountId,
                        removeUnsalableItem,
                        params.country,
                        params.state)

    CouponDao
      .findByCode(cart.storeCode, couponCode)
      .map { coupon =>
        if (!coupon.anonymous) {
          val updatedCart = runInTransaction(
            { implicit session: DBSession =>
              val shopId = MogopayConstant.SHOP_MOGOBIZ
              val couponExist = cart.findShopCart(shopId).exists { shopCart =>
                shopCart.coupons.exists { c =>
                  couponCode == c.code
                }
              }

              if (couponExist)
                throw DuplicateException("")
              else if (!couponHandler.consumeCoupon(cart.storeCode, coupon))
                throw InsufficientStockCouponException()
              else {
                val invalidationResult = invalidateCart(cart)
                val shopCart = invalidationResult.cart
                  .findShopCart(shopId)
                  .getOrElse(StoreShopCart(shopId))
                val newCoupons = StoreCartCoupon(coupon.id, coupon.code) :: shopCart.coupons
                val newShopCart = shopCart.copy(coupons = newCoupons)
                val newCart = invalidationResult.cart.copy(
                  shopCarts =
                    addOrReplaceShopCart(invalidationResult.cart.shopCarts,
                                         newShopCart))
                invalidationResult.copy(cart = newCart)
              }
            }, { cart: StoreCart =>
              StoreCartDao.save(cart)
              cart
            }
          )

          val computeCart =
            computeStoreCart(updatedCart, params.country, params.state)
          renderCart(computeCart, currency, locale)
        } else {
          throw NotFoundException("")
        }
      }
      .getOrElse(throw NotFoundException(""))
  }

  /**
    * Supprime un coupon du panier
    */
  @throws[NotFoundException]
  def queryCartCouponDelete(
      storeCode: String,
      uuid: String,
      couponCode: String,
      params: CouponParameters,
      accountId: Option[Mogopay.Document]): Map[String, Any] = {
    val removeUnsalableItem = true
    val locale = buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val cart = initCart(storeCode,
                        uuid,
                        accountId,
                        removeUnsalableItem,
                        params.country,
                        params.state)

    CouponDao
      .findByCode(cart.storeCode, couponCode)
      .map { coupon =>
        val shopId = MogopayConstant.SHOP_MOGOBIZ
        val existCoupon = cart.findShopCart(shopId).flatMap { shopCart =>
          shopCart.coupons.find { c =>
            couponCode == c.code
          }
        }

        existCoupon
          .map { existCoupon =>
            val updatedCart = runInTransaction(
              { implicit session: DBSession =>
                couponHandler.releaseCoupon(cart.storeCode, coupon)
                val invalidationResult = invalidateCart(cart)

                val shopCart = invalidationResult.cart
                  .findShopCart(shopId)
                  .getOrElse(StoreShopCart(shopId))
                val newCoupons = shopCart.coupons.filterNot { c =>
                  c.code == existCoupon.code
                }
                val newShopCart = shopCart.copy(coupons = newCoupons)
                val newCart = invalidationResult.cart.copy(
                  shopCarts = deleteEmptyShopCarts(
                    addOrReplaceShopCart(invalidationResult.cart.shopCarts,
                                         newShopCart)))
                invalidationResult.copy(cart = newCart)
              }, { cart: StoreCart =>
                StoreCartDao.save(cart)
                cart
              }
            )

            val computeCart =
              computeStoreCart(updatedCart, params.country, params.state)
            renderCart(computeCart, currency, locale)
          }
          .getOrElse(throw NotFoundException(""))
      }
      .getOrElse(throw NotFoundException(""))
  }

  /**
    * Prépare le panier pour le paiement
    */
  @throws[InsufficientStockException]
  def queryCartPaymentPrepare(storeCode: String,
                              uuid: String,
                              params: PrepareTransactionParameters,
                              accountId: Mogopay.Document): Map[String, Any] = {
    val removeUnsalableItem = true
    val locale = buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val cart = initCart(storeCode,
                        uuid,
                        Some(accountId),
                        removeUnsalableItem,
                        params.country,
                        params.state)

    val company = CompanyDao.findByCode(cart.storeCode)

    val transactionalBloc = { implicit session: DBSession =>
      val validationResult = validateCart(
        cart.copy(countryCode = params.country,
                  stateCode = params.state,
                  rate = Some(currency)))

      val deleteResult = deletePendingBOCart(validationResult)
      val cartWithPrice =
        computeStoreCart(deleteResult.cart, params.country, params.state)
      val cartPriceWithChanges =
        CartWithPricesAndChanges(cartWithPrice, deleteResult.changes)

      createBOCart(cartPriceWithChanges,
                   currency,
                   params.buyer,
                   company.get,
                   params.shippingAddress)
    }

    val updatedCartPrice = runInTransaction(transactionalBloc, {
      cartPrice: StoreCartWithPrices =>
        StoreCartDao.save(cartPrice)
        cartPrice
    })
    val renderedCart = renderTransactionCart(updatedCartPrice, currency, locale)
    Map(
      "amount" -> rateService.calculateAmount(updatedCartPrice.totalFinalPrice,
                                              currency),
      "currencyCode" -> currency.code,
      "currencyRate" -> currency.rate.doubleValue(),
      "transactionExtra" -> renderedCart
    )
  }

  def getCartForPay(storeCode: String,
                    uuid: String,
                    accountId: Option[String],
                    currencyCode: String,
                    shippingAddress: Option[AccountAddress]): CartPay = {
    val removeUnsalableItem = false
    val cart =
      initCart(storeCode, uuid, accountId, removeUnsalableItem, None, None)
    val currency = queryCurrency(storeCode, Some(currencyCode))

    // Calcul des données du panier
    val countryCode = shippingAddress.map { _.country }.getOrElse(None)
    val stateCode = shippingAddress.map { _.admin1 }.getOrElse(None)
    val cartWithPrice = computeStoreCart(cart, countryCode, stateCode)

    val compagny = CompanyDao.findByCode(storeCode)
    val shipFromAddressOpt = compagny
      .map {
        _.shipFrom
      }
      .getOrElse(None)
    val companyAddress = shipFromAddressOpt.map { shipFromAddress =>
      CompanyAddress(
        storeCode,
        shipFromAddress.road1,
        shipFromAddress.road2,
        shipFromAddress.city,
        shipFromAddress.postalCode,
        shipFromAddress.country.code,
        if (!shipFromAddress.state.isEmpty) Some(shipFromAddress.state)
        else None,
        compagny.flatMap { _.phone },
        compagny.exists { c =>
          c.shippingInternational
        }
      )
    }

    val mogobizFinalPrice = cartWithPrice
      .findShopCart(MogopayConstant.SHOP_MOGOBIZ)
      .map { shopCart =>
        shopCart.totalFinalPrice
      }
      .getOrElse(0L)

    val shippingRulePrice = computeShippingRulePrice(cart.storeCode,
                                                     cart.countryCode,
                                                     mogobizFinalPrice)

    transformCartForCartPay(companyAddress,
                            cartWithPrice,
                            currency,
                            shippingRulePrice)
  }

  def shippingPrices(cart: CartPay, accountId: String): ShippingCart = {
    val addressAndList = MogopayHandlers.handlers.transactionHandler
      .shippingPrices(cart, accountId)

    ShippingCart(Map(MogopayConstant.SHOP_MOGOBIZ -> addressAndList._2))
  }

  protected def computeShippingRulePrice(storeCode: String,
                                         countryCode: Option[String],
                                         cartPice: Long): Option[Long] =
    countryCode.flatMap { country =>
      val shippingRules = ShippingRuleDao.findByCompany(storeCode)
      val v =
        shippingRules.find(sr =>
          sr.countryCode == country && sr.minAmount <= cartPice && cartPice <= sr.maxAmount)
      v.map {
        _.price
      }
    }

  /**
    * Complète le panier après un paiement réalisé avec succès. Le contenu du panier est envoyé par mail comme justificatif
    * et un nouveau panier est créé
    */
  def queryCartPaymentLinkToTransaction(storeCode: String,
                                        uuid: String,
                                        params: CommitTransactionParameters,
                                        accountId: Mogopay.Document): Unit = {
    val removeUnsalableItem = false
    val cart = initCart(storeCode,
                        uuid,
                        Some(accountId),
                        removeUnsalableItem,
                        None,
                        None)

    runInTransaction(
      { implicit session: DBSession =>
        cart.boCartUuid
          .map {
            boCartUuid =>
              boCartHandler
                .find(storeCode, boCartUuid)
                .map {
                  boCart =>
                    val currency =
                      queryCurrency(storeCode, Some(boCart.currencyCode))

                    val newShopCarts = boCart.shopCarts.map { boShopCart =>
                      boShopCart.copy(
                        transactionUuid = Some(params.transactionUuid))
                    }

                    val transactionBoCart =
                      boCart.copy(
                        transactionUuid = Some(params.transactionUuid),
                        shopCarts = newShopCarts)

                    val newChanges = CartChanges(boCartChange =
                      Some(boCartHandler.update(transactionBoCart)))
                    CartWithChanges(cart = cart, changes = newChanges)
                }
                .getOrElse(throw new IllegalArgumentException(
                  "Unabled to retrieve Cart " + cart.uuid + " into BO. It has not been initialized or has already been validated"))
          }
          .getOrElse(throw new IllegalArgumentException(
            "Unabled to retrieve Cart " + cart.uuid + " into BO. It has not been initialized or has already been validated"))

      }, { cart: StoreCart =>
        StoreCartDao.save(cart)
      }
    )
  }

  /**
    * Complète le panier après un paiement réalisé avec succès. Le contenu du panier est envoyé par mail comme justificatif
    * et un nouveau panier est créé
    */
  def queryCartPaymentCommit(storeCode: String,
                             uuid: String,
                             params: CommitTransactionParameters,
                             accountId: Mogopay.Document,
                             selectShippingCart: SelectShippingCart): Unit = {
    val removeUnsalableItem = false
    val cart = initCart(storeCode,
                        uuid,
                        Some(accountId),
                        removeUnsalableItem,
                        None,
                        None)

    Try {
      val productIds = cart.shopCarts.flatMap { shopCart =>
        shopCart.cartItems.map { item =>
          UserActionRegistration.register(storeCode,
                                          uuid,
                                          item.productId.toString,
                                          UserAction.Purchase,
                                          item.quantity)
          item.productId.toString
        }
      }
      CartRegistration.register(storeCode, uuid, productIds)
    }

    runInTransaction(
      { implicit session: DBSession =>
        val transactionCart =
          cart.copy(transactionUuid = Some(params.transactionUuid))
        boCartHandler
          .find(storeCode, transactionCart.boCartUuid.get)
          .map {
            boCart =>
              val currency = queryCurrency(storeCode, Some(boCart.currencyCode))

              val transactionBoCart =
                boCart.copy(transactionUuid = Some(params.transactionUuid),
                            status = TransactionStatus.COMPLETE,
                            externalOrderId = None)
              val newBOCart = boCartHandler.update(transactionBoCart)

              val saleChanges = cart.shopCarts.flatMap {
                shopCart =>
                  shopCart.cartItems.map { cartItem =>
                    val productAndSku =
                      ProductDao.getProductAndSku(transactionCart.storeCode,
                                                  cartItem.skuId)
                    val product = productAndSku.get._1
                    val sku = productAndSku.get._2
                    salesHandler.incrementSales(transactionCart.storeCode,
                                                product,
                                                sku,
                                                cartItem.quantity)
                  }
              }
              Dashboard.indexCart(storeCode, newBOCart, accountId)
              val newChanges = CartChanges(boCartChange = Some(newBOCart),
                                           saleChanges = saleChanges)
              CartWithChanges(cart = transactionCart, changes = newChanges)
          }
          .getOrElse(throw new IllegalArgumentException(
            "Unabled to retrieve Cart " + cart.uuid + " into BO. It has not been initialized or has already been validated"))
      }, { cart: StoreCart =>
        StoreCartDao.save(cart)
        val updatedCart = StoreCart(storeCode = cart.storeCode,
                                    dataUuid = cart.dataUuid,
                                    userUuid = cart.userUuid)
        StoreCartDao.save(updatedCart)
        cart
      }
    )
  }

  /**
    * Met à jour le panier suite à l'abandon ou l'échec du paiement
    */
  def queryCartPaymentCancel(
      storeCode: String,
      uuid: String,
      params: CancelTransactionParameters,
      accountId: Option[Mogopay.Document]): Map[String, Any] = {
    val locale = buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)
    val removeUnsalableItem = false
    val cart =
      initCart(storeCode, uuid, accountId, removeUnsalableItem, None, None)

    val updatedCart = runInTransaction({ implicit session: DBSession =>
      cancelCart(invalidateCart(cart))
    }, { cart: StoreCart =>
      StoreCartDao.save(cart)
      cart
    })

    val computeCart =
      computeStoreCart(updatedCart, params.country, params.state)
    renderCart(computeCart, currency, locale)
  }

  /**
    * Supprime tous les paniers expirés pour l'index fourni
    */
  def cleanup(index: String, querySize: Int): Unit = {
    StoreCartDao.getExpired(index, querySize).map { cart =>
      val r = Try {
        val updatedCart = clearCart(cart, { cart: StoreCart =>
          StoreCartDao.delete(cart)
        })
      }
      r match {
        case Success(_) =>
        case Failure(e) => logger.error(e.getMessage, e)
      }
    }
  }

  /**
    * Crée un BOCart à partir des données fournis en générant si nécessaire les QRCode correspondant. Renvoi
    * un panier en lien avec le BOCart créé
    */
  protected def createBOCart(cartWithChanges: CartWithPricesAndChanges,
                             rate: Currency,
                             buyer: String,
                             company: Company,
                             shippingAddressExtra: String)(
      implicit session: DBSession): CartWithPricesAndChanges = {
    val cart: StoreCartWithPrices = cartWithChanges.cart.copy(rate = Some(rate))
    val storeCode = cart.storeCode

    val shippingAddress =
      JacksonConverter.deserialize[AccountAddress](shippingAddressExtra)

    val boCartUuid = UUID.randomUUID().toString
    val newShopCartAndBOList = cart.shopCarts.map { shopCart =>
      val boShopCartUuid = UUID.randomUUID().toString
      val newCartItemAndBOList = shopCart.cartItems.map { cartItem =>
        val boCartItemUuid = UUID.randomUUID().toString
        val productAndSku =
          ProductDao.getProductAndSku(storeCode, cartItem.skuId)
        val product = productAndSku.get._1
        val sku = productAndSku.get._2

        val boProductUuid = UUID.randomUUID().toString

        // Création des BO pour les RegisteredCartItems et mise à jour des RegisteredCartItems
        val newRegisteredCartItemAndBOList = cartItem.registeredCartItems.map {
          registeredCartItem =>
            val registeredCartItemUuid = UUID.randomUUID().toString

            val shortCodeAndQrCode = product.xtype match {
              case ProductType.SERVICE =>
                val startDateStr = cartItem.startDate.map(d =>
                  d.toString(DateTimeFormat.forPattern("dd/MM/yyyy HH:mm")))
                val shortCode = "C" + boCartUuid + "|S" + boShopCartUuid + "|CI" + boCartItemUuid + "|P" + boProductUuid + "|R" + registeredCartItemUuid
                val qrCodeContent = "EventId:" + product.id + ";BoCartUuid:" + boCartUuid + ";BoShopCartUuid:" + boShopCartUuid +
                  ";boCartItemUuid:" + boCartItemUuid + ";BoProductUuid:" + boProductUuid + ";BoRegisteredCartItemUuid:" + registeredCartItemUuid +
                  ";EventName:" + product.name + ";EventDate:" + startDateStr + ";FirstName:" +
                  registeredCartItem.firstname.getOrElse("") + ";LastName:" + registeredCartItem.lastname
                  .getOrElse("") +
                  ";Phone:" + registeredCartItem.phone
                  .getOrElse("") + ";TicketType:" + sku.name + ";shortCode:" + shortCode

                val hexSecret = true
                val encryptedQrCodeContent =
                  SymmetricCrypt.encrypt(qrCodeContent,
                                         company.aesPassword,
                                         "AES",
                                         hexSecret)
                val output = new ByteArrayOutputStream()
                QRCodeUtils.createQrCode(output,
                                         encryptedQrCodeContent,
                                         256,
                                         "png")
                val qrCodeBase64 = Base64.encode(output.toByteArray)

                (Some(shortCode),
                 Some(qrCodeBase64),
                 Some(encryptedQrCodeContent))
              case _ => (None, None, None)
            }

            val newRegisteredCartItem = shortCodeAndQrCode._3
              .map { qrCodeContent =>
                registeredCartItem.copy(qrCodeContent = Some(
                  product.name + ":" + registeredCartItem.email + "||" + qrCodeContent))
              }
              .getOrElse(registeredCartItem)
            val boRegisteredCartItem =
              boCartHandler.initRegisteredCartItem(registeredCartItemUuid,
                                                   sku,
                                                   cartItem,
                                                   newRegisteredCartItem,
                                                   shortCodeAndQrCode._1,
                                                   shortCodeAndQrCode._2,
                                                   shortCodeAndQrCode._3)
            (newRegisteredCartItem, boRegisteredCartItem)
        }
        val newRegisteredCartItems = newRegisteredCartItemAndBOList.map(_._1)
        val boRegisteredCartItems = newRegisteredCartItemAndBOList.map(_._2)

        val boProduct =
          boCartHandler.initBOProduct(boProductUuid,
                                      cartItem,
                                      product,
                                      principal = true,
                                      boRegisteredCartItems)
        val boDelivery = cartItem.shipping.map { shipping =>
          boCartHandler.initBODelivery(shippingAddressExtra)
        }

        // Downloadable Link

        val downloadableLink =
          if (product.xtype == ProductType.DOWNLOADABLE)
            Some(
              validatorHandler.buildDownloadLink(storeCode,
                                                 boCartUuid,
                                                 boShopCartUuid,
                                                 boCartItemUuid,
                                                 boProductUuid,
                                                 product,
                                                 sku,
                                                 company))
          else None

        // Mise à jour du CartItem et création du BOCartItem
        val newCartItem =
          cartItem.copy(downloadableLink = downloadableLink,
                        registeredCartItems = newRegisteredCartItems)
        val boCartItem = boCartHandler.initBOCartItem(boCartItemUuid,
                                                      newCartItem,
                                                      sku,
                                                      boProduct,
                                                      boDelivery)
        (newCartItem, boCartItem)
      }

      val newCartItems = newCartItemAndBOList.map(_._1)
      val boCartItems = newCartItemAndBOList.map(_._2)

      val newShopCart = shopCart.copy(cartItems = newCartItems)
      val boShopCart =
        boCartHandler.initBOShopCart(newShopCart, cart.rate.get, boCartItems)
      (newShopCart, boShopCart)
    }
    val newShopCarts = newShopCartAndBOList.map(_._1)
    val boShopCarts = newShopCartAndBOList.map(_._2)

    val newCart =
      cart.copy(boCartUuid = Some(boCartUuid), shopCarts = newShopCarts)
    val boCart = boCartHandler.initBOCart(company.id,
                                          boCartUuid,
                                          buyer,
                                          newCart,
                                          shippingAddress,
                                          boShopCarts)

    val newChanges = cartWithChanges.changes.copy(
      boCartChange = Some(boCartHandler.create(boCart)))
    cartWithChanges.copy(cart = newCart, changes = newChanges)
  }

  /**
    * Supprime le BOCart correspondant au panier s'il est en statut Pending
    * et renvoi un panier sans lien avec le boCart supprimé
    */
  protected def deletePendingBOCart(cartWithChange: CartWithChanges)(
      implicit session: DBSession): CartWithChanges = {
    val cart = cartWithChange.cart
    cart.boCartUuid
      .map { boCartUuid =>
        boCartHandler
          .find(cart.storeCode, boCartUuid)
          .map { boCart =>
            if (boCart.status == TransactionStatus.PENDING) {
              boCartHandler.delete(boCart)

              val newChanges =
                cartWithChange.changes.copy(deletedBOCart = Some(boCart))
              val newCart = cart.copy(boCartUuid = None)
              cartWithChange.copy(cart = newCart, changes = newChanges)
            } else cartWithChange
          }
          .getOrElse(cartWithChange)
      }
      .getOrElse(cartWithChange)
  }

  /**
    * Construit un Locale à partir de la langue et du pays.<br/>
    * Si la lang == "_all" alors la langue par défaut est utilisée<br/>
    * Si le pays vaut None alors le pays par défaut est utiulisé
    */
  protected def buildLocal(lang: String, country: Option[String]): Locale = {
    val defaultLocal = Locale.getDefault
    val l = if (lang == "_all") defaultLocal.getLanguage else lang
    val c = if (country.isEmpty) defaultLocal.getCountry else country.get
    new Locale(l, c)
  }

  /**
    * Récupère le panier correspondant au uuid et au compte client.
    * La méthode gère le panier anonyme et le panier authentifié.
    */
  protected def initCart(storeCode: String,
                         uuid: String,
                         currentAccountId: Option[String],
                         removeUnsalableItem: Boolean,
                         country: Option[String],
                         state: Option[String]): StoreCart = {
    def prepareCart(cart: StoreCart) = {
      if (removeUnsalableItem)
        removeAllUnsellableItemsFromCart(cart, country, state)
      else cart
    }

    def getOrCreateStoreCart(cart: Option[StoreCart]): StoreCart = {
      cart match {
        case Some(c) => prepareCart(c)
        case None =>
          val c = StoreCart(storeCode = storeCode,
                            dataUuid = uuid,
                            userUuid = currentAccountId)
          StoreCartDao.save(c)
          c
      }
    }

    if (currentAccountId.isDefined) {
      val cartAnonyme =
        StoreCartDao.findByDataUuidAndUserUuid(storeCode, uuid, None).map { c =>
          prepareCart(c)
        }
      val cartAuthentifie = getOrCreateStoreCart(
        StoreCartDao
          .findByDataUuidAndUserUuid(storeCode, uuid, currentAccountId))

      // S'il y a un panier anonyme, il est fusionné avec le panier authentifié et supprimé de la base
      if (cartAnonyme.isDefined) {
        StoreCartDao.delete(cartAnonyme.get)
        val fusionCart = mergeCarts(cartAnonyme.get, cartAuthentifie)
        StoreCartDao.save(fusionCart)
        fusionCart
      } else cartAuthentifie
    } else {
      // Utilisateur anonyme
      getOrCreateStoreCart(
        StoreCartDao.findByDataUuidAndUserUuid(storeCode, uuid, None))
    }
  }

  protected def findShopCartByCartItemId(
      cart: StoreCart,
      cartItemId: String): Option[StoreShopCart] = {
    cart.shopCarts.find { shopCart =>
      shopCart.cartItems.exists(_.id == cartItemId)
    }
  }

  protected def buildNewCartByReplaceCartItems(
      cart: StoreCart,
      shopCart: StoreShopCart,
      newCartItems: List[StoreCartItem]) = {
    val newShopCarts = cart.shopCarts.map { newShopCart =>
      if (shopCart.shopId == newShopCart.shopId)
        newShopCart.copy(cartItems = newCartItems)
      else newShopCart
    }
    cart.copy(shopCarts = deleteEmptyShopCarts(newShopCarts))
  }

  protected def removeAllUnsellableItemsFromCart(
      cart: StoreCart,
      country: Option[String],
      state: Option[String]): StoreCart = {
    val transactionalBloc = { implicit session: DBSession =>
      case class RemoveShopItemsResult(newCartItems: List[StoreCartItem],
                                       newCoupons: List[StoreCartCoupon],
                                       needInvalidation: Boolean)
      // On calcule la nouvelle liste des items et des coupons en mettant de coté les items qu'il faut supprimer
      val removeShopItemsResult = cart.shopCarts.map { shopCart =>
        // On séparer les cartItems à garder de ceux à supprimer
        val splitCartItems = shopCart.cartItems.span { cartItem =>
          val cartItemWithIndex = cartItem.copy(
            indexEs = Option(cartItem.indexEs).getOrElse(cart.storeCode))
          val productAndSku =
            ProductDao.getProductAndSku(cart.storeCode, cartItem.skuId)
          productAndSku.isDefined && checkProductAndSkuSalabality(
            productAndSku.get,
            country,
            state)
        }
        val newCoupons = shopCart.coupons.flatMap { coupon =>
          CouponDao.findByCode(cart.storeCode, coupon.code).map { c =>
            coupon
          }
        }
        shopCart.shopId -> RemoveShopItemsResult(splitCartItems._1,
                                                 newCoupons,
                                                 splitCartItems._2.nonEmpty)
      }.toMap

      // s'il y a au moins un item dans un shop cart à supprimer, on invalide le panier, sinon on le garde tel quel
      val invalidationResult =
        if (removeShopItemsResult.exists(_._2.needInvalidation))
          invalidateCart(cart)
        else CartWithChanges(cart, CartChanges())
      // On applique la nouvelle liste d'items et de coupons au panier
      val newShopCarts = invalidationResult.cart.shopCarts.map {
        shopCart: StoreShopCart =>
          removeShopItemsResult
            .get(shopCart.shopId)
            .map { removeShopItemsResult =>
              shopCart.copy(cartItems = removeShopItemsResult.newCartItems,
                            coupons = removeShopItemsResult.newCoupons)
            }
            .getOrElse(shopCart)
      }
      invalidationResult.copy(
        cart = invalidationResult.cart.copy(shopCarts = newShopCarts))
    }
    val successBloc = { cart: StoreCart =>
      StoreCartDao.save(cart)
      cart
    }
    runInTransaction(transactionalBloc, successBloc)
  }

  protected def clearCart[U](cart: StoreCart, success: StoreCart => U): U = {
    runInTransaction(
      { implicit session: DBSession =>
        val invalidateResult = invalidateCart(cart)
        val cancelResult = cancelCart(invalidateResult)

        cart.shopCarts.foreach { shopCart =>
          shopCart.coupons.foreach(coupon => {
            val optCoupon = CouponDao.findByCode(cart.storeCode, coupon.code)
            if (optCoupon.isDefined)
              couponHandler.releaseCoupon(cart.storeCode, optCoupon.get)
          })
        }

        cancelResult
      }, { cart: StoreCart =>
        success(cart)
      }
    )
  }

  /**
    * Annule la transaction courante et génère un nouveau uuid au panier
    */
  protected def cancelCart(cartAndChanges: CartWithChanges)(
      implicit session: DBSession): CartWithChanges = {
    val cart = cartAndChanges.cart
    cart.boCartUuid.map { boCartUuid =>
      val newBoCart = boCartHandler.find(cart.storeCode, boCartUuid).map {
        boCart =>
          // Mise à jour du statut
          val newBoCart = boCart.copy(status = TransactionStatus.FAILED)
          boCartHandler.update(newBoCart)
          newBoCart
      }
      val newChanges = cartAndChanges.changes.copy(boCartChange = newBoCart)
      val newCart = cart.copy(boCartUuid = None, transactionUuid = None)
      cartAndChanges.copy(cart = newCart, changes = newChanges)
    } getOrElse cartAndChanges
  }

  /**
    * Valide le panier en décrémentant les stocks
    */
  @throws[InsufficientStockException]
  protected def validateCart(cart: StoreCart)(
      implicit session: DBSession): CartWithChanges = {
    if (!cart.validate) {
      val changes = CartChanges(
        stockChanges = validateShopCarts(cart.shopCarts))
      CartWithChanges(cart.copy(validate = true,
                                validateUuid =
                                  Some(UUID.randomUUID().toString())),
                      changes)
    } else CartWithChanges(cart, CartChanges())
  }

  protected def validateShopCarts(shopCarts: List[StoreShopCart])(
      implicit session: DBSession): List[StockChange] = {
    if (shopCarts.isEmpty) Nil
    else
      validateCartItems(shopCarts.head.cartItems) ++ validateShopCarts(
        shopCarts.tail)
  }

  protected def validateCartItems(cartItems: List[StoreCartItem])(
      implicit session: DBSession): List[StockChange] = {
    if (cartItems.isEmpty) Nil
    else {
      val result = validateCartItems(cartItems.tail)
      val cartItem = cartItems.head
      validateCartItem(cartItem)
        .map { indexAndProduct =>
          indexAndProduct :: result
        }
        .getOrElse(result)
    }
  }

  protected def validateCartItem(cartItem: StoreCartItem)(
      implicit session: DBSession): Option[StockChange] = {
    val productAndSku =
      ProductDao.getProductAndSku(cartItem.indexEs, cartItem.skuId)
    productAndSku.flatMap { ps =>
      val product = ps._1
      val sku = ps._2
      stockHandler.decrementStock(cartItem.indexEs,
                                  product,
                                  sku,
                                  cartItem.quantity,
                                  cartItem.startDate)
    }
  }

  /**
    * Invalide le panier et libère les stocks si le panier était validé.
    */
  protected def invalidateCart(cart: StoreCart)(
      implicit session: DBSession): CartWithChanges = {
    if (cart.validate) {
      val changes = CartChanges(
        stockChanges = invalidateShopCarts(cart.shopCarts))
      CartWithChanges(cart.copy(validate = false, validateUuid = None), changes)
    } else CartWithChanges(cart, CartChanges())
  }

  protected def invalidateShopCarts(shopCarts: List[StoreShopCart])(
      implicit session: DBSession): List[StockChange] = {
    if (shopCarts.isEmpty) Nil
    else
      invalidateCartItems(shopCarts.head.cartItems) ++ invalidateShopCarts(
        shopCarts.tail)
  }

  protected def invalidateCartItems(cartItems: List[StoreCartItem])(
      implicit session: DBSession): List[StockChange] = {
    if (cartItems.isEmpty) Nil
    else {
      val result = invalidateCartItems(cartItems.tail)
      val cartItem = cartItems.head
      invalidateCartItem(cartItem)
        .map { indexAndProduct =>
          indexAndProduct :: result
        }
        .getOrElse(result)
    }
  }

  protected def invalidateCartItem(cartItem: StoreCartItem)(
      implicit session: DBSession): Option[StockChange] = {
    val productAndSku =
      ProductDao.getProductAndSku(cartItem.indexEs, cartItem.skuId)
    productAndSku.flatMap { ps =>
      val product = ps._1
      val sku = ps._2
      stockHandler.incrementStock(cartItem.indexEs,
                                  product,
                                  sku,
                                  cartItem.quantity,
                                  cartItem.startDate)
    }
  }

  def notifyChangesIntoES(storeCode: String,
                          changes: CartChanges,
                          refresh: Boolean = false): Unit = {
    val system = ActorSystemLocator()
    val stockActor = system.actorOf(Props[EsUpdateActor])
    changes.stockChanges.foreach { stockChange =>
      stockActor ! StockUpdateRequest(stockChange.esIndex,
                                      stockChange.product,
                                      stockChange.sku,
                                      stockChange.stock,
                                      stockChange.stockCalendar)
    }

    changes.saleChanges.foreach { saleChange =>
      salesHandler.fireUpdateEsSales(saleChange.esIndex,
                                     saleChange.product,
                                     saleChange.sku,
                                     saleChange.newNbProductSales,
                                     saleChange.newNbSkuSales)
    }

    changes.boCartChange.map { boCart =>
      boCartHandler.esSave(storeCode, boCart, refresh)
    }

    changes.deletedBOCart.foreach { boCart =>
      boCartHandler.esDelete(storeCode, boCart.uuid)
    }
  }

  /**
    * Fusionne le panier source avec le panier cible et renvoie le résultat
    * de la fusion
    */
  protected def mergeCarts(source: StoreCart, target: StoreCart): StoreCart = {
    // Ajoute les coupons de la source dans la target si les coupons n'existe pas déjà
    def mergeCoupons(source: List[StoreCartCoupon],
                     target: List[StoreCartCoupon]): List[StoreCartCoupon] = {
      if (source.isEmpty) target
      else {
        val coupon = source.head
        if (target.exists { c =>
              c.code == coupon.code
            }) mergeCoupons(source.tail, target)
        else mergeCoupons(source.tail, coupon :: target)
      }
    }
    // Ajoute les items de la source dans la target en fusionnant les quantités si nécessaire
    def mergeCartItems(source: List[StoreCartItem],
                       target: List[StoreCartItem]): List[StoreCartItem] = {
      if (source.isEmpty) target
      else mergeCartItems(source.tail, addOrMergeCartItem(target, source.head))
    }
    // Ajoute les shops de la source dans la target en les fusionnant si nésessaire
    def mergeShopCarts(source: List[StoreShopCart],
                       target: List[StoreShopCart]): List[StoreShopCart] = {
      if (source.isEmpty) target
      else {
        val shopCart = source.head
        val existShopCart = target.find(_.shopId == shopCart.shopId)
        val newTarget = existShopCart
          .map { existShopCart =>
            val newShopCart = existShopCart.copy(
              coupons = mergeCoupons(shopCart.coupons, existShopCart.coupons),
              cartItems =
                mergeCartItems(shopCart.cartItems, existShopCart.cartItems))
            addOrReplaceShopCart(target, newShopCart)
          }
          .getOrElse(shopCart :: target)
        mergeShopCarts(source.tail, newTarget)
      }
    }
    target.copy(shopCarts = mergeShopCarts(source.shopCarts, target.shopCarts))
  }

  /**
    * Ajoute le ShopCar s'il n'existe pas déjà ou remplace le ShopCart existant
    * @return
    */
  protected def addOrReplaceShopCart(
      source: List[StoreShopCart],
      newShopCart: StoreShopCart): List[StoreShopCart] = {
    val existShopCart = source.find(_.shopId == newShopCart.shopId)

    existShopCart
      .map { existShopCart =>
        source.map { shopCart =>
          if (shopCart == existShopCart) newShopCart else shopCart
        }
      }
      .getOrElse(newShopCart :: source)
  }

  protected def deleteEmptyShopCarts(
      source: List[StoreShopCart]): List[StoreShopCart] = {
    source.filterNot { shopCart =>
      shopCart.cartItems.isEmpty && shopCart.coupons.isEmpty
    }
  }

  /**
    * Ajoute un CartItem à une liste. Si un item existe déjà (sauf pour le SERVICE), la quantité
    * de l'item existant est modifié
    */
  protected def addOrMergeCartItem(
      source: List[StoreCartItem],
      cartItem: StoreCartItem): List[StoreCartItem] = {
    val existCartItem =
      if (cartItem.xtype == ProductType.SERVICE) None
      else
        source.find { ci: StoreCartItem =>
          ci.productId == cartItem.productId &&
          ci.skuId == cartItem.skuId &&
          isSameDateTime(ci, cartItem)
        }

    existCartItem
      .map { existCartItem =>
        source.map { item =>
          if (item == existCartItem)
            item.copy(quantity = item.quantity + cartItem.quantity)
          else item
        }
      }
      .getOrElse(cartItem :: source)
  }

  protected def isSameDateTime(cartItem1: StoreCartItem,
                               cartItem2: StoreCartItem): Boolean = {
    val now = DateTime.now()
    cartItem1.startDate
      .getOrElse(now)
      .withMillisOfSecond(0)
      .withSecondOfMinute(0)
      .isEqual(
        cartItem2.startDate
          .getOrElse(now)
          .withMillisOfSecond(0)
          .withSecondOfMinute(0)) &&
    cartItem1.endDate
      .getOrElse(now)
      .withMillisOfSecond(0)
      .withSecondOfMinute(0)
      .isEqual(
        cartItem2.endDate
          .getOrElse(now)
          .withMillisOfSecond(0)
          .withSecondOfMinute(0))
  }

  /**
    * Transforme le StoreCart en un CartVO en calculant les montants
    */
  protected def computeStoreCart(
      cart: StoreCart,
      countryCode: Option[String],
      stateCode: Option[String]): StoreCartWithPrices = {

    val computeResult = computePricesForShopCarts(cart.storeCode,
                                                  cart.shopCarts,
                                                  countryCode,
                                                  stateCode)

    new StoreCartWithPrices(
      cart,
      computeResult.shopCarts,
      computeResult.totalPrice,
      computeResult.totalEndPrice,
      computeResult.totalReduction,
      Math.max(0, computeResult.totalEndPrice - computeResult.totalReduction)
    )
  }

  case class ComputePricesForShopCartsResult(
      totalPrice: Long,
      totalEndPrice: Long,
      totalReduction: Long,
      shopCarts: List[StoreShopCartWithPrices])

  protected def computePricesForShopCarts(
      storeCode: String,
      shopCarts: List[StoreShopCart],
      countryCode: Option[String],
      stateCode: Option[String]): ComputePricesForShopCartsResult = {

    if (shopCarts.isEmpty) ComputePricesForShopCartsResult(0, 0, 0, Nil)
    else {
      val tailResult = computePricesForShopCarts(storeCode,
                                                 shopCarts.tail,
                                                 countryCode,
                                                 stateCode)
      val shopCart = shopCarts.head

      // On n'applique les réduction que sur le panier Mogobiz. Les autres paniers appliques les réductions du shop
      val coupons: List[CouponWithData] =
        if (shopCart.shopId == MogopayConstant.SHOP_MOGOBIZ)
          computeDataForCoupon(storeCode, shopCart)
        else Nil

      val cartItemsWithPrice = computePricesForCartItems(storeCode,
                                                         shopCart.cartItems,
                                                         countryCode,
                                                         stateCode)
      val reductionResult = applyReductionOnCartItems(storeCode,
                                                      shopCart,
                                                      cartItemsWithPrice,
                                                      coupons)
      val newShopCart = new StoreShopCartWithPrices(
        shopCart,
        reductionResult.cartItems,
        reductionResult.coupons,
        reductionResult.totalPrice,
        reductionResult.totalEndPrice,
        reductionResult.totalReduction,
        Math.max(0,
                 reductionResult.totalEndPrice - reductionResult.totalReduction)
      )

      ComputePricesForShopCartsResult(
        tailResult.totalPrice + newShopCart.totalPrice,
        tailResult.totalEndPrice + newShopCart.totalEndPrice,
        tailResult.totalReduction + newShopCart.totalReduction,
        newShopCart :: tailResult.shopCarts
      )
    }
  }

  protected def computeDataForCoupon(
      storeCode: String,
      shopCart: StoreShopCart): List[CouponWithData] = {
    // On transforme les coupon du panier + les promotions qui s'applique par rapport au contenu du panier
    shopCart.coupons.flatMap { coupon =>
      couponHandler.getWithData(storeCode, coupon)
    } ::: CouponDao.findPromotionsThatOnlyApplyOnCart(storeCode).map {
      promotion =>
        couponHandler.getWithData(promotion)
    }
  }

  case class ReductionResult(totalPrice: Long,
                             totalEndPrice: Long,
                             totalReduction: Long,
                             cartItems: List[StoreCartItemWithPrices],
                             coupons: List[CouponWithPrices])

  def applyReductionOnCartItems(
      storeCode: String,
      shopCart: StoreShopCart,
      cartItems: List[StoreCartItemWithPrices],
      coupons: List[CouponWithData]): ReductionResult = {

    if (cartItems.isEmpty) ReductionResult(0, 0, 0, Nil, coupons.map {
      new CouponWithPrices(_, 0)
    })
    else {
      val reductionResult =
        applyReductionOnCartItems(storeCode, shopCart, cartItems.tail, coupons)
      val cartItem = cartItems.head
      val maxReduction =
        findBestReductionForCartItem(storeCode, cartItem, coupons, cartItems)

      // On n'applique pas de réduction supérieur au prix du produit
      val reductionValue =
        Math.max(0,
                 Math.min(cartItem.totalEndPrice.getOrElse(cartItem.totalPrice),
                          maxReduction.reduction))

      // Si la réduction est associé à un coupon. Le réduction sera appliquée au panier sinon elle est appliquée
      // au produit
      val reductionToApplyOnItem = maxReduction.coupon
        .map { c =>
          0L
        }
        .getOrElse(reductionValue)
      val reductionToApplyOnCart = maxReduction.coupon
        .map { c =>
          reductionValue
        }
        .getOrElse(0L)
      val cartItemWithReduction = applyReductionOnCartItem(
        storeCode,
        shopCart,
        cartItem,
        reductionToApplyOnItem)

      // On met à jour le montant de la réduction correspondant au coupon appliqué (s'il existe)
      val newCoupons = maxReduction.coupon
        .map { coupon: CouponWithData =>
          reductionResult.coupons.map { couponWithPrices: CouponWithPrices =>
            if (couponWithPrices.id == coupon.id)
              new CouponWithPrices(couponWithPrices,
                                   couponWithPrices.reduction + reductionValue)
            else couponWithPrices
          }
        }
        .getOrElse(reductionResult.coupons)

      ReductionResult(
        reductionResult.totalPrice + cartItemWithReduction.saleTotalPrice,
        reductionResult.totalEndPrice + cartItemWithReduction.saleTotalEndPrice
          .getOrElse(cartItemWithReduction.saleTotalPrice),
        reductionResult.totalReduction + reductionToApplyOnCart,
        cartItemWithReduction :: reductionResult.cartItems,
        newCoupons
      )
    }
  }
  /*
    def computeCouponAsRenderCoupon(storeCode: String, coupons: List[CouponWithPrices]): List[Option[Coupon]] = {
      if (coupons.isEmpty) List()
      else couponHandler.transformAsRender(storeCode, coupons.head) :: computeCouponAsRenderCoupon(storeCode, coupons.tail)
    }
   */

  protected def computePricesForCartItems(
      storeCode: String,
      cartItems: List[StoreCartItem],
      countryCode: Option[String],
      stateCode: Option[String]): List[StoreCartItemWithPrices] = {
    cartItems.map { cartItem =>
      val product = ProductDao.get(storeCode, cartItem.productId).get
      val tax =
        taxRateHandler.findTaxRateByProduct(product, countryCode, stateCode)
      val endPrice = taxRateHandler.calculateEndPrice(cartItem.initPrice, tax)
      val totalPrice = cartItem.initPrice * cartItem.quantity
      val totalEndPrice = endPrice.map { p: Long =>
        p * cartItem.quantity
      }
      new StoreCartItemWithPrices(cartItem,
                                  cartItem.initPrice,
                                  endPrice,
                                  tax,
                                  totalPrice,
                                  totalEndPrice,
                                  cartItem.initSalePrice,
                                  endPrice,
                                  totalPrice,
                                  totalEndPrice,
                                  0)
    }
  }

  protected def applyReductionOnCartItem(
      storeCode: String,
      shopCart: StoreShopCart,
      cartItem: StoreCartItemWithPrices,
      reduction: Long): StoreCartItemWithPrices = {
    val product = ProductDao.get(storeCode, cartItem.productId).get
    val discounts =
      findSuggestionDiscount(storeCode, shopCart, cartItem.productId)
    val salePrice =
      computeDiscounts(Math.max(0, cartItem.price - reduction), discounts)
    val saleEndPrice = taxRateHandler.calculateEndPrice(salePrice, cartItem.tax)
    val saleTotalPrice = salePrice * cartItem.quantity
    val saleTotalEndPrice = saleEndPrice.map { _ * cartItem.quantity }
    new StoreCartItemWithPrices(
      cartItem,
      cartItem.price,
      cartItem.endPrice,
      cartItem.tax,
      cartItem.totalPrice,
      cartItem.totalEndPrice,
      salePrice,
      saleEndPrice,
      saleTotalPrice,
      saleTotalEndPrice,
      reduction
    )
  }

  case class MaxReduction(val reduction: Long,
                          val coupon: Option[CouponWithData])

  protected def findBestReductionForCartItem(
      storeCode: String,
      cartItem: StoreCartItemWithPrices,
      coupons: List[CouponWithData],
      cartItems: List[StoreCartItemWithPrices]): MaxReduction = {
    if (coupons.isEmpty) MaxReduction(cartItem.price - cartItem.salePrice, None)
    else {
      val coupon = coupons.head
      val price = couponHandler.computeCouponPriceForCartItem(storeCode,
                                                              coupon,
                                                              cartItem,
                                                              cartItems)
      val tailResult = findBestReductionForCartItem(storeCode,
                                                    cartItem,
                                                    coupons.tail,
                                                    cartItems)
      if (price > tailResult.reduction) MaxReduction(price, Some(coupon))
      else tailResult
    }
  }

  protected def findSuggestionDiscount(storeCode: String,
                                       shopCart: StoreShopCart,
                                       productId: Long): List[String] = {
    def extractDiscout(l: List[Mogobiz.Suggestion]): List[String] = {
      if (l.isEmpty) List()
      else {
        val s: Mogobiz.Suggestion = l.head
        val ci = shopCart.cartItems.find { ci =>
          ci.productId == s.parentId
        }
        if (ci.isDefined) s.discount :: extractDiscout(l.tail)
        else extractDiscout(l.tail)
      }
    }
    val suggestions: List[Mogobiz.Suggestion] =
      SuggestionDao.getSuggestionsbyId(storeCode, productId)
    extractDiscout(suggestions)
  }

  protected def computeDiscounts(price: Long, discounts: List[String]): Long = {
    if (discounts.isEmpty) Math.max(price, 0)
    else {
      val discountPrice = Math.max(
        price - couponHandler.computeDiscount(Some(discounts.head), price),
        0)
      Math.min(discountPrice, computeDiscounts(price, discounts.tail))
    }
  }

  protected def calculateCount(cart: StoreCartWithPrices): Int = {
    cart.shopCarts.map { calculateCount(_) }.sum
  }

  protected def calculateCount(shopCart: StoreShopCartWithPrices): Int = {
    shopCart.cartItems.map { _.quantity }.sum
  }

  protected def renderCart(cart: StoreCartWithPrices,
                           currency: Currency,
                           locale: Locale): Map[String, Any] = {
    val formatedPrice =
      rateService.formatLongPrice(cart.totalPrice, currency, locale)
    val formatedEndPrice =
      rateService.formatLongPrice(cart.totalEndPrice, currency, locale)
    val formatedReduction =
      rateService.formatLongPrice(cart.totalReduction, currency, locale)
    val formatedFinalPrice =
      rateService.formatLongPrice(cart.totalFinalPrice, currency, locale)

    Map(
      "validateUuid" -> cart.validateUuid.getOrElse(""),
      "count" -> calculateCount(cart),
      "shopCarts" -> cart.shopCarts.map(shopCart =>
        renderShopCart(cart.storeCode, shopCart, currency, locale)),
      "price" -> cart.totalPrice,
      "endPrice" -> cart.totalEndPrice,
      "reduction" -> cart.totalReduction,
      "finalPrice" -> cart.totalFinalPrice,
      "formatedPrice" -> formatedPrice,
      "formatedEndPrice" -> formatedEndPrice,
      "formatedReduction" -> formatedReduction,
      "formatedFinalPrice" -> formatedFinalPrice
    )
  }

  protected def renderShopCart(storeCode: String,
                               shopCart: StoreShopCartWithPrices,
                               currency: Currency,
                               locale: Locale): Map[String, Any] = {
    val formatedPrice =
      rateService.formatLongPrice(shopCart.totalPrice, currency, locale)
    val formatedEndPrice =
      rateService.formatLongPrice(shopCart.totalEndPrice, currency, locale)
    val formatedReduction =
      rateService.formatLongPrice(shopCart.totalReduction, currency, locale)
    val formatedFinalPrice =
      rateService.formatLongPrice(shopCart.totalFinalPrice, currency, locale)

    Map(
      "shopId" -> shopCart.shopId,
      "cartItems" -> shopCart.cartItems.map(item =>
        renderCartItem(item, currency, locale)),
      "coupons" -> shopCart.coupons
        .map(c => renderCoupon(storeCode, c, currency, locale))
        .flatten,
      "price" -> shopCart.totalPrice,
      "endPrice" -> shopCart.totalEndPrice,
      "reduction" -> shopCart.totalReduction,
      "finalPrice" -> shopCart.totalFinalPrice,
      "formatedPrice" -> formatedPrice,
      "formatedEndPrice" -> formatedEndPrice,
      "formatedReduction" -> formatedReduction,
      "formatedFinalPrice" -> formatedFinalPrice
    )
  }

  protected def transformCartForCartPay(
      compagnyAddress: Option[CompanyAddress],
      cart: StoreCartWithPrices,
      rate: Currency,
      shippingRulePrice: Option[Long]): CartPay = {
    val cartRate = CartRate(rate.code,
                            rate.numericCode,
                            rate.rate,
                            rate.currencyFractionDigits)

    val shopCartsPay = cart.shopCarts.map { shopCart =>
      val cartItemsPay = shopCart.cartItems.map { cartItem =>
        val registeredCartItemsPay = cartItem.registeredCartItems.map { rci =>
          RegisteredCartItemPay(rci.id,
                                rci.email,
                                rci.firstname,
                                rci.lastname,
                                rci.phone,
                                rci.birthdate,
                                rci.qrCodeContent,
                                Map())
        }
        val shippingPay = cartItem.shipping.map { shipping =>
          ShippingPay(
            shipping.weight,
            shipping.weightUnit.toString(),
            shipping.width,
            shipping.height,
            shipping.depth,
            shipping.linearUnit.toString(),
            shipping.amount,
            shipping.free,
            Map()
          )
        }
        val customCartItem = Map(
          "productId" -> cartItem.productId,
          "productName" -> cartItem.productName,
          "xtype" -> cartItem.xtype.toString(),
          "calendarType" -> cartItem.calendarType.toString(),
          "skuId" -> cartItem.skuId,
          "skuName" -> cartItem.skuName,
          "startDate" -> cartItem.startDate,
          "endDate" -> cartItem.endDate
        )
        val name = s"${cartItem.productName} ${cartItem.skuName}"
        val endPrice = cartItem.endPrice.getOrElse(cartItem.price)
        val totalEndPrice =
          cartItem.totalEndPrice.getOrElse(cartItem.totalPrice)
        val saleEndPrice = cartItem.saleEndPrice.getOrElse(cartItem.salePrice)
        val saleTotalEndPrice =
          cartItem.saleTotalEndPrice.getOrElse(cartItem.saleTotalPrice)
        CartItemPay(
          cartItem.id,
          name,
          cartItem.productPicture,
          cartItem.productUrl,
          cartItem.quantity,
          cartItem.price,
          endPrice,
          cartItem.tax.getOrElse(0),
          endPrice - cartItem.price,
          cartItem.totalPrice,
          totalEndPrice,
          totalEndPrice - cartItem.totalPrice,
          cartItem.salePrice,
          saleEndPrice,
          saleEndPrice - cartItem.salePrice,
          cartItem.saleTotalPrice,
          saleTotalEndPrice,
          saleTotalEndPrice - cartItem.saleTotalPrice,
          registeredCartItemsPay,
          shippingPay,
          cartItem.downloadableLink.getOrElse(""),
          cartItem.externalCode,
          customCartItem
        )
      }

      val couponsPay = shopCart.coupons.flatMap { coupon =>
        couponHandler.transformAsRender(cart.storeCode, coupon).map { coupon =>
          val customCoupon =
            Map("name" -> coupon.name, "active" -> coupon.active)
          new CouponPay(coupon.code,
                        coupon.startDate,
                        coupon.endDate,
                        coupon.price,
                        customCoupon)
        }
      }

      ShopCartPay(
        shopCart.shopId,
        cartRate,
        shopCart.totalPrice,
        shopCart.totalEndPrice,
        shopCart.totalEndPrice - shopCart.totalPrice,
        shopCart.totalReduction,
        shopCart.totalFinalPrice,
        cartItemsPay,
        couponsPay,
        Map()
      )
    }

    new CartPay(
      calculateCount(cart),
      cartRate,
      cart.totalPrice,
      cart.totalEndPrice,
      cart.totalEndPrice - cart.totalPrice,
      cart.totalReduction,
      cart.totalFinalPrice,
      shippingRulePrice,
      shopCartsPay,
      Map(),
      compagnyAddress
    )
  }

  /**
    * Idem que renderCart à quelques différences près d'où la dupplication de code
    */
  protected def renderTransactionCart(cart: StoreCartWithPrices,
                                      rate: Currency,
                                      locale: Locale): Map[String, Any] = {
    val mogobizShopCart = cart
      .findShopCart(MogopayConstant.SHOP_MOGOBIZ)
      .getOrElse(
        StoreShopCartWithPrices(MogopayConstant.SHOP_MOGOBIZ,
                                None,
                                Nil,
                                Nil,
                                0,
                                0,
                                0,
                                0))

    val price = rateService.calculateAmount(mogobizShopCart.totalPrice, rate)
    val endPrice =
      rateService.calculateAmount(mogobizShopCart.totalEndPrice, rate)
    val reduction =
      rateService.calculateAmount(mogobizShopCart.totalReduction, rate)
    val finalPrice =
      rateService.calculateAmount(mogobizShopCart.totalFinalPrice, rate)

    Map(
      "boCartUuid" -> cart.boCartUuid.getOrElse(""),
      "transactionUuid" -> cart.transactionUuid.getOrElse(""),
      "count" -> calculateCount(mogobizShopCart),
      "cartItemVOs" -> mogobizShopCart.cartItems.map(item =>
        renderTransactionCartItem(item, rate, locale)),
      "coupons" -> mogobizShopCart.coupons.flatMap(c =>
        renderTransactionCoupon(cart.storeCode, c, rate, locale)),
      "price" -> price,
      "endPrice" -> endPrice,
      "reduction" -> reduction,
      "finalPrice" -> finalPrice,
      "formatedPrice" -> rateService.formatLongPrice(price, rate, locale),
      "formatedEndPrice" -> rateService.formatLongPrice(endPrice, rate, locale),
      "formatedReduction" -> rateService.formatLongPrice(reduction,
                                                         rate,
                                                         locale),
      "formatedFinalPrice" -> rateService.formatLongPrice(finalPrice,
                                                          rate,
                                                          locale),
      "date" -> new Date().getTime
    )
  }

  /**
    * Renvoie un coupon JSONisé augmenté par un calcul de prix formaté
    */
  protected def renderCoupon(storeCode: String,
                             couponWithData: CouponWithPrices,
                             currency: Currency,
                             locale: Locale) = {
    couponHandler.transformAsRender(storeCode, couponWithData).map { coupon =>
      implicit def json4sFormats: Formats =
        DefaultFormats ++ JodaTimeSerializers.all + FieldSerializer[Coupon]()
      val jsonCoupon = parse(write(coupon))

      //code from renderPriceCoupon
      val formatedPrice =
        rateService.formatPrice(coupon.price, currency, locale)
      val additionalsData = parse(write(Map("formatedPrice" -> formatedPrice)))

      jsonCoupon merge additionalsData
    }
  }

  protected def renderTransactionCoupon(storeCode: String,
                                        couponWithData: CouponWithPrices,
                                        rate: Currency,
                                        locale: Locale) = {
    couponHandler.transformAsRender(storeCode, couponWithData).map { coupon =>
      implicit def json4sFormats: Formats =
        DefaultFormats ++ JodaTimeSerializers.all + FieldSerializer[Coupon]()
      val jsonCoupon = parse(write(coupon))

      val price = rateService.calculateAmount(coupon.price, rate)
      val updatedData = parse(
        write(Map(
          "price" -> price,
          "formatedPrice" -> rateService.formatPrice(coupon.price, rate, locale)
        )))

      jsonCoupon merge updatedData
    }
  }

  /**
    * Renvoie un cartItem JSONinsé augmenté par le calcul des prix formattés
    */
  protected def renderCartItem(item: StoreCartItemWithPrices,
                               currency: Currency,
                               locale: Locale) = {
    import org.json4s.jackson.JsonMethods._
    import org.json4s.jackson.Serialization.write
    implicit def json4sFormats: Formats =
      DefaultFormats ++ JodaTimeSerializers.all + FieldSerializer[
        StoreCartItem]() + new org.json4s.ext.EnumNameSerializer(
        ProductCalendar)
    val jsonItem = parse(write(item))

    val additionalsData = parse(
      write(
        Map(
          "price" -> item.price,
          "endPrice" -> item.endPrice,
          "tax" -> item.tax,
          "totalPrice" -> item.totalPrice,
          "totalEndPrice" -> item.totalEndPrice,
          "salePrice" -> item.salePrice,
          "saleEndPrice" -> item.saleEndPrice,
          "saleTotalPrice" -> item.saleTotalPrice,
          "saleTotalEndPrice" -> item.saleTotalEndPrice,
          "formatedPrice" -> rateService
            .formatPrice(item.price, currency, locale),
          "formatedSalePrice" -> rateService
            .formatPrice(item.salePrice, currency, locale),
          "formatedEndPrice" -> item.endPrice.map {
            rateService.formatPrice(_, currency, locale)
          },
          "formatedSaleEndPrice" -> item.saleEndPrice.map {
            rateService.formatPrice(_, currency, locale)
          },
          "formatedTotalPrice" -> rateService
            .formatPrice(item.totalPrice, currency, locale),
          "formatedSaleTotalPrice" -> rateService
            .formatPrice(item.saleTotalPrice, currency, locale),
          "formatedTotalEndPrice" -> item.totalEndPrice.map {
            rateService.formatPrice(_, currency, locale)
          },
          "formatedSaleTotalEndPrice" -> item.saleTotalEndPrice.map {
            rateService.formatPrice(_, currency, locale)
          }
        )))

    jsonItem merge additionalsData
  }

  protected def renderTransactionCartItem(item: StoreCartItemWithPrices,
                                          rate: Currency,
                                          locale: Locale) = {
    import org.json4s.jackson.JsonMethods._
    import org.json4s.jackson.Serialization.write
    val jsonItem = parse(write(item))

    val price = rateService.calculateAmount(item.price, rate)
    val salePrice = rateService.calculateAmount(item.salePrice, rate)
    val endPrice =
      rateService.calculateAmount(item.endPrice.getOrElse(item.price), rate)
    val saleEndPrice = rateService.calculateAmount(
      item.saleEndPrice.getOrElse(item.salePrice),
      rate)
    val totalPrice = rateService.calculateAmount(item.totalPrice, rate)
    val saleTotalPrice = rateService.calculateAmount(item.saleTotalPrice, rate)
    val totalEndPrice = rateService.calculateAmount(
      item.totalEndPrice.getOrElse(item.totalPrice),
      rate)
    val saleTotalEndPrice = rateService.calculateAmount(
      item.saleTotalEndPrice.getOrElse(item.saleTotalPrice),
      rate)

    val updatedData = parse(
      write(
        Map(
          "price" -> price,
          "salePrice" -> salePrice,
          "endPrice" -> endPrice,
          "saleEndPrice" -> saleEndPrice,
          "tax" -> item.tax,
          "totalPrice" -> totalPrice,
          "saleTotalPrice" -> saleTotalPrice,
          "totalEndPrice" -> totalEndPrice,
          "saleTotalEndPrice" -> saleTotalEndPrice,
          "formatedPrice" -> rateService
            .formatLongPrice(item.price, rate, locale),
          "formatedSalePrice" -> rateService
            .formatLongPrice(item.salePrice, rate, locale),
          "formatedEndPrice" -> rateService
            .formatLongPrice(item.endPrice.getOrElse(item.price), rate, locale),
          "formatedSaleEndPrice" -> rateService
            .formatLongPrice(item.saleEndPrice.getOrElse(item.salePrice),
                             rate,
                             locale),
          "formatedTotalPrice" -> rateService
            .formatLongPrice(item.totalPrice, rate, locale),
          "formatedSaleTotalPrice" -> rateService
            .formatLongPrice(item.saleTotalPrice, rate, locale),
          "formatedTotalEndPrice" -> rateService
            .formatLongPrice(item.totalEndPrice.getOrElse(item.totalPrice),
                             rate,
                             locale),
          "formatedSaleTotalEndPrice" -> rateService
            .formatLongPrice(
              item.saleTotalEndPrice.getOrElse(item.saleTotalPrice),
              rate,
              locale)
        )))

    jsonItem merge updatedData
  }

  /**
    * Renvoie tous les champs prix calculé sur le panier
    */
  protected def renderPriceCart(cart: CartPay,
                                currency: Currency,
                                locale: Locale) = {
    val formatedPrice =
      rateService.formatLongPrice(cart.price, currency, locale)
    val formatedEndPrice =
      rateService.formatLongPrice(cart.endPrice, currency, locale)
    val formatedReduction =
      rateService.formatLongPrice(cart.reduction, currency, locale)
    val formatedFinalPrice =
      rateService.formatLongPrice(cart.finalPrice, currency, locale)

    Map(
      "price" -> cart.price,
      "endPrice" -> cart.endPrice,
      "reduction" -> cart.reduction,
      "finalPrice" -> cart.finalPrice,
      "formatedPrice" -> formatedPrice,
      "formatedEndPrice" -> formatedEndPrice,
      "formatedReduction" -> formatedReduction,
      "formatedFinalPrice" -> formatedFinalPrice
    )
  }

  protected def renderTransactionPriceCart(cart: CartPay,
                                           rate: Currency,
                                           locale: Locale) = {
    val price = rateService.calculateAmount(cart.price, rate)
    val endPrice = rateService.calculateAmount(cart.endPrice, rate)
    val reduction = rateService.calculateAmount(cart.reduction, rate)
    val finalPrice = rateService.calculateAmount(cart.finalPrice, rate)

    Map(
      "price" -> price,
      "endPrice" -> endPrice,
      "reduction" -> reduction,
      "finalPrice" -> finalPrice,
      "formatedPrice" -> rateService.formatLongPrice(cart.price, rate, locale),
      "formatedEndPrice" -> rateService.formatLongPrice(cart.endPrice,
                                                        rate,
                                                        locale),
      "formatedReduction" -> rateService.formatLongPrice(cart.reduction,
                                                         rate,
                                                         locale),
      "formatedFinalPrice" -> rateService.formatLongPrice(cart.finalPrice,
                                                          rate,
                                                          locale)
    )
  }

  def exportBOCartIntoES(storeCode: String,
                         boCart: BOCart,
                         refresh: Boolean = false)(implicit session: DBSession =
                                                     AutoSession): Unit = {
    boCartHandler.esSave(storeCode, boCart, refresh)
  }
}

object StoreCartDao {

  import com.sksamuel.elastic4s.http.ElasticDsl._

  protected val ES_DOCUMENT = "StoreCart"

  protected def buildIndex(storeCode: String) = s"${storeCode}_cart"

  def findByDataUuidAndUserUuid(
      storeCode: String,
      dataUuid: String,
      userUuid: Option[Mogopay.Document]): Option[StoreCart] = {
    val uuid = dataUuid + "--" + userUuid.getOrElse("None")
    EsClient.load[StoreCart](buildIndex(storeCode), uuid, ES_DOCUMENT)
  }

  def save(entity: RunCart): Boolean = {
    val newEntity =
      if (entity.isInstanceOf[StoreCart]) entity.asInstanceOf[StoreCart]
      else createStoreCart(entity)

    val upsert = true
    val refresh = false
    EsClient.update[RunCart](
      buildIndex(newEntity.storeCode),
      newEntity.copy(
        expireDate = DateTime.now.plusSeconds(60 * Settings.Cart.Lifetime)),
      ES_DOCUMENT,
      upsert,
      refresh)
  }

  def delete(cart: RunCart): Unit = {
    EsClient.delete[RunCart](buildIndex(cart.storeCode),
                             cart.uuid,
                             ES_DOCUMENT,
                             false)
  }

  def getExpired(index: String, querySize: Int): List[StoreCart] = {
    val req = search(index -> ES_DOCUMENT) query boolQuery()
      .must(rangeQuery("expireDate") lt "now") from 0 size querySize
    EsClient.searchAll[StoreCart](req).toList
  }

  private def createStoreCart(source: RunCart): StoreCart =
    StoreCart(
      source.storeCode,
      source.dataUuid,
      source.userUuid,
      source.boCartUuid,
      source.transactionUuid,
      source.externalOrderId,
      source.validate,
      source.validateUuid,
      source.shopCarts.map { createStoreShopCart(_) },
      source.expireDate,
      source.dateCreated,
      source.lastUpdated,
      source.countryCode,
      source.stateCode,
      source.rate
    )

  private def createStoreShopCart(source: RunShopCart): StoreShopCart =
    StoreShopCart(source.shopId,
                  source.shopTransactionUuid,
                  source.cartItems.map { createStoreCartItem(_) },
                  source.coupons.map { createCartCoupon(_) })

  private def createStoreCartItem(source: RunCartItem): StoreCartItem =
    StoreCartItem(
      source.indexEs,
      source.id,
      source.productId,
      source.productName,
      source.productPicture,
      source.productUrl,
      source.xtype,
      source.calendarType,
      source.skuId,
      source.skuName,
      source.quantity,
      source.initPrice,
      source.initSalePrice,
      source.startDate,
      source.endDate,
      source.registeredCartItems,
      source.shipping,
      source.downloadableLink,
      source.externalCode
    )

  private def createCartCoupon(source: CartCoupon): StoreCartCoupon =
    StoreCartCoupon(source.id, source.code)
}
