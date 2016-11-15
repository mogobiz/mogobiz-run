/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import java.io.ByteArrayOutputStream
import java.util.{Date, Locale, UUID}

import akka.actor.Props
import com.mogobiz.es.EsClient
import com.mogobiz.json.JacksonConverter
import com.mogobiz.pay.common.{Cart => CartPay, CartItem => CartItemPay, Coupon => CouponPay, RegisteredCartItem => RegisteredCartItemPay, Shipping => ShippingPay, _}
import com.mogobiz.pay.config.MogopayHandlers
import com.mogobiz.pay.model.Mogopay
import com.mogobiz.pay.model.{SelectShippingCart, ShippingCart, ShippingData, AccountAddress}
import com.mogobiz.run.actors.EsUpdateActor
import com.mogobiz.run.actors.EsUpdateActor.{StockUpdateRequest, ProductStockAvailabilityUpdateRequest}
import com.mogobiz.run.config.MogobizHandlers.handlers._
import com.mogobiz.run.config.Settings
import com.mogobiz.run.dashboard.Dashboard
import com.mogobiz.run.es._
import com.mogobiz.run.exceptions._
import com.mogobiz.run.implicits.Json4sProtocol
import com.mogobiz.run.learning.{CartRegistration, UserActionRegistration}
import com.mogobiz.run.model.ES.{BOCartEx, BOCartItemEx, BOCart => BOCartES, BOCartItem => BOCartItemES, BODelivery => BODeliveryES, BOProduct => BOProductES, BORegisteredCartItem => BORegisteredCartItemES, BOReturn => BOReturnES, BOReturnedItem => BOReturnedItemES}
import com.mogobiz.run.model.Learning.UserAction
import com.mogobiz.run.model.Mogobiz._
import com.mogobiz.run.model.Render.{Coupon, RegisteredCartItem}
import com.mogobiz.run.model.RequestParameters._
import com.mogobiz.run.model._
import com.mogobiz.run.services.RateBoService
import com.mogobiz.run.utils.Utils
import com.mogobiz.system.ActorSystemLocator
import com.mogobiz.utils.{QRCodeUtils, SymmetricCrypt}
import com.sksamuel.elastic4s.ElasticDsl.{get, tuple2indexestypes}
import com.sun.org.apache.xml.internal.security.utils.Base64
import com.typesafe.scalalogging.StrictLogging
import org.elasticsearch.common.bytes.ChannelBufferBytesReference
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.json4s.ext.JodaTimeSerializers
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization._
import org.json4s._
import scalikejdbc._
import scalikejdbc.TxBoundary.Try._

import scala.util.{Success, Failure, Try}

class CartHandler extends StrictLogging {
  val rateService = RateBoService

  def runInTransaction[U](call: DBSession => CartWithChanges, success: StoreCart => U): U = {
    com.mogobiz.utils.GlobalUtil.runInTransaction(call, { cartAndChanges: CartWithChanges =>
      val cart = cartAndChanges.cart
      notifyChangesIntoES(cart.storeCode, cartAndChanges.changes)
      success(cart)
    })
  }

  def runInTransaction[U](call: => DBSession => CartWithPricesAndChanges, success: StoreCartWithPrice => U): U = {
    com.mogobiz.utils.GlobalUtil.runInTransaction(call, { cartAndChanges: CartWithPricesAndChanges =>
      val cart = cartAndChanges.cart
      notifyChangesIntoES(cart.storeCart.storeCode, cartAndChanges.changes)
      success(cart)
    })
  }

  /**
    * Permet de récupérer le contenu du panier<br/>
    * Si le panier n'existe pas, il est créé<br/>
    *
    * @param storeCode
    * @param uuid
    * @param params
    * @param accountId
    * @return
    */
  def queryCartInit(storeCode: String,
                    uuid: String,
                    params: CartParameters,
                    accountId: Option[Mogopay.Document]): Map[String, Any] = {
    val locale   = buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val cart = initCart(storeCode, uuid, accountId, true, params.country, params.state)

    val computeCart = computeStoreCart(cart, params.country, params.state)
    renderCart(computeCart, currency, locale)
  }

  /**
    * Vide le contenu du panier
    *
    * @param storeCode
    * @param uuid
    * @param params
    * @param accountId
    * @return
    */
  def queryCartClear(storeCode: String,
                     uuid: String,
                     params: CartParameters,
                     accountId: Option[Mogopay.Document]): Map[String, Any] = {
    val locale   = buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val cart = initCart(storeCode, uuid, accountId, true, params.country, params.state)

    val updatedCart = clearCart(cart, { cart: StoreCart =>
      val updatedCart = new StoreCart(storeCode = cart.storeCode, dataUuid = cart.dataUuid, userUuid = cart.userUuid)
      StoreCartDao.save(updatedCart)
      updatedCart
    })

    val computeCart = computeStoreCart(updatedCart, params.country, params.state)
    renderCart(computeCart, currency, locale)
  }

  /**
    * Ajoute un item au panier
    *
    * @param storeCode
    * @param uuid
    * @param params
    * @param cmd
    * @param accountId
    * @return
    */
  @throws[NotFoundException]
  @throws[MinMaxQuantityException]
  @throws[DateIsNullException]
  @throws[UnsaleableDateException]
  @throws[NotEnoughRegisteredCartItemException]
  def queryCartItemAdd(storeCode: String,
                       uuid: String,
                       params: CartParameters,
                       cmd: AddCartItemRequest,
                       accountId: Option[Mogopay.Document]): Map[String, Any] = {
    val locale   = buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val cart = initCart(storeCode, uuid, accountId, true, params.country, params.state)

    val transactionalBloc = { implicit session: DBSession =>
      val productAndSku = ProductDao.getProductAndSku(cart.storeCode, cmd.skuId)
      if (productAndSku.isEmpty) throw new NotFoundException("unknown sku")

      if (!checkProductAndSkuSalabality(productAndSku.get, params.country, params.state))
        throw new UnsaleableProductException()

      val product      = productAndSku.get._1
      val sku          = productAndSku.get._2
      val startEndDate = Utils.verifyAndExtractStartEndDate(product, sku, cmd.dateTime)
      val startDate    = if (startEndDate.isDefined) Some(startEndDate.get._1) else None
      val endDate      = if (startEndDate.isDefined) Some(startEndDate.get._2) else None

      if (sku.minOrder > cmd.quantity || (sku.maxOrder < cmd.quantity && sku.maxOrder > -1))
        throw new MinMaxQuantityException(sku.minOrder, sku.maxOrder)

      if (!cmd.dateTime.isDefined && !ProductCalendar.NO_DATE.equals(product.calendarType))
        throw new DateIsNullException()
      else if (cmd.dateTime.isDefined && startEndDate == None)
        throw new UnsaleableDateException()

      if (product.xtype == ProductType.SERVICE && cmd.registeredCartItems.size != cmd.quantity)
        throw new NotEnoughRegisteredCartItemException()

      if (!stockHandler.checkStock(cart.storeCode, product, sku, cmd.quantity, startDate)) {
        throw new InsufficientStockCartItemException()
      }

      val newCartItemId = cmd.uuid.getOrElse(UUID.randomUUID().toString);
      val registeredItems = cmd.registeredCartItems.map { item =>
        new RegisteredCartItem(
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
      val indexEs   = EsClient.getUniqueIndexByAlias(storeCode).getOrElse(storeCode)
      val cartItem = StoreCartItem(indexEs,
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
                                   None,
                                   sku.getExternalCode)

      val invalidationResult = invalidateCart(cart)
      invalidationResult.copy(cart = addCartItemIntoCart(invalidationResult.cart, cartItem))
    }
    val updatedCart = runInTransaction(transactionalBloc, { cart: StoreCart =>
      StoreCartDao.save(cart)
      cart
    })

    val computeCart = computeStoreCart(updatedCart, params.country, params.state)
    renderCart(computeCart, currency, locale)
  }

  protected def checkProductAndSkuSalabality(productAndSku: (Mogobiz.Product, Mogobiz.Sku),
                                             country: Option[String],
                                             state: Option[String]): Boolean = {
    val now          = DateTime.now().toLocalDate
    val sku          = productAndSku._2
    val skuStartDate = sku.startDate.getOrElse(DateTime.now()).toLocalDate
    val skuEndDate   = sku.stopDate.getOrElse(DateTime.now()).toLocalDate

    !skuStartDate.isAfter(now) && !skuEndDate.isBefore(now) && (state.isEmpty || taxRateHandler
          .findTaxRateByProduct(productAndSku._1, country, state)
          .isDefined)
  }

  /**
    * Met à jour la quantité d'un item du panier
    *
    * @param storeCode
    * @param uuid
    * @param cartItemId
    * @param params
    * @param cmd
    * @param accountId
    * @return
    */
  @throws[InsufficientStockCartItemException]
  @throws[NotFoundException]
  def queryCartItemUpdate(storeCode: String,
                          uuid: String,
                          cartItemId: String,
                          params: CartParameters,
                          cmd: UpdateCartItemRequest,
                          accountId: Option[Mogopay.Document]): Map[String, Any] = {
    val locale   = buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val cart = initCart(storeCode, uuid, accountId, true, params.country, params.state)

    cart.cartItems.find { item =>
      item.id == cartItemId
    }.map { existCartItem =>
      val updatedCart = if (ProductType.SERVICE != existCartItem.xtype && existCartItem.quantity != cmd.quantity) {
        val productAndSku = ProductDao.getProductAndSku(cart.storeCode, existCartItem.skuId)
        val product       = productAndSku.get._1
        val sku           = productAndSku.get._2

        runInTransaction({ implicit session: DBSession =>
          if (!stockHandler.checkStock(cart.storeCode, product, sku, cmd.quantity, existCartItem.startDate)) {
            throw new InsufficientStockCartItemException()
          }

          val invalidationResult = invalidateCart(cart)
          val newCartItems = cart.cartItems.map { item =>
            if (item == existCartItem) item.copy(quantity = cmd.quantity) else item
          }
          invalidationResult.copy(cart = invalidationResult.cart.copy(cartItems = newCartItems))
        }, { cart: StoreCart =>
          StoreCartDao.save(cart)
          cart
        })
      } else {
        logger.debug("silent op")
        cart
      }
      val computeCart = computeStoreCart(updatedCart, params.country, params.state)
      renderCart(computeCart, currency, locale)
    }.getOrElse(throw new NotFoundException(""))
  }

  /**
    * Supprime un item du panier
    *
    * @param storeCode
    * @param uuid
    * @param cartItemId
    * @param params
    * @param accountId
    * @return
    */
  def queryCartItemRemove(storeCode: String,
                          uuid: String,
                          cartItemId: String,
                          params: CartParameters,
                          accountId: Option[Mogopay.Document]): Map[String, Any] = {
    val locale   = buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val cart = initCart(storeCode, uuid, accountId, true, params.country, params.state)

    val updatedCart = runInTransaction({ implicit session: DBSession =>
      val invalidationResult = invalidateCart(cart)
      val newCartItems = cart.cartItems.filterNot { item =>
        item.id == cartItemId
      }
      invalidationResult.copy(cart = invalidationResult.cart.copy(cartItems = newCartItems))
    }, { cart: StoreCart =>
      StoreCartDao.save(cart)
      cart
    })

    val computeCart = computeStoreCart(updatedCart, params.country, params.state)
    renderCart(computeCart, currency, locale)
  }

  /**
    * Ajoute un coupon au panier
    *
    * @param storeCode
    * @param uuid
    * @param couponCode
    * @param params
    * @param accountId
    * @return
    */
  @throws[DuplicateException]
  @throws[InsufficientStockCouponException]
  def queryCartCouponAdd(storeCode: String,
                         uuid: String,
                         couponCode: String,
                         params: CouponParameters,
                         accountId: Option[Mogopay.Document]): Map[String, Any] = {
    val locale   = buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val cart = initCart(storeCode, uuid, accountId, true, params.country, params.state)

    CouponDao
      .findByCode(cart.storeCode, couponCode)
      .map { coupon =>
        if (!coupon.anonymous) {
          val updatedCart = runInTransaction({ implicit session: DBSession =>
            if (cart.coupons.exists { c =>
                  couponCode == c.code
                }) {
              throw new DuplicateException("")
            } else if (!couponHandler.consumeCoupon(cart.storeCode, coupon)) {
              throw new InsufficientStockCouponException()
            } else {
              val invalidationResult = invalidateCart(cart)
              val newCoupons         = StoreCoupon(coupon.id, coupon.code) :: cart.coupons
              invalidationResult.copy(cart = invalidationResult.cart.copy(coupons = newCoupons))
            }
          }, { cart: StoreCart =>
            StoreCartDao.save(cart)
            cart
          })

          val computeCart = computeStoreCart(updatedCart, params.country, params.state)
          renderCart(computeCart, currency, locale)
        } else {
          throw new NotFoundException("")
        }
      }
      .getOrElse(throw new NotFoundException(""))
  }

  /**
    * Supprime un coupon du panier
    *
    * @param storeCode
    * @param uuid
    * @param couponCode
    * @param params
    * @param accountId
    * @return
    */
  @throws[NotFoundException]
  def queryCartCouponDelete(storeCode: String,
                            uuid: String,
                            couponCode: String,
                            params: CouponParameters,
                            accountId: Option[Mogopay.Document]): Map[String, Any] = {
    val locale   = buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val cart = initCart(storeCode, uuid, accountId, true, params.country, params.state)

    CouponDao
      .findByCode(cart.storeCode, couponCode)
      .map { coupon =>
        cart.coupons.find { c =>
          couponCode == c.code
        }.map { existCoupon =>
          val updatedCart = runInTransaction({ implicit session: DBSession =>
            couponHandler.releaseCoupon(cart.storeCode, coupon)
            val invalidationResult = invalidateCart(cart)
            val newCoupons = cart.coupons.filterNot { c =>
              c.code == existCoupon.code
            }
            invalidationResult.copy(cart = invalidationResult.cart.copy(coupons = newCoupons))
          }, { cart: StoreCart =>
            StoreCartDao.save(cart)
            cart
          })

          val computeCart = computeStoreCart(updatedCart, params.country, params.state)
          renderCart(computeCart, currency, locale)
        }.getOrElse(throw new NotFoundException(""))
      }
      .getOrElse(throw new NotFoundException(""))
  }

  /**
    * Prépare le panier pour le paiement
    *
    * @param storeCode
    * @param uuid
    * @param params
    * @param accountId
    * @return
    */
  @throws[InsufficientStockException]
  def queryCartPaymentPrepare(storeCode: String,
                              uuid: String,
                              params: PrepareTransactionParameters,
                              accountId: Mogopay.Document): Map[String, Any] = {
    val locale   = buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val cart = initCart(storeCode, uuid, Some(accountId), true, params.country, params.state)

    val company = CompanyDao.findByCode(cart.storeCode)

    val transactionalBloc = { implicit session: DBSession =>
      val validationResult = validateCart(
          cart.copy(countryCode = params.country, stateCode = params.state, rate = Some(currency)))

      val deleteResult         = deletePendingBOCart(validationResult)
      val cartWithPrice        = computeStoreCart(deleteResult.cart, params.country, params.state)
      val cartPriceWithChanges = CartWithPricesAndChanges(cartWithPrice, deleteResult.changes)

      createBOCart(cartPriceWithChanges, currency, params.buyer, company.get, params.shippingAddress)
    }

    val updatedCartPrice = runInTransaction(transactionalBloc, { cartPrice: StoreCartWithPrice =>
      StoreCartDao.save(cartPrice.storeCart)
      cartPrice
    })
    val renderedCart = renderTransactionCart(updatedCartPrice, currency, locale)
    Map(
        "amount"           -> rateService.calculateAmount(updatedCartPrice.totalFinalPrice, currency),
        "currencyCode"     -> currency.code,
        "currencyRate"     -> currency.rate.doubleValue(),
        "transactionExtra" -> renderedCart
    )
  }

  def getCartForPay(storeCode: String, uuid: String, accountId: Option[String], currencyCode: String): CartPay = {
    val cart = initCart(storeCode, uuid, accountId, false, None, None)
    val currency = queryCurrency(storeCode, Some(currencyCode))

    // Calcul des données du panier
    val cartWithPrice = computeStoreCart(cart, cart.countryCode, cart.stateCode)

    val compagny = CompanyDao.findByCode(storeCode)
    val shipFromAddressOpt = compagny.map {
      _.shipFrom
    }.getOrElse(None)
    val companyAddress = shipFromAddressOpt.map { shipFromAddress =>
      CompanyAddress(storeCode,
                     shipFromAddress.road1,
                     shipFromAddress.road2,
                     shipFromAddress.city,
                     shipFromAddress.postalCode,
                     shipFromAddress.country.code,
                     if (!shipFromAddress.state.isEmpty) Some(shipFromAddress.state) else None,
                     compagny.map {
                       _.phone
                     }.flatten,
                     compagny.map { c =>
                       c.shippingInternational
                     }.getOrElse(false))
    }

    val shippingRulePrice = computeShippingRulePrice(cart.storeCode, cart.countryCode, cartWithPrice.mogobizFinalPrice)

    transformCartForCartPay(companyAddress, cartWithPrice, currency, shippingRulePrice)
  }

  def shippingPrices(cart: CartPay, accountId: String): ShippingCart = {
    val addressAndList = MogopayHandlers.handlers.transactionHandler.shippingPrices(cart, accountId)

    val externalShippingPrices = addressAndList._1.map { addr =>
      miraklHandler.shippingPrices(cart, addr)
    }.getOrElse(Map())
    ShippingCart(addressAndList._2, externalShippingPrices)
  }

  protected def computeShippingRulePrice(storeCode: String,
                                         countryCode: Option[String],
                                         cartPice: Long): Option[Long] = {
    countryCode.map { country =>
      val shippingRules = ShippingRuleDao.findByCompany(storeCode)
      val v =
        shippingRules.find(sr => sr.countryCode == country && sr.minAmount <= cartPice && cartPice <= sr.maxAmount)
      v.map {
        _.price
      }
    }.getOrElse(None)
  }

  /**
    * Complète le panier après un paiement réalisé avec succès. Le contenu du panier est envoyé par mail comme justificatif
    * et un nouveau panier est créé
    *
    * @param storeCode
    * @param uuid
    * @param params
    * @param accountId
    */
  def queryCartPaymentLinkToTransaction(storeCode: String,
                                        uuid: String,
                                        params: CommitTransactionParameters,
                                        accountId: Mogopay.Document): Unit = {

    val cart = initCart(storeCode, uuid, Some(accountId), false, None, None)

    runInTransaction({ implicit session: DBSession =>
      cart.boCartUuid.map {
        boCartUuid =>
          BOCartDao
            .load(boCartUuid)
            .map { boCart =>
              val currency = queryCurrency(storeCode, Some(boCart.currencyCode))
              val shippingAddress = BOCartDao.getShippingAddress(boCart).getOrElse(
                throw new IllegalArgumentException("BOcart must have a shipping address")
              )

              val transactionBoCart = boCart.copy(transactionUuid = Some(params.transactionUuid))

              BOCartDao.updateStatusAndExternalCode(transactionBoCart)

              val newChanges = CartChanges(boCartChange = Some(transactionBoCart))
              CartWithChanges(cart = cart, changes = newChanges)
            }
            .getOrElse(throw new IllegalArgumentException(
                    "Unabled to retrieve Cart " + cart.uuid + " into BO. It has not been initialized or has already been validated"))
      }.getOrElse(throw new IllegalArgumentException(
              "Unabled to retrieve Cart " + cart.uuid + " into BO. It has not been initialized or has already been validated"))

    }, { cart: StoreCart =>
      StoreCartDao.save(cart)
    })
  }

  /**
    * Complète le panier après un paiement réalisé avec succès. Le contenu du panier est envoyé par mail comme justificatif
    * et un nouveau panier est créé
    *
    * @param storeCode
    * @param uuid
    * @param params
    * @param accountId
    */
  def queryCartPaymentCommit(storeCode: String,
                             uuid: String,
                             params: CommitTransactionParameters,
                             accountId: Mogopay.Document,
                             selectShippingCart : SelectShippingCart): Unit = {
    val cart = initCart(storeCode, uuid, Some(accountId), false, None, None)

    Try {
      val productIds = cart.cartItems.map { item =>
        UserActionRegistration.register(storeCode, uuid, item.productId.toString, UserAction.Purchase, item.quantity)
        item.productId.toString
      }
      CartRegistration.register(storeCode, uuid, productIds)
    }

    runInTransaction({ implicit session: DBSession =>
      val transactionCart = cart.copy(transactionUuid = Some(params.transactionUuid))
      BOCartDao
        .load(transactionCart.boCartUuid.get)
        .map {
          boCart =>
            val currency = queryCurrency(storeCode, Some(boCart.currencyCode))
            val shippingAddress = BOCartDao.getShippingAddress(boCart).getOrElse(
              throw new IllegalArgumentException("BOcart must have a shipping address")
            )

            // Traitement MIRAKL
            val miraklOrderId = miraklHandler.createOrder(boCart, currency, accountId, shippingAddress, selectShippingCart)

            val transactionBoCart =
              boCart.copy(transactionUuid = Some(params.transactionUuid), status = TransactionStatus.COMPLETE, externalOrderId = miraklOrderId)
            BOCartDao.updateStatusAndExternalCode(transactionBoCart)

            val saleChanges = cart.cartItems.map { cartItem =>
              val productAndSku = ProductDao.getProductAndSku(transactionCart.storeCode, cartItem.skuId)
              val product       = productAndSku.get._1
              val sku           = productAndSku.get._2
              salesHandler.incrementSales(transactionCart.storeCode, product, sku, cartItem.quantity)
            }
            Dashboard.indexCart(storeCode, boCartToESBOCart(storeCode, transactionBoCart), accountId)
            val newChanges = CartChanges(boCartChange = Some(transactionBoCart), saleChanges = saleChanges)
            CartWithChanges(cart = transactionCart, changes = newChanges)
        }
        .getOrElse(throw new IllegalArgumentException(
                "Unabled to retrieve Cart " + cart.uuid + " into BO. It has not been initialized or has already been validated"))
    }, { cart: StoreCart =>
      StoreCartDao.save(cart)
      val updatedCart = StoreCart(storeCode = cart.storeCode, dataUuid = cart.dataUuid, userUuid = cart.userUuid)
      StoreCartDao.save(updatedCart)
      cart
    })
  }

  /**
    * Met à jour le panier suite à l'abandon ou l'échec du paiement
    *
    * @param storeCode
    * @param uuid
    * @param params
    * @param accountId
    * @return
    */
  def queryCartPaymentCancel(storeCode: String,
                             uuid: String,
                             params: CancelTransactionParameters,
                             accountId: Option[Mogopay.Document]): Map[String, Any] = {
    val locale   = buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val cart = initCart(storeCode, uuid, accountId, false, None, None)

    val updatedCart = runInTransaction({ implicit session: DBSession =>
      cancelCart(invalidateCart(cart))
    }, { cart: StoreCart =>
      StoreCartDao.save(cart)
      cart
    })

    val computeCart = computeStoreCart(updatedCart, params.country, params.state)
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
    *
    * @param cartWithChanges
    * @param rate
    * @param buyer
    * @param company
    * @param shippingAddress
    * @return
    */
  protected def createBOCart(cartWithChanges: CartWithPricesAndChanges,
                             rate: Currency,
                             buyer: String,
                             company: Company,
                             shippingAddress: String)(implicit session: DBSession): CartWithPricesAndChanges = {
    val cartWithPrice = cartWithChanges.cart
    val storeCode     = cartWithPrice.storeCart.storeCode
    val boCart        = BOCartDao.create(buyer, company.id, rate, cartWithPrice.totalFinalPrice)

    val newCartItems = cartWithPrice.cartItems.map { cartItemWithPrice =>
      val cartItem      = cartItemWithPrice.cartItem
      val productAndSku = ProductDao.getProductAndSku(storeCode, cartItem.skuId)
      val product       = productAndSku.get._1
      val sku           = productAndSku.get._2

      // Création du BOProduct correspondant au produit principal
      val boProduct =
        BOProductDao.create(cartItemWithPrice.saleTotalEndPrice.getOrElse(cartItemWithPrice.saleTotalPrice),
                            true,
                            cartItem.productId)

      val newStoreRegistedCartItems = cartItem.registeredCartItems.map { registeredCartItem =>
        val boTicketId = BOTicketTypeDao.newId()

        val shortCodeAndQrCode = product.xtype match {
          case ProductType.SERVICE => {
            val startDateStr = cartItem.startDate.map(d => d.toString(DateTimeFormat.forPattern("dd/MM/yyyy HH:mm")))
            val shortCode    = "P" + boProduct.id + "T" + boTicketId
            val qrCodeContent = "EventId:" + product.id + ";BoProductId:" + boProduct.id + ";BoTicketId:" + boTicketId +
                ";EventName:" + product.name + ";EventDate:" + startDateStr + ";FirstName:" +
                registeredCartItem.firstname.getOrElse("") + ";LastName:" + registeredCartItem.lastname.getOrElse("") +
                ";Phone:" + registeredCartItem.phone
                .getOrElse("") + ";TicketType:" + sku.name + ";shortCode:" + shortCode

            val encryptedQrCodeContent = SymmetricCrypt.encrypt(qrCodeContent, company.aesPassword, "AES", true)
            val output                 = new ByteArrayOutputStream()
            QRCodeUtils.createQrCode(output, encryptedQrCodeContent, 256, "png")
            val qrCodeBase64 = Base64.encode(output.toByteArray)

            (Some(shortCode), Some(qrCodeBase64), Some(encryptedQrCodeContent))
          }
          case _ => (None, None, None)
        }

        BOTicketTypeDao.create(boTicketId,
                               sku,
                               cartItemWithPrice,
                               registeredCartItem,
                               shortCodeAndQrCode._1,
                               shortCodeAndQrCode._2,
                               shortCodeAndQrCode._3,
                               boProduct.id)
        shortCodeAndQrCode._3.map { qrCodeContent =>
          registeredCartItem.copy(
              qrCodeContent = Some(product.name + ":" + registeredCartItem.email + "||" + qrCodeContent))
        }.getOrElse(registeredCartItem)
      }

      val boDelivery = BODeliveryDao.create(boCart, Some(shippingAddress))

      // Downloadable Link
      val boCartItemUuid = BOCartItemDao.create(sku, cartItemWithPrice, boCart, Some(boDelivery), boProduct.id).uuid
      val downloadableLink = product.xtype match {
        case ProductType.DOWNLOADABLE => {
          val params =
            s"boCartItemUuid:$boCartItemUuid;skuId:${sku.id};storeCode:$storeCode;maxDelay:${product.downloadMaxDelay};maxTimes:${product.downloadMaxTimes}"
          val encryptedParams = SymmetricCrypt.encrypt(params, company.aesPassword, "AES", true)
          Some(s"${Settings.AccessUrl}/$storeCode/download/$encryptedParams")
        }
        case _ => None
      }
      val newCartItem = cartItem.copy(registeredCartItems = newStoreRegistedCartItems,
                                      boCartItemUuid = Some(boCartItemUuid),
                                      downloadableLink = downloadableLink)
      cartItemWithPrice.copy(cartItem = newCartItem)
    }

    val newCart = cartWithPrice.storeCart.copy(boCartUuid = Some(boCart.uuid), cartItems = newCartItems.map {
      _.cartItem
    })
    val newCartWithPrice = cartWithPrice.copy(storeCart = newCart)
    val newChanges       = cartWithChanges.changes.copy(boCartChange = Some(boCart))
    cartWithChanges.copy(cart = newCartWithPrice, changes = newChanges)
  }

  /**
    * Supprime le BOCart correspondant au panier s'il est en statut Pending
    * et renvoi un panier sans lien avec le boCart supprimé
    *
    * @param cartWithChange
    * @return
    */
  protected def deletePendingBOCart(cartWithChange: CartWithChanges)(implicit session: DBSession): CartWithChanges = {
    val cart = cartWithChange.cart
    cart.boCartUuid.map { boCartUuid =>
      BOCartDao
        .load(boCartUuid)
        .map { boCart =>
          if (boCart.status == TransactionStatus.PENDING) {
            BOCartItemDao.findByBOCart(boCart).foreach { boCartItem =>
              BOCartItemDao.delete(boCartItem)

              BOCartItemDao.getBOProducts(boCartItem).foreach { boProduct =>
                BOTicketTypeDao.findByBOProduct(boProduct.id).foreach { boTicketType =>
                  BOTicketTypeDao.delete(boTicketType)
                }
                BOProductDao.delete(boProduct)
              }
            }
            BOCartDao.delete(boCart)

            val newChanges = cartWithChange.changes.copy(deletedBOCart = Some(boCart))
            val newCart    = cart.copy(boCartUuid = None)
            cartWithChange.copy(cart = newCart, changes = newChanges)
          } else cartWithChange
        }
        .getOrElse(cartWithChange)
    }.getOrElse(cartWithChange)
  }

  /**
    * Construit un Locale à partir de la langue et du pays.<br/>
    * Si la lang == "_all" alors la langue par défaut est utilisée<br/>
    * Si le pays vaut None alors le pays par défaut est utiulisé
    *
    * @param lang
    * @param country
    * @return
    */
  protected def buildLocal(lang: String, country: Option[String]): Locale = {
    val defaultLocal = Locale.getDefault
    val l            = if (lang == "_all") defaultLocal.getLanguage else lang
    val c            = if (country.isEmpty) defaultLocal.getCountry else country.get
    new Locale(l, c)
  }

  /**
    * Récupère le panier correspondant au uuid et au compte client.
    * La méthode gère le panier anonyme et le panier authentifié.
    *
    * @param uuid
    * @param currentAccountId
    * @return
    */
  protected def initCart(storeCode: String,
                         uuid: String,
                         currentAccountId: Option[String],
                         removeUnsalableItem: Boolean,
                         country: Option[String],
                         state: Option[String]): StoreCart = {
    def prepareCart(cart: StoreCart) = {
      if (removeUnsalableItem) removeAllUnsellableItems(cart, country, state) else cart
    }

    def getOrCreateStoreCart(cart: Option[StoreCart]): StoreCart = {
      cart match {
        case Some(c) => prepareCart(c)
        case None =>
          val c = new StoreCart(storeCode = storeCode, dataUuid = uuid, userUuid = currentAccountId)
          StoreCartDao.save(c)
          c
      }
    }

    if (currentAccountId.isDefined) {
      val cartAnonyme = StoreCartDao.findByDataUuidAndUserUuid(storeCode, uuid, None).map { c =>
        prepareCart(c)
      };
      val cartAuthentifie = getOrCreateStoreCart(
          StoreCartDao.findByDataUuidAndUserUuid(storeCode, uuid, currentAccountId));

      // S'il y a un panier anonyme, il est fusionné avec le panier authentifié et supprimé de la base
      if (cartAnonyme.isDefined) {
        StoreCartDao.delete(cartAnonyme.get)
        val fusionCart = mergeCarts(cartAnonyme.get, cartAuthentifie)
        StoreCartDao.save(fusionCart)
        fusionCart
      } else cartAuthentifie
    } else {
      // Utilisateur anonyme
      getOrCreateStoreCart(StoreCartDao.findByDataUuidAndUserUuid(storeCode, uuid, None));
    }
  }

  protected def removeAllUnsellableItems(cart: StoreCart, country: Option[String], state: Option[String]): StoreCart = {
    val transactionalBloc = { implicit session: DBSession =>
      val cartItemsToRemoved = cart.cartItems.flatMap { cartItem =>
        val cartItemWithIndex = cartItem.copy(indexEs = Option(cartItem.indexEs).getOrElse(cart.storeCode))
        val productAndSku     = ProductDao.getProductAndSku(cart.storeCode, cartItem.skuId)
        if (productAndSku.isEmpty || !checkProductAndSkuSalabality(productAndSku.get, country, state)) {
          Some(cartItemWithIndex)
        } else None
      }

      val newCoupons = cart.coupons.map { coupon =>
        CouponDao.findByCode(cart.storeCode, coupon.code).map { c =>
          coupon
        }
      }.flatten

      removeCartItemsInTransaction(cart.copy(coupons = newCoupons), cartItemsToRemoved)
    }
    val successBloc = { cart: StoreCart =>
      StoreCartDao.save(cart)
      cart
    }
    runInTransaction(transactionalBloc, successBloc)
  }

  /**
    * Supprime les éléments du panier en incrémentant si besoin des stocks (si panier validé).
    * Doit être fait dans une transaction
    * @param cart
    * @param cartItemsToRemoved
    * @return
    */
  protected def removeCartItemsInTransaction(cart: StoreCart, cartItemsToRemoved: List[StoreCartItem])(
      implicit session: DBSession): CartWithChanges = {
    if (cartItemsToRemoved.isEmpty) CartWithChanges(cart, CartChanges())
    else {
      val invalidationResult = invalidateCart(cart)
      val newCartItems = invalidationResult.cart.cartItems.filter { cartItem =>
        cartItemsToRemoved.find { item =>
          item.id == cartItem.id
        }.isEmpty
      }
      invalidationResult.copy(cart = invalidationResult.cart.copy(cartItems = newCartItems))
    }
  }

  protected def clearCart[U](cart: StoreCart, success: StoreCart => U): U = {
    runInTransaction({ implicit session: DBSession =>
      val invalidateResult = invalidateCart(cart)
      val cancelResult     = cancelCart(invalidateResult)

      cart.coupons.foreach(coupon => {
        val optCoupon = CouponDao.findByCode(cart.storeCode, coupon.code)
        if (optCoupon.isDefined) couponHandler.releaseCoupon(cart.storeCode, optCoupon.get)
      })

      cancelResult
    }, { cart: StoreCart =>
      success(cart)
    })
  }

  /**
    * Annule la transaction courante et génère un nouveau uuid au panier
    *
    * @param cartAndChanges
    * @return
    */
  protected def cancelCart(cartAndChanges: CartWithChanges)(implicit session: DBSession): CartWithChanges = {
    val cart = cartAndChanges.cart
    cart.boCartUuid.map { boCartUuid =>
      val newBoCart = BOCartDao.load(cart.boCartUuid.get).map { boCart =>
        // Mise à jour du statut
        val newBoCart = boCart.copy(status = TransactionStatus.FAILED)
        BOCartDao.updateStatusAndExternalCode(newBoCart)
        newBoCart
      }
      val newChanges = cartAndChanges.changes.copy(boCartChange = newBoCart)
      val newCart    = cart.copy(boCartUuid = None, transactionUuid = None)
      cartAndChanges.copy(cart = newCart, changes = newChanges)
    } getOrElse (cartAndChanges)
  }

  /**
    * Valide le panier en décrémentant les stocks
    *
    * @param cart
    * @return
    */
  @throws[InsufficientStockException]
  protected def validateCart(cart: StoreCart)(implicit session: DBSession): CartWithChanges = {
    if (!cart.validate) {
      val changes = CartChanges(stockChanges = validateCartItems(cart.cartItems))
      CartWithChanges(cart.copy(validate = true, validateUuid = Some(UUID.randomUUID().toString())), changes)
    } else CartWithChanges(cart, CartChanges())
  }

  protected def validateCartItems(cartItems: List[StoreCartItem])(implicit session: DBSession): List[StockChange] = {
    if (cartItems.isEmpty) Nil
    else {
      val result   = validateCartItems(cartItems.tail)
      val cartItem = cartItems.head
      validateCartItem(cartItem).map { indexAndProduct =>
        indexAndProduct :: result
      }.getOrElse(result)
    }
  }

  protected def validateCartItem(cartItem: StoreCartItem)(implicit session: DBSession): Option[StockChange] = {
    val productAndSku = ProductDao.getProductAndSku(cartItem.indexEs, cartItem.skuId)
    productAndSku.map { ps =>
      val product = ps._1
      val sku     = ps._2
      stockHandler.decrementStock(cartItem.indexEs, product, sku, cartItem.quantity, cartItem.startDate)
    }.flatten
  }

  /**
    * Invalide le panier et libère les stocks si le panier était validé.
    *
    * @param cart
    * @return
    */
  protected def invalidateCart(cart: StoreCart)(implicit session: DBSession): CartWithChanges = {
    if (cart.validate) {
      val changes = CartChanges(stockChanges = invalidateCartItems(cart.cartItems))
      CartWithChanges(cart.copy(validate = false, validateUuid = None), changes)
    } else CartWithChanges(cart, CartChanges())
  }

  protected def invalidateCartItems(cartItems: List[StoreCartItem])(implicit session: DBSession): List[StockChange] = {
    if (cartItems.isEmpty) Nil
    else {
      val result   = invalidateCartItems(cartItems.tail)
      val cartItem = cartItems.head
      invalidateCartItem(cartItem).map { indexAndProduct =>
        indexAndProduct :: result
      }.getOrElse(result)
    }
  }

  protected def invalidateCartItem(cartItem: StoreCartItem)(implicit session: DBSession): Option[StockChange] = {
    val productAndSku = ProductDao.getProductAndSku(cartItem.indexEs, cartItem.skuId)
    productAndSku.map { ps =>
      val product = ps._1
      val sku     = ps._2
      stockHandler.incrementStock(cartItem.indexEs, product, sku, cartItem.quantity, cartItem.startDate)
    }.flatten
  }

  def notifyChangesIntoES(storeCode: String, changes: CartChanges, refresh: Boolean = false): Unit = {
    import scala.concurrent.duration._
    val system = ActorSystemLocator()
    import system.dispatcher
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
      val boCartES = boCartToESBOCart(storeCode, boCart)
      BOCartESDao.save(storeCode, boCartES, refresh)
    }

    changes.deletedBOCart.map { boCart =>
      BOCartESDao.delete(storeCode, boCart.uuid)
    }

  }

  /**
    * Fusionne le panier source avec le panier cible et renvoie le résultat
    * de la fusion
    *
    * @param source
    * @param target
    * @return
    */
  protected def mergeCarts(source: StoreCart, target: StoreCart): StoreCart = {
    def mergeCartItem(source: List[StoreCartItem], target: StoreCart): StoreCart = {
      if (source.isEmpty) target
      else mergeCartItem(source.tail, addCartItemIntoCart(target, source.head))
    }
    def mergeCoupon(source: List[StoreCoupon], target: StoreCart): StoreCart = {
      if (source.isEmpty) target
      else mergeCoupon(source.tail, addCouponIntoCart(target, source.head))
    }
    mergeCoupon(source.coupons.toList, mergeCartItem(source.cartItems.toList, target))
  }

  /**
    * Ajoute un item au panier. Si un item existe déjà (sauf pour le SERVICE), la quantité
    * de l'item existant est modifié
    *
    * @param cart
    * @param cartItem
    * @return
    */
  protected def addCartItemIntoCart(cart: StoreCart, cartItem: StoreCartItem): StoreCart = {
    val newCartItems = findCartItem(cart, cartItem).map { existCartItem =>
      cart.cartItems.map { item =>
        if (item == existCartItem) item.copy(quantity = item.quantity + cartItem.quantity) else item
      }
    }.getOrElse(cartItem :: cart.cartItems)
    cart.copy(cartItems = newCartItems)
  }

  /**
    * Retrouve un item parmi la liste des items du panier. L'item est recherche si le type
    * n'est pas SERVICE et si l'id du produit et du sku sont identiques
    *
    * @param cart
    * @param cartItem
    * @return
    */
  protected def findCartItem(cart: StoreCart, cartItem: StoreCartItem): Option[StoreCartItem] = {
    if (cartItem.xtype == ProductType.SERVICE) None
    else
      cart.cartItems.find { ci: StoreCartItem =>
        ci.productId == cartItem.productId &&
        ci.skuId == cartItem.skuId &&
        isSameDateTime(ci, cartItem)
      }
  }

  protected def isSameDateTime(cartItem1: StoreCartItem, cartItem2: StoreCartItem): Boolean = {
    val now = DateTime.now()
    cartItem1.startDate
      .getOrElse(now)
      .withMillisOfSecond(0)
      .withSecondOfMinute(0)
      .isEqual(cartItem2.startDate.getOrElse(now).withMillisOfSecond(0).withSecondOfMinute(0)) &&
    cartItem1.endDate
      .getOrElse(now)
      .withMillisOfSecond(0)
      .withSecondOfMinute(0)
      .isEqual(cartItem2.endDate.getOrElse(now).withMillisOfSecond(0).withSecondOfMinute(0))
  }

  /**
    * Ajoute un coupon au panier s'il n'existe pas déjà (en comparant les codes des coupons)
    *
    * @param cart
    * @param coupon
    * @return
    */
  protected def addCouponIntoCart(cart: StoreCart, coupon: StoreCoupon): StoreCart = {
    val existCoupon = cart.coupons.find { c: StoreCoupon =>
      c.code == coupon.code
    }
    if (existCoupon.isDefined) {
      cart
    } else {
      val newCoupons = (coupon :: cart.coupons)
      cart.copy(coupons = newCoupons)
    }
  }

  /**
    * Transforme le StoreCart en un CartVO en calculant les montants
    *
    * @param cart
    * @param countryCode
    * @param stateCode
    * @return
    */
  protected def computeStoreCart(cart: StoreCart,
                                 countryCode: Option[String],
                                 stateCode: Option[String]): StoreCartWithPrice = {
    val coupons = cart.coupons.collect {
      case coupon: StoreCoupon => couponHandler.getWithData(cart.storeCode, coupon)
    }.flatten :::
      CouponDao.findPromotionsThatOnlyApplyOnCart(cart.storeCode).collect {
        case promotion: Mogobiz.Coupon => couponHandler.getWithData(promotion)
      }
    val cartItemsWithPrice = computeCartItemWithPrice(cart.storeCode, cart.cartItems, countryCode, stateCode)

    val pricesCartItemsAndCoupons =
      computeCartPriceAndCartItemWithSalePriceAndCoupons(cart, cartItemsWithPrice, coupons)

    StoreCartWithPrice(cart,
      pricesCartItemsAndCoupons.cartItems,
      pricesCartItemsAndCoupons.coupons,
      pricesCartItemsAndCoupons.mogobizPrice,
      pricesCartItemsAndCoupons.mogobizEndPrice,
      pricesCartItemsAndCoupons.mogobizReduction,
      Math.max(0, pricesCartItemsAndCoupons.mogobizEndPrice - pricesCartItemsAndCoupons.mogobizReduction),
      pricesCartItemsAndCoupons.externalPrice,
      pricesCartItemsAndCoupons.externalEndPrice,
      pricesCartItemsAndCoupons.externalReduction,
      Math.max(0, pricesCartItemsAndCoupons.externalEndPrice - pricesCartItemsAndCoupons.externalReduction))
  }

  type PriceHT   = Long
  type PriceTTC  = Long
  type Reduction = Long
  case class ComputeCartPriceResult(mogobizPrice: PriceHT,
                                    mogobizEndPrice: PriceTTC,
                                    mogobizReduction: Reduction,
                                    externalPrice: PriceHT,
                                    externalEndPrice: PriceTTC,
                                    externalReduction: Reduction,
                                    cartItems: List[StoreCartItemWithPrice],
                                    coupons: List[CouponWithData])
  def computeCartPriceAndCartItemWithSalePriceAndCoupons(cart: StoreCart,
                                                         cartItems: List[StoreCartItemWithPrice],
                                                         coupons: List[CouponWithData])
    : ComputeCartPriceResult = {
    if (cartItems.isEmpty) ComputeCartPriceResult(0, 0, 0, 0, 0, 0, Nil, coupons)
    else {
      val cartItemsAndCoupons = computeCartPriceAndCartItemWithSalePriceAndCoupons(cart, cartItems.tail, coupons)
      val cartItem            = cartItems.head
      val maxReduction        = findBestReductionForCartItem(cart.storeCode, cartItem, coupons, cartItems)

      // Si la réduction est associé à un coupon. Le réduction sera appliquée au panier sinon elle est appliquée
      // au produit
      val reductionValue =
      Math.max(0, Math.min(cartItem.totalEndPrice.getOrElse(cartItem.totalPrice), maxReduction._1))
      val renderCartItem = computeCartItemWithSalePrice(cart, cartItem, maxReduction._2.map {
        couponReduction: CouponWithData =>
          0L
      }.getOrElse(reductionValue))
      val newCoupons = maxReduction._2.map { couponReduction: CouponWithData =>
        cartItemsAndCoupons.coupons.collect {
          case c: CouponWithData => {
            if (c.coupon.id == couponReduction.coupon.id) c.copy(reduction = c.reduction + reductionValue)
            else c
          }
        }
      }.getOrElse(cartItemsAndCoupons.coupons)
      if (cartItem.cartItem.externalCode.isDefined) {
        val newExternalPrice = cartItemsAndCoupons.externalPrice + renderCartItem.saleTotalPrice
        val newExternalEndPrice = cartItemsAndCoupons.externalEndPrice + renderCartItem.saleTotalEndPrice.getOrElse(
          renderCartItem.saleTotalPrice)
        val newExternalReduction = cartItemsAndCoupons.externalReduction + maxReduction._2.map { couponReduction: CouponWithData =>
          reductionValue
        }.getOrElse(0L)
        ComputeCartPriceResult(cartItemsAndCoupons.mogobizPrice,
          cartItemsAndCoupons.mogobizEndPrice,
          cartItemsAndCoupons.mogobizReduction,
          newExternalPrice,
          newExternalEndPrice,
          newExternalReduction,
          renderCartItem :: cartItemsAndCoupons.cartItems,
          newCoupons)
      }
      else {
        val newMogobizPrice = cartItemsAndCoupons.mogobizPrice + renderCartItem.saleTotalPrice
        val newMogobizEndPrice = cartItemsAndCoupons.mogobizEndPrice + renderCartItem.saleTotalEndPrice.getOrElse(
          renderCartItem.saleTotalPrice)
        val newMogobizReduction = cartItemsAndCoupons.mogobizReduction + maxReduction._2.map { couponReduction: CouponWithData =>
          reductionValue
        }.getOrElse(0L)
        ComputeCartPriceResult(newMogobizPrice,
          newMogobizEndPrice,
          newMogobizReduction,
          cartItemsAndCoupons.externalPrice,
          cartItemsAndCoupons.externalEndPrice,
          cartItemsAndCoupons.externalReduction,
          renderCartItem :: cartItemsAndCoupons.cartItems,
          newCoupons)
      }
    }
  }

    def computeCouponAsRenderCoupon(storeCode: String, coupons: List[CouponWithData]): List[Option[Coupon]] = {
      if (coupons.isEmpty) List()
      else couponHandler.transformAsRender(storeCode, coupons.head) :: computeCouponAsRenderCoupon(storeCode, coupons.tail)
    }

  protected def computeCartItemWithPrice(storeCode: String,
                                         cartItems: List[StoreCartItem],
                                         countryCode: Option[String],
                                         stateCode: Option[String]): List[StoreCartItemWithPrice] = {
    cartItems.map { cartItem =>
      val product    = ProductDao.get(storeCode, cartItem.productId).get
      val tax        = taxRateHandler.findTaxRateByProduct(product, countryCode, stateCode)
      val endPrice   = taxRateHandler.calculateEndPrice(cartItem.price, tax)
      val totalPrice = cartItem.price * cartItem.quantity
      val totalEndPrice = endPrice.map { p: Long =>
        p * cartItem.quantity
      }
      StoreCartItemWithPrice(cartItem,
                             cartItem.quantity,
                             cartItem.price,
                             endPrice,
                             tax,
                             totalPrice,
                             totalEndPrice,
                             cartItem.price,
                             endPrice,
                             totalPrice,
                             totalEndPrice,
                             0)
    }
  }

  protected def computeCartItemWithSalePrice(cart: StoreCart,
                                             cartItemWithPrice: StoreCartItemWithPrice,
                                             reduction: Long): StoreCartItemWithPrice = {
    val cartItem       = cartItemWithPrice.cartItem
    val product        = ProductDao.get(cart.storeCode, cartItem.productId).get
    val discounts      = findSuggestionDiscount(cart, cartItem.productId)
    val salePrice      = computeDiscounts(Math.max(0, cartItem.price - reduction), discounts)
    val saleEndPrice   = taxRateHandler.calculateEndPrice(salePrice, cartItemWithPrice.tax)
    val saleTotalPrice = salePrice * cartItem.quantity
    val saleTotalEndPrice = saleEndPrice.map { p: Long =>
      p * cartItem.quantity
    }

    cartItemWithPrice.copy(
        salePrice = salePrice,
        saleEndPrice = saleEndPrice,
        saleTotalPrice = saleTotalPrice,
        saleTotalEndPrice = saleTotalEndPrice,
        reduction = reduction
    )
  }

  protected def findBestReductionForCartItem(
      storeCode: String,
      cartItem: StoreCartItemWithPrice,
      coupons: List[CouponWithData],
      cartItems: List[StoreCartItemWithPrice]): (Long, Option[CouponWithData]) = {
    if (coupons.isEmpty) (cartItem.cartItem.price - cartItem.cartItem.salePrice, None)
    else {
      val coupon     = coupons.head
      val price      = couponHandler.computeCouponPriceForCartItem(storeCode, coupon, cartItem, cartItems)
      val tailResult = findBestReductionForCartItem(storeCode, cartItem, coupons.tail, cartItems)
      if (price > tailResult._1) (price, Some(coupon))
      else tailResult
    }
  }

  protected def findSuggestionDiscount(cart: StoreCart, productId: Long): List[String] = {
    def extractDiscout(l: List[Mogobiz.Suggestion]): List[String] = {
      if (l.isEmpty) List()
      else {
        val s: Mogobiz.Suggestion = l.head
        val ci = cart.cartItems.find { ci =>
          ci.productId == s.parentId
        }
        if (ci.isDefined) s.discount :: extractDiscout(l.tail)
        else extractDiscout(l.tail)
      }
    }
    val suggestions: List[Mogobiz.Suggestion] = SuggestionDao.getSuggestionsbyId(cart.storeCode, productId)
    extractDiscout(suggestions)
  }

  protected def computeDiscounts(price: Long, discounts: List[String]): Long = {
    if (discounts.isEmpty) Math.max(price, 0)
    else {
      val discountPrice = Math.max(price - couponHandler.computeDiscount(Some(discounts.head), price), 0)
      Math.min(discountPrice, computeDiscounts(price, discounts.tail))
    }
  }

  protected def calculateCount(cart: StoreCartWithPrice): Int = {
    cart.cartItems.map { cartItem =>
      cartItem.quantity
    }.sum
  }

  protected def renderCart(cart: StoreCartWithPrice, currency: Currency, locale: Locale): Map[String, Any] = {
    val formatedPrice      = rateService.formatLongPrice(cart.totalPrice, currency, locale)
    val formatedEndPrice   = rateService.formatLongPrice(cart.totalEndPrice, currency, locale)
    val formatedReduction  = rateService.formatLongPrice(cart.totalReduction, currency, locale)
    val formatedFinalPrice = rateService.formatLongPrice(cart.totalFinalPrice, currency, locale)

    Map(
        "validateUuid"       -> cart.storeCart.validateUuid.getOrElse(""),
        "count"              -> calculateCount(cart),
        "cartItemVOs"        -> cart.cartItems.map(item => renderCartItem(item, currency, locale)),
        "coupons"            -> cart.coupons.map(c => renderCoupon(cart.storeCart.storeCode, c, currency, locale)).flatten,
        "price"              -> cart.totalPrice,
        "endPrice"           -> cart.totalEndPrice,
        "reduction"          -> cart.totalReduction,
        "finalPrice"         -> cart.totalFinalPrice,
        "formatedPrice"      -> formatedPrice,
        "formatedEndPrice"   -> formatedEndPrice,
        "formatedReduction"  -> formatedReduction,
        "formatedFinalPrice" -> formatedFinalPrice
    )
  }

  protected def transformCartForCartPay(compagnyAddress: Option[CompanyAddress],
                                        cartWithPrice: StoreCartWithPrice,
                                        rate: Currency,
                                        shippingRulePrice: Option[Long]): CartPay = {
    val cartItemsPay = cartWithPrice.cartItems.map { cartItemWithPrice =>
      val cartItem = cartItemWithPrice.cartItem
      val registeredCartItemsPay = cartItem.registeredCartItems.map { rci =>
        new RegisteredCartItemPay(rci.id,
                                  rci.email,
                                  rci.firstname,
                                  rci.lastname,
                                  rci.phone,
                                  rci.birthdate,
                                  rci.qrCodeContent,
                                  Map())
      }
      val shippingPay = cartItem.shipping.map { shipping =>
        new ShippingPay(shipping.weight,
                        shipping.weightUnit.toString(),
                        shipping.width,
                        shipping.height,
                        shipping.depth,
                        shipping.linearUnit.toString(),
                        shipping.amount,
                        shipping.free,
                        Map())
      }
      val customCartItem = Map("productId" -> cartItem.productId,
                               "productName"  -> cartItem.productName,
                               "xtype"        -> cartItem.xtype.toString(),
                               "calendarType" -> cartItem.calendarType.toString(),
                               "skuId"        -> cartItem.skuId,
                               "skuName"      -> cartItem.skuName,
                               "startDate"    -> cartItem.startDate,
                               "endDate"      -> cartItem.endDate)
      val name              = s"${cartItem.productName} ${cartItem.skuName}"
      val endPrice          = cartItemWithPrice.endPrice.getOrElse(cartItemWithPrice.price)
      val totalEndPrice     = cartItemWithPrice.totalEndPrice.getOrElse(cartItemWithPrice.totalPrice)
      val saleEndPrice      = cartItemWithPrice.saleEndPrice.getOrElse(cartItemWithPrice.salePrice)
      val saleTotalEndPrice = cartItemWithPrice.saleTotalEndPrice.getOrElse(cartItemWithPrice.saleTotalPrice)
      new CartItemPay(cartItem.id,
                      name,
                      cartItem.productPicture,
                      cartItem.productUrl,
                      cartItem.quantity,
                      cartItemWithPrice.price,
                      endPrice,
                      cartItemWithPrice.tax.getOrElse(0),
                      endPrice - cartItemWithPrice.price,
                      cartItemWithPrice.totalPrice,
                      totalEndPrice,
                      totalEndPrice - cartItemWithPrice.totalPrice,
                      cartItemWithPrice.salePrice,
                      saleEndPrice,
                      saleEndPrice - cartItemWithPrice.salePrice,
                      cartItemWithPrice.saleTotalPrice,
                      saleTotalEndPrice,
                      saleTotalEndPrice - cartItemWithPrice.saleTotalPrice,
                      registeredCartItemsPay,
                      shippingPay,
                      cartItem.downloadableLink.getOrElse(""),
                      cartItem.externalCode,
                      customCartItem)
    }
    val couponsPay = cartWithPrice.coupons.map { couponWithData =>
      couponHandler.transformAsRender(cartWithPrice.storeCart.storeCode, couponWithData).map { coupon =>
        val customCoupon = Map("name" -> coupon.name, "active" -> coupon.active)
        new CouponPay(coupon.code, coupon.startDate, coupon.endDate, coupon.price, customCoupon)
      }
    }.flatten

    val cartRate = CartRate(rate.code, rate.numericCode, rate.rate, rate.currencyFractionDigits)

    new CartPay(calculateCount(cartWithPrice),
                cartRate,
                cartWithPrice.totalPrice,
                cartWithPrice.totalEndPrice,
                cartWithPrice.totalEndPrice - cartWithPrice.totalPrice,
                cartWithPrice.totalReduction,
                cartWithPrice.totalFinalPrice,
                cartWithPrice.mogobizFinalPrice,
                shippingRulePrice,
                cartItemsPay,
                couponsPay,
                Map(),
                compagnyAddress)
  }

  /**
    * Idem que renderCart à quelques différences près d'où la dupplication de code
    *
    * @param cartWithPrice
    * @param rate
    * @return
    */
  protected def renderTransactionCart(cartWithPrice: StoreCartWithPrice,
                                      rate: Currency,
                                      locale: Locale): Map[String, Any] = {
    val storeCart  = cartWithPrice.storeCart
    val price      = rateService.calculateAmount(cartWithPrice.totalPrice, rate)
    val endPrice   = rateService.calculateAmount(cartWithPrice.totalEndPrice, rate)
    val reduction  = rateService.calculateAmount(cartWithPrice.totalReduction, rate)
    val finalPrice = rateService.calculateAmount(cartWithPrice.totalFinalPrice, rate)

    Map(
        "boCartUuid"      -> storeCart.boCartUuid.getOrElse(""),
        "transactionUuid" -> storeCart.transactionUuid.getOrElse(""),
        "count"           -> calculateCount(cartWithPrice),
        "cartItemVOs"     -> cartWithPrice.cartItems.map(item => renderTransactionCartItem(item, rate, locale)),
        "coupons" -> cartWithPrice.coupons
          .map(c => renderTransactionCoupon(storeCart.storeCode, c, rate, locale))
          .flatten,
        "price"              -> price,
        "endPrice"           -> endPrice,
        "reduction"          -> reduction,
        "finalPrice"         -> finalPrice,
        "formatedPrice"      -> rateService.formatLongPrice(cartWithPrice.totalPrice, rate, locale),
        "formatedEndPrice"   -> rateService.formatLongPrice(cartWithPrice.totalEndPrice, rate, locale),
        "formatedReduction"  -> rateService.formatLongPrice(cartWithPrice.totalReduction, rate, locale),
        "formatedFinalPrice" -> rateService.formatLongPrice(cartWithPrice.totalFinalPrice, rate, locale),
        "date"               -> new Date().getTime
    )
  }

  /**
    * Renvoie un coupon JSONiné augmenté par un calcul de prix formaté
    *
    * @param storeCode
    * @param couponWithData
    * @param currency
    * @param locale
    * @return
    */
  protected def renderCoupon(storeCode: String, couponWithData: CouponWithData, currency: Currency, locale: Locale) = {
    couponHandler.transformAsRender(storeCode, couponWithData).map { coupon =>
      implicit def json4sFormats: Formats = DefaultFormats ++ JodaTimeSerializers.all + FieldSerializer[Coupon]()
      val jsonCoupon = parse(write(coupon))

      //code from renderPriceCoupon
      val formatedPrice   = rateService.formatPrice(coupon.price, currency, locale)
      val additionalsData = parse(write(Map("formatedPrice" -> formatedPrice)))

      jsonCoupon merge additionalsData
    }
  }

  protected def renderTransactionCoupon(storeCode: String,
                                        couponWithData: CouponWithData,
                                        rate: Currency,
                                        locale: Locale) = {
    couponHandler.transformAsRender(storeCode, couponWithData).map { coupon =>
      implicit def json4sFormats: Formats = DefaultFormats ++ JodaTimeSerializers.all + FieldSerializer[Coupon]()
      val jsonCoupon = parse(write(coupon))

      val price = rateService.calculateAmount(coupon.price, rate)
      val updatedData = parse(
          write(
              Map(
                  "price"         -> price,
                  "formatedPrice" -> rateService.formatPrice(coupon.price, rate, locale)
              )))

      jsonCoupon merge updatedData
    }
  }

  /**
    * Renvoie un cartItem JSONinsé augmenté par le calcul des prix formattés
    *
    * @param item item du panier
    * @param currency
    * @param locale
    * @return
    */
  protected def renderCartItem(item: StoreCartItemWithPrice, currency: Currency, locale: Locale) = {
    import org.json4s.jackson.JsonMethods._
    import org.json4s.jackson.Serialization.write
    implicit def json4sFormats: Formats =
      DefaultFormats ++ JodaTimeSerializers.all + FieldSerializer[StoreCartItem]() + new org.json4s.ext.EnumNameSerializer(
          ProductCalendar)
    val jsonItem = parse(write(item.cartItem))

    val additionalsData = parse(
        write(
            Map(
                "price"             -> item.price,
                "endPrice"          -> item.endPrice,
                "tax"               -> item.tax,
                "totalPrice"        -> item.totalPrice,
                "totalEndPrice"     -> item.totalEndPrice,
                "salePrice"         -> item.salePrice,
                "saleEndPrice"      -> item.saleEndPrice,
                "saleTotalPrice"    -> item.saleTotalPrice,
                "saleTotalEndPrice" -> item.saleTotalEndPrice,
                "formatedPrice"     -> rateService.formatPrice(item.price, currency, locale),
                "formatedSalePrice" -> rateService.formatPrice(item.salePrice, currency, locale),
                "formatedEndPrice" -> item.endPrice.map {
          rateService.formatPrice(_, currency, locale)
        },
                "formatedSaleEndPrice" -> item.saleEndPrice.map {
          rateService.formatPrice(_, currency, locale)
        },
                "formatedTotalPrice"     -> rateService.formatPrice(item.totalPrice, currency, locale),
                "formatedSaleTotalPrice" -> rateService.formatPrice(item.saleTotalPrice, currency, locale),
                "formatedTotalEndPrice" -> item.totalEndPrice.map {
          rateService.formatPrice(_, currency, locale)
        },
                "formatedSaleTotalEndPrice" -> item.saleTotalEndPrice.map {
          rateService.formatPrice(_, currency, locale)
        }
            )))

    jsonItem merge additionalsData
  }

  protected def renderTransactionCartItem(item: StoreCartItemWithPrice, rate: Currency, locale: Locale) = {
    import org.json4s.jackson.JsonMethods._
    import org.json4s.jackson.Serialization.write
    import Json4sProtocol._
    val jsonItem = parse(write(item.cartItem))

    val price             = rateService.calculateAmount(item.price, rate)
    val salePrice         = rateService.calculateAmount(item.salePrice, rate)
    val endPrice          = rateService.calculateAmount(item.endPrice.getOrElse(item.price), rate)
    val saleEndPrice      = rateService.calculateAmount(item.saleEndPrice.getOrElse(item.salePrice), rate)
    val totalPrice        = rateService.calculateAmount(item.totalPrice, rate)
    val saleTotalPrice    = rateService.calculateAmount(item.saleTotalPrice, rate)
    val totalEndPrice     = rateService.calculateAmount(item.totalEndPrice.getOrElse(item.totalPrice), rate)
    val saleTotalEndPrice = rateService.calculateAmount(item.saleTotalEndPrice.getOrElse(item.saleTotalPrice), rate)

    val updatedData = parse(
        write(
            Map(
                "price"             -> price,
                "salePrice"         -> salePrice,
                "endPrice"          -> endPrice,
                "saleEndPrice"      -> saleEndPrice,
                "tax"               -> item.tax,
                "totalPrice"        -> totalPrice,
                "saleTotalPrice"    -> saleTotalPrice,
                "totalEndPrice"     -> totalEndPrice,
                "saleTotalEndPrice" -> saleTotalEndPrice,
                "formatedPrice"     -> rateService.formatLongPrice(item.price, rate, locale),
                "formatedSalePrice" -> rateService.formatLongPrice(item.salePrice, rate, locale),
                "formatedEndPrice"  -> rateService.formatLongPrice(item.endPrice.getOrElse(item.price), rate, locale),
                "formatedSaleEndPrice" -> rateService
                  .formatLongPrice(item.saleEndPrice.getOrElse(item.salePrice), rate, locale),
                "formatedTotalPrice"     -> rateService.formatLongPrice(item.totalPrice, rate, locale),
                "formatedSaleTotalPrice" -> rateService.formatLongPrice(item.saleTotalPrice, rate, locale),
                "formatedTotalEndPrice" -> rateService
                  .formatLongPrice(item.totalEndPrice.getOrElse(item.totalPrice), rate, locale),
                "formatedSaleTotalEndPrice" -> rateService
                  .formatLongPrice(item.saleTotalEndPrice.getOrElse(item.saleTotalPrice), rate, locale)
            )))

    jsonItem merge updatedData
  }

  /**
   * Renvoie tous les champs prix calculé sur le panier
   *
   * @param cart
   * @param currency
   * @param locale
   * @return
   */
  protected def renderPriceCart(cart: CartPay, currency: Currency, locale: Locale) = {
    val formatedPrice = rateService.formatLongPrice(cart.price, currency, locale)
    val formatedEndPrice = rateService.formatLongPrice(cart.endPrice, currency, locale)
    val formatedReduction = rateService.formatLongPrice(cart.reduction, currency, locale)
    val formatedFinalPrice = rateService.formatLongPrice(cart.finalPrice, currency, locale)

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

  protected def renderTransactionPriceCart(cart: CartPay, rate: Currency, locale: Locale) = {
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
      "formatedEndPrice" -> rateService.formatLongPrice(cart.endPrice, rate, locale),
      "formatedReduction" -> rateService.formatLongPrice(cart.reduction, rate, locale),
      "formatedFinalPrice" -> rateService.formatLongPrice(cart.finalPrice, rate, locale)
    )
  }

  def exportBOCartIntoES(storeCode: String, boCart: BOCart, refresh: Boolean = false)(implicit session: DBSession = AutoSession): Boolean = {
    val boCartES = boCartToESBOCart(storeCode, boCart)
    BOCartESDao.save(storeCode, boCartES, refresh)
  }

  def boCartToESBOCart(storeCode: String, boCart: BOCart)(implicit session: DBSession = AutoSession): ES.BOCart = {
    // Conversion des BOCartItem
    val cartItems = BOCartItemDao.findByBOCart(boCart).flatMap { boCartItem =>
      // Conversion des BOProducts
      val boProducts: List[ES.BOProduct] = BOCartItemDao.getBOProducts(boCartItem).flatMap { boProduct =>
        // Convertion des BOTicketType
        val boRegisteredCartItems = BOTicketTypeDao.findByBOProduct(boProduct.id).map { boTicketType =>
          // Instanciation du BORegisteredCartItem pour ES
          BORegisteredCartItemES(age = boTicketType.age,
                                 quantity = boTicketType.quantity,
                                 price = boTicketType.price,
                                 ticketType = boTicketType.ticketType,
                                 firstname = boTicketType.firstname,
                                 lastname = boTicketType.lastname,
                                 email = boTicketType.email,
                                 phone = boTicketType.phone,
                                 birthdate = boTicketType.birthdate,
                                 shortCode = boTicketType.shortCode,
                                 startDate = boTicketType.startDate,
                                 endDate = boTicketType.endDate,
                                 uuid = boTicketType.uuid)
        }

        // Instanciation du BOProduct pour ES
        ProductDao.get(storeCode, boProduct.productFk) map { product =>
          BOProductES(acquittement = boProduct.acquittement,
                      principal = boProduct.principal,
                      price = boProduct.price,
                      product = product,
                      registeredCartItem = boRegisteredCartItems,
                      uuid = boProduct.uuid)

        }
      }

      // Convertion du BODelivery pour ES
      val boDelivery = BODeliveryDao.findByBOCartItem(boCartItem).map { boDelivery =>
        new BODeliveryES(status = boDelivery.status,
                         tracking = boDelivery.tracking,
                         extra = boDelivery.extra,
                         uuid = boDelivery.uuid)
      }

      // Concertion des BOReturnedItem pour ES
      val boReturnedItems = BOReturnedItemDao.findByBOCartItem(boCartItem).map { boReturnedItem =>
        val boReturns = BOReturnDao.findByBOReturnedItem(boReturnedItem).map { boReturn =>
          BOReturnES(motivation = boReturn.motivation,
                     status = boReturn.status,
                     dateCreated = boReturn.dateCreated.toDate,
                     lastUpdated = boReturn.lastUpdated.toDate,
                     uuid = boReturn.uuid)
        }

        BOReturnedItemES(quantity = boReturnedItem.quantity,
                         refunded = boReturnedItem.refunded,
                         totalRefunded = boReturnedItem.totalRefunded,
                         status = boReturnedItem.status,
                         boReturns = boReturns,
                         dateCreated = boReturnedItem.dateCreated.toDate,
                         lastUpdated = boReturnedItem.lastUpdated.toDate,
                         uuid = boReturnedItem.uuid)
      }

      val (principal, secondary) = boProducts.partition(_.principal)

      ProductDao.getProductAndSku(storeCode, boCartItem.ticketTypeFk).map { productAndSku =>
        BOCartItemES(code = boCartItem.code,
                     price = boCartItem.price,
                     tax = boCartItem.tax,
                     endPrice = boCartItem.endPrice,
                     totalPrice = boCartItem.totalPrice,
                     totalEndPrice = boCartItem.totalEndPrice,
                     hidden = boCartItem.hidden,
                     quantity = boCartItem.quantity,
                     startDate = boCartItem.startDate,
                     endDate = boCartItem.endDate,
                     sku = productAndSku._2,
                     principal = principal.head,
                     secondary = secondary,
                     bODelivery = boDelivery,
                     bOReturnedItems = boReturnedItems,
                     uuid = boCartItem.uuid,
                     url = boCartItem.url)
      }
    }

    new BOCartES(transactionUuid = boCart.transactionUuid,
                 buyer = boCart.buyer,
                 xdate = boCart.xdate,
                 price = boCart.price,
                 status = boCart.status,
                 currencyCode = boCart.currencyCode,
                 currencyRate = boCart.currencyRate,
                 cartItems = cartItems,
                 dateCreated = boCart.dateCreated.toDate,
                 lastUpdated = boCart.lastUpdated.toDate,
                 uuid = boCart.uuid)
  }
}

object StoreCartDao {

  import com.sksamuel.elastic4s.ElasticDsl._

  protected def buildIndex(storeCode: String) = s"${storeCode}_cart"

  def findByDataUuidAndUserUuid(storeCode: String,
                                dataUuid: String,
                                userUuid: Option[Mogopay.Document]): Option[StoreCart] = {
    val uuid = dataUuid + "--" + userUuid.getOrElse("None")
    EsClient.load[StoreCart](buildIndex(storeCode), uuid)
  }

  def save(entity: StoreCart): Boolean = {
    EsClient.update[StoreCart](buildIndex(entity.storeCode),
                               entity.copy(expireDate = DateTime.now.plusSeconds(60 * Settings.Cart.Lifetime)),
                               true,
                               false)
  }

  def delete(cart: StoreCart): Unit = {
    EsClient.delete[StoreCart](buildIndex(cart.storeCode), cart.uuid, false)
  }

  def getExpired(index: String, querySize: Int): List[StoreCart] = {
    val req = search in index -> "StoreCart" postFilter (rangeFilter("expireDate") lt "now") from 0 size (querySize)
    EsClient.searchAll[StoreCart](req).toList
  }
}

object DownloadableDao {

  def load(storeCode: String, skuId: String): Option[ChannelBufferBytesReference] = {
    EsClient
      .loadRaw(get id skuId from storeCode -> "downloadable" fields "file.content" fetchSourceContext true) match {
      case Some(response) =>
        if (response.getFields.containsKey("file.content")) {
          Some(response.getField("file.content").getValue.asInstanceOf[ChannelBufferBytesReference])
        } else None
      case _ => None
    }
  }
}

object BOCartESDao {

  def buildIndex(storeCode: String) = s"${storeCode}_bo"

  def load(storeCode: String, uuid: String): Option[BOCartES] = {
    EsClient.load[BOCartES](buildIndex(storeCode), uuid)
  }

  def save(storeCode: String, boCart: BOCartES, refresh: Boolean = false): Boolean = {
    var result = EsClient.update[BOCartES](buildIndex(storeCode), boCart, true, refresh)
    val cartEx = BOCartEx(transactionUuid = boCart.transactionUuid,
                          buyer = boCart.buyer,
                          xdate = boCart.xdate,
                          price = boCart.price,
                          status = boCart.status,
                          currencyCode = boCart.currencyCode,
                          currencyRate = boCart.currencyRate,
                          dateCreated = boCart.dateCreated,
                          lastUpdated = boCart.lastUpdated,
                          uuid = boCart.uuid)
    boCart.cartItems.foreach { it =>
      val cartItemEx = BOCartItemEx(
          code = it.code,
          price = it.price,
          tax = it.tax,
          endPrice = it.endPrice,
          totalPrice = it.totalPrice,
          totalEndPrice = it.totalEndPrice,
          hidden = it.hidden,
          quantity = it.quantity,
          startDate = it.startDate,
          endDate = it.endDate,
          sku = it.sku,
          secondary = it.secondary,
          principal = it.principal,
          bODelivery = it.bODelivery,
          bOReturnedItems = it.bOReturnedItems,
          uuid = it.uuid,
          url = it.url,
          boCart = cartEx
      )
      result = result || EsClient.update[BOCartItemEx](buildIndex(storeCode), cartItemEx, true, refresh)
    }
    result
  }

  def delete(storeCode: String, uuid: String): Unit = {
    EsClient.delete[BOCartES](buildIndex(storeCode), uuid, false)
  }
}

object BOCartDao extends SQLSyntaxSupport[BOCart] with BoService {

  override val tableName = "b_o_cart"

  def apply(rn: ResultName[BOCart])(rs: WrappedResultSet): BOCart =
    BOCart(rs.get(rn.id),
           rs.get(rn.buyer),
           rs.get(rn.companyFk),
           rs.get(rn.currencyCode),
           rs.get(rn.currencyRate),
           rs.get(rn.xdate),
           rs.get(rn.dateCreated),
           rs.get(rn.lastUpdated),
           rs.get(rn.price),
           TransactionStatus.withName(rs.get(rn.status)),
           rs.get(rn.transactionUuid),
           rs.get(rn.uuid),
           rs.get(rn.externalOrderId))

  def load(uuid: String)(implicit session: DBSession): Option[BOCart] = {
    val t = BOCartDao.syntax("t")
    withSQL {
      select.from(BOCartDao as t).where.eq(t.uuid, uuid)
    }.map(BOCartDao(t.resultName)).single().apply()
  }

  def findByTransactionUuid(transactionUuid: String)(implicit session: DBSession = AutoSession): Option[BOCart] = {
    val t = BOCartDao.syntax("t")
    withSQL {
      select.from(BOCartDao as t).where.eq(t.transactionUuid, transactionUuid)
    }.map(BOCartDao(t.resultName)).single().apply()
  }

  def updateStatusAndExternalCode(boCart: BOCart)(implicit session: DBSession): Unit = {
    withSQL {
      update(BOCartDao)
        .set(
            BOCartDao.column.status          -> boCart.status.toString(),
            BOCartDao.column.transactionUuid -> boCart.transactionUuid,
            BOCartDao.column.externalOrderId -> boCart.externalOrderId,
            BOCartDao.column.lastUpdated     -> DateTime.now
        )
        .where
        .eq(BOCartDao.column.id, boCart.id)
    }.update.apply()
  }

  def create(buyer: String, companyId: Long, rate: Currency, price: Long)(implicit session: DBSession): BOCart = {

    val newBoCart = new BOCart(
        newId(),
        buyer,
        companyId,
        rate.code,
        rate.rate,
        DateTime.now,
        DateTime.now,
        DateTime.now,
        price,
        TransactionStatus.PENDING,
        None,
        UUID.randomUUID().toString
    )

    applyUpdate {
      insert
        .into(BOCartDao)
        .namedValues(
            BOCartDao.column.id              -> newBoCart.id,
            BOCartDao.column.buyer           -> newBoCart.buyer,
            BOCartDao.column.companyFk       -> newBoCart.companyFk,
            BOCartDao.column.currencyCode    -> newBoCart.currencyCode,
            BOCartDao.column.currencyRate    -> newBoCart.currencyRate,
            BOCartDao.column.xdate           -> newBoCart.xdate,
            BOCartDao.column.dateCreated     -> newBoCart.dateCreated,
            BOCartDao.column.lastUpdated     -> newBoCart.lastUpdated,
            BOCartDao.column.price           -> newBoCart.price,
            BOCartDao.column.status          -> newBoCart.status.toString(),
            BOCartDao.column.transactionUuid -> newBoCart.transactionUuid,
            BOCartDao.column.uuid            -> newBoCart.uuid
        )
    }

    newBoCart
  }

  def delete(boCart: BOCart)(implicit session: DBSession) = {
    withSQL {
      deleteFrom(BOCartDao).where.eq(BOCartDao.column.id, boCart.id)
    }.update.apply()
  }

  def getShippingAddress(boCart: BOCart)(implicit session: DBSession) : Option[AccountAddress] = {
    val list = BOCartItemDao.findByBOCart(boCart).flatMap{ boCartItem : BOCartItem =>
      BODeliveryDao.findByBOCartItem(boCartItem)
    }.flatMap(_.extra)
    if (list.isEmpty) None
    else Some(JacksonConverter.deserialize[AccountAddress](list.head))
  }
}

object BODeliveryDao extends SQLSyntaxSupport[BODelivery] with BoService {

  override val tableName = "b_o_delivery"

  def apply(rn: ResultName[BODelivery])(rs: WrappedResultSet): BODelivery =
    new BODelivery(rs.get(rn.id),
                   rs.get(rn.bOCartFk),
                   DeliveryStatus.withName(rs.get(rn.status)),
                   rs.get(rn.tracking),
                   rs.get(rn.extra),
                   rs.get(rn.dateCreated),
                   rs.get(rn.lastUpdated),
                   rs.get(rn.uuid))

  def create(boCart: BOCart, extra: Option[String])(implicit session: DBSession): BODelivery = {
    val newBODelivery = new BODelivery(
        id = newId(),
        bOCartFk = boCart.id,
        status = DeliveryStatus.NOT_STARTED,
        extra = extra,
        uuid = UUID.randomUUID().toString
    )

    applyUpdate {
      insert
        .into(BODeliveryDao)
        .namedValues(
            BODeliveryDao.column.id          -> newBODelivery.id,
            BODeliveryDao.column.bOCartFk    -> newBODelivery.bOCartFk,
            BODeliveryDao.column.status      -> newBODelivery.status.toString(),
            BODeliveryDao.column.tracking    -> newBODelivery.tracking,
            BODeliveryDao.column.extra       -> newBODelivery.extra,
            BODeliveryDao.column.dateCreated -> newBODelivery.dateCreated,
            BODeliveryDao.column.lastUpdated -> newBODelivery.lastUpdated,
            BODeliveryDao.column.uuid        -> newBODelivery.uuid
        )
    }

    newBODelivery
  }

  def save(boDelivery: BODelivery)(implicit session: DBSession): BODelivery = {
    val updatedboDelivery = boDelivery.copy(lastUpdated = DateTime.now())

    withSQL {
      update(BODeliveryDao)
        .set(
            BODeliveryDao.column.id          -> updatedboDelivery.id,
            BODeliveryDao.column.bOCartFk    -> updatedboDelivery.bOCartFk,
            BODeliveryDao.column.status      -> updatedboDelivery.status.toString(),
            BODeliveryDao.column.tracking    -> updatedboDelivery.tracking,
            BODeliveryDao.column.extra       -> updatedboDelivery.extra,
            BODeliveryDao.column.dateCreated -> updatedboDelivery.dateCreated,
            BODeliveryDao.column.lastUpdated -> updatedboDelivery.lastUpdated,
            BODeliveryDao.column.uuid        -> updatedboDelivery.uuid
        )
        .where
        .eq(BOReturnedItemDao.column.id, updatedboDelivery.id)
    }.update.apply()

    updatedboDelivery
  }

  def findByBOCartItem(boCartItem: BOCartItem)(implicit session: DBSession): Option[BODelivery] = {
    val t = BODeliveryDao.syntax("t")
    withSQL {
      select.from(BODeliveryDao as t).where.eq(t.id, boCartItem.bODeliveryFk)
    }.map(BODeliveryDao(t.resultName)).single().apply()
  }

  def delete(boDeliveryId: Long)(implicit session: DBSession) = {
    withSQL {
      deleteFrom(BODeliveryDao).where.eq(BODeliveryDao.column.id, boDeliveryId)
    }.update.apply()
  }
}

object BOReturnedItemDao extends SQLSyntaxSupport[BOReturnedItem] with BoService {

  override val tableName = "b_o_returned_item"

  def apply(rn: ResultName[BOReturnedItem])(rs: WrappedResultSet): BOReturnedItem =
    new BOReturnedItem(rs.get(rn.id),
                       rs.get(rn.bOCartItemFk),
                       rs.get(rn.quantity),
                       rs.get(rn.refunded),
                       rs.get(rn.totalRefunded),
                       ReturnedItemStatus.withName(rs.get(rn.status)),
                       rs.get(rn.dateCreated),
                       rs.get(rn.lastUpdated),
                       rs.get(rn.uuid))

  def load(boReturnedItemUuid: String)(implicit session: DBSession = AutoSession): Option[BOReturnedItem] = {
    val t = BOReturnedItemDao.syntax("t")
    withSQL {
      select.from(BOReturnedItemDao as t).where.eq(t.uuid, boReturnedItemUuid)
    }.map(BOReturnedItemDao(t.resultName)).single().apply()
  }

  def findByBOCartItem(boCartItem: BOCartItem)(implicit session: DBSession): List[BOReturnedItem] = {
    val t = BOReturnedItemDao.syntax("t")
    withSQL {
      select.from(BOReturnedItemDao as t).where.eq(t.bOCartItemFk, boCartItem.id)
    }.map(BOReturnedItemDao(t.resultName)).list().apply()
  }

  def create(boReturnedItem: BOReturnedItem)(implicit session: DBSession): BOReturnedItem = {
    applyUpdate {
      insert
        .into(BOReturnedItemDao)
        .namedValues(
            BOReturnedItemDao.column.id            -> boReturnedItem.id,
            BOReturnedItemDao.column.bOCartItemFk  -> boReturnedItem.bOCartItemFk,
            BOReturnedItemDao.column.quantity      -> boReturnedItem.quantity,
            BOReturnedItemDao.column.refunded      -> boReturnedItem.refunded,
            BOReturnedItemDao.column.totalRefunded -> boReturnedItem.totalRefunded,
            BOReturnedItemDao.column.status        -> boReturnedItem.status.toString(),
            BOReturnedItemDao.column.dateCreated   -> boReturnedItem.dateCreated,
            BOReturnedItemDao.column.lastUpdated   -> boReturnedItem.lastUpdated,
            BOReturnedItemDao.column.uuid          -> boReturnedItem.uuid
        )
    }

    boReturnedItem
  }

  def save(boReturnedItem: BOReturnedItem)(implicit session: DBSession): BOReturnedItem = {
    val updatedBoReturnedItem = boReturnedItem.copy(lastUpdated = DateTime.now())

    withSQL {
      update(BOReturnedItemDao)
        .set(
            BOReturnedItemDao.column.refunded      -> updatedBoReturnedItem.refunded,
            BOReturnedItemDao.column.totalRefunded -> updatedBoReturnedItem.totalRefunded,
            BOReturnedItemDao.column.status        -> updatedBoReturnedItem.status.toString(),
            BOReturnedItemDao.column.lastUpdated   -> updatedBoReturnedItem.lastUpdated
        )
        .where
        .eq(BOReturnedItemDao.column.id, updatedBoReturnedItem.id)
    }.update.apply()

    updatedBoReturnedItem
  }
}

object BOReturnDao extends SQLSyntaxSupport[BOReturn] with BoService {

  override val tableName = "b_o_return"

  def apply(rn: ResultName[BOReturn])(rs: WrappedResultSet): BOReturn =
    new BOReturn(rs.get(rn.id),
                 rs.get(rn.bOReturnedItemFk),
                 rs.get(rn.motivation),
                 ReturnStatus.withName(rs.get(rn.status)),
                 rs.get(rn.dateCreated),
                 rs.get(rn.lastUpdated),
                 rs.get(rn.uuid))

  def findByBOReturnedItem(boReturnedItem: BOReturnedItem)(implicit session: DBSession = AutoSession): List[BOReturn] = {
    val t = BOReturnDao.syntax("t")
    withSQL {
      select.from(BOReturnDao as t).where.eq(t.bOReturnedItemFk, boReturnedItem.id).orderBy(t.dateCreated).desc
    }.map(BOReturnDao(t.resultName)).list().apply()
  }

  def create(boReturn: BOReturn)(implicit session: DBSession): BOReturn = {
    applyUpdate {
      insert
        .into(BOReturnDao)
        .namedValues(
            BOReturnDao.column.id               -> boReturn.id,
            BOReturnDao.column.bOReturnedItemFk -> boReturn.bOReturnedItemFk,
            BOReturnDao.column.motivation       -> boReturn.motivation,
            BOReturnDao.column.status           -> boReturn.status.toString(),
            BOReturnDao.column.dateCreated      -> boReturn.dateCreated,
            BOReturnDao.column.lastUpdated      -> boReturn.lastUpdated,
            BOReturnDao.column.uuid             -> boReturn.uuid
        )
    }

    boReturn
  }
}

object BOCartItemDao extends SQLSyntaxSupport[BOCartItem] with BoService {

  override val tableName = "b_o_cart_item"

  def apply(rn: ResultName[BOCartItem])(rs: WrappedResultSet): BOCartItem =
    new BOCartItem(rs.get(rn.id),
                   rs.get(rn.code),
                   rs.get(rn.price),
                   rs.get(rn.tax),
                   rs.get(rn.endPrice),
                   rs.get(rn.totalPrice),
                   rs.get(rn.totalEndPrice),
                   rs.get(rn.hidden),
                   rs.get(rn.quantity),
                   rs.get(rn.startDate),
                   rs.get(rn.endDate),
                   rs.get(rn.ticketTypeFk),
                   rs.get(rn.bOCartFk),
                   rs.get(rn.bODeliveryFk),
                   rs.get(rn.dateCreated),
                   rs.get(rn.lastUpdated),
                   rs.get(rn.uuid),
                   rs.get(rn.url),
                   rs.get(rn.externalCode))

  def create(sku: Mogobiz.Sku,
             cartItem: StoreCartItemWithPrice,
             boCart: BOCart,
             bODelivery: Option[BODelivery],
             boProductId: Long)(implicit session: DBSession): BOCartItem = {
    val newBOCartItem = new BOCartItem(
        newId(),
        "SALE_" + boCart.id + "_" + boProductId,
        cartItem.price,
        cartItem.tax.getOrElse(0f).toDouble,
        cartItem.saleEndPrice.getOrElse(cartItem.salePrice),
        cartItem.saleTotalPrice,
        cartItem.saleTotalEndPrice.getOrElse(cartItem.saleTotalPrice),
        false,
        cartItem.quantity,
        cartItem.cartItem.startDate,
        cartItem.cartItem.endDate,
        sku.id,
        boCart.id,
        if (bODelivery.isDefined) Some(bODelivery.get.id) else None,
        DateTime.now,
        DateTime.now,
        cartItem.cartItem.id,
        cartItem.cartItem.productUrl,
        ExternalCode.toString(cartItem.cartItem.externalCode)
    )

    applyUpdate {
      insert
        .into(BOCartItemDao)
        .namedValues(
            BOCartItemDao.column.id            -> newBOCartItem.id,
            BOCartItemDao.column.code          -> newBOCartItem.code,
            BOCartItemDao.column.price         -> newBOCartItem.price,
            BOCartItemDao.column.tax           -> newBOCartItem.tax,
            BOCartItemDao.column.endPrice      -> newBOCartItem.endPrice,
            BOCartItemDao.column.totalPrice    -> newBOCartItem.totalPrice,
            BOCartItemDao.column.totalEndPrice -> newBOCartItem.totalEndPrice,
            BOCartItemDao.column.hidden        -> newBOCartItem.hidden,
            BOCartItemDao.column.quantity      -> newBOCartItem.quantity,
            BOCartItemDao.column.startDate     -> newBOCartItem.startDate,
            BOCartItemDao.column.endDate       -> newBOCartItem.endDate,
            BOCartItemDao.column.ticketTypeFk  -> newBOCartItem.ticketTypeFk,
            BOCartItemDao.column.bOCartFk      -> newBOCartItem.bOCartFk,
            BOCartItemDao.column.bODeliveryFk  -> newBOCartItem.bODeliveryFk,
            BOCartItemDao.column.dateCreated   -> newBOCartItem.dateCreated,
            BOCartItemDao.column.lastUpdated   -> newBOCartItem.lastUpdated,
            BOCartItemDao.column.uuid          -> newBOCartItem.uuid,
            BOCartItemDao.column.url           -> newBOCartItem.url,
            BOCartItemDao.column.externalCode  -> newBOCartItem.externalCode
        )
    }

    sql"insert into b_o_cart_item_b_o_product(b_o_products_fk, boproduct_id) values(${newBOCartItem.id},$boProductId)".update
      .apply()

    newBOCartItem
  }

  def findByBOCart(boCart: BOCart)(implicit session: DBSession = AutoSession): List[BOCartItem] = {
    val t = BOCartItemDao.syntax("t")
    withSQL {
      select.from(BOCartItemDao as t).where.eq(t.bOCartFk, boCart.id)
    }.map(BOCartItemDao(t.resultName)).list().apply()
  }

  def findByBOReturnedItem(boReturnedItem: BOReturnedItem)(
      implicit session: DBSession = AutoSession): Option[BOCartItem] = {
    val t = BOCartItemDao.syntax("t")
    withSQL {
      select.from(BOCartItemDao as t).where.eq(t.id, boReturnedItem.bOCartItemFk)
    }.map(BOCartItemDao(t.resultName)).single().apply()
  }

  def load(boCartItemUuid: String)(implicit session: DBSession = AutoSession): Option[BOCartItem] = {
    val t = BOCartItemDao.syntax("t")
    withSQL {
      select.from(BOCartItemDao as t).where.eq(t.uuid, boCartItemUuid)
    }.map(BOCartItemDao(t.resultName)).single().apply()
  }

  def getBOProducts(boCartItem: BOCartItem)(implicit session: DBSession = AutoSession): List[BOProduct] = {
    sql"select p.* from b_o_cart_item_b_o_product ass inner join b_o_product p on ass.boproduct_id=p.id where b_o_products_fk=${boCartItem.id}"
      .map(rs => BOProductDao(rs))
      .list()
      .apply()
  }

  def delete(boCartItem: BOCartItem)(implicit session: DBSession) = {
    sql"delete from b_o_cart_item_b_o_product where b_o_products_fk=${boCartItem.id}".update.apply()

    val result = withSQL {
      deleteFrom(BOCartItemDao).where.eq(BOCartItemDao.column.id, boCartItem.id)
    }.update.apply()
    if (boCartItem.bODeliveryFk.isDefined) BODeliveryDao.delete(boCartItem.bODeliveryFk.get) else result
  }
}

object BOTicketTypeDao extends SQLSyntaxSupport[BOTicketType] with BoService {

  override val tableName = "b_o_ticket_type"

  def apply(rn: ResultName[BOTicketType])(rs: WrappedResultSet): BOTicketType =
    new BOTicketType(rs.get(rn.id),
                     rs.get(rn.quantity),
                     rs.get(rn.price),
                     rs.get(rn.shortCode),
                     rs.get(rn.ticketType),
                     rs.get(rn.firstname),
                     rs.get(rn.lastname),
                     rs.get(rn.email),
                     rs.get(rn.phone),
                     rs.get(rn.age),
                     rs.get(rn.birthdate),
                     rs.get(rn.startDate),
                     rs.get(rn.endDate),
                     rs.get(rn.qrcode),
                     rs.get(rn.qrcodeContent),
                     rs.get(rn.bOProductFk),
                     rs.get(rn.dateCreated),
                     rs.get(rn.lastUpdated),
                     rs.get(rn.uuid))

  def create(boTicketId: Long,
             sku: Sku,
             cartItem: StoreCartItemWithPrice,
             registeredCartItem: Render.RegisteredCartItem,
             shortCode: Option[String],
             qrCode: Option[String],
             qrCodeContent: Option[String],
             boProductId: Long)(implicit session: DBSession): BOTicketType = {
    val newBOTicketType = new BOTicketType(
        boTicketId,
        1, // Un seul ticket par bénéficiaire
        cartItem.saleTotalEndPrice.getOrElse(cartItem.saleTotalPrice),
        shortCode,
        Some(sku.name),
        registeredCartItem.firstname,
        registeredCartItem.lastname,
        Some(registeredCartItem.email),
        registeredCartItem.phone,
        Utils.computeAge(registeredCartItem.birthdate),
        registeredCartItem.birthdate,
        cartItem.cartItem.startDate,
        cartItem.cartItem.endDate,
        qrCode,
        qrCodeContent,
        boProductId,
        DateTime.now,
        DateTime.now,
        UUID.randomUUID().toString
    )

    applyUpdate {
      insert
        .into(BOTicketTypeDao)
        .namedValues(
            BOTicketTypeDao.column.uuid          -> newBOTicketType.uuid,
            BOTicketTypeDao.column.id            -> newBOTicketType.id,
            BOTicketTypeDao.column.shortCode     -> newBOTicketType.shortCode,
            BOTicketTypeDao.column.quantity      -> newBOTicketType.quantity,
            BOTicketTypeDao.column.price         -> newBOTicketType.price,
            BOTicketTypeDao.column.ticketType    -> newBOTicketType.ticketType,
            BOTicketTypeDao.column.firstname     -> newBOTicketType.firstname,
            BOTicketTypeDao.column.lastname      -> newBOTicketType.lastname,
            BOTicketTypeDao.column.email         -> newBOTicketType.email,
            BOTicketTypeDao.column.phone         -> newBOTicketType.phone,
            BOTicketTypeDao.column.age           -> newBOTicketType.age,
            BOTicketTypeDao.column.birthdate     -> newBOTicketType.birthdate,
            BOTicketTypeDao.column.startDate     -> newBOTicketType.startDate,
            BOTicketTypeDao.column.endDate       -> newBOTicketType.endDate,
            BOTicketTypeDao.column.qrcode        -> newBOTicketType.qrcode,
            BOTicketTypeDao.column.qrcodeContent -> newBOTicketType.qrcodeContent,
            BOTicketTypeDao.column.dateCreated   -> newBOTicketType.dateCreated,
            BOTicketTypeDao.column.lastUpdated   -> newBOTicketType.lastUpdated,
            BOTicketTypeDao.column.bOProductFk   -> newBOTicketType.bOProductFk
        )
    }

    newBOTicketType
  }

  def findByBOProduct(boProductId: Long)(implicit session: DBSession): List[BOTicketType] = {
    val t = BOTicketTypeDao.syntax("t")
    withSQL {
      select.from(BOTicketTypeDao as t).where.eq(t.bOProductFk, boProductId)
    }.map(BOTicketTypeDao(t.resultName)).list().apply()
  }

  def delete(boTicketType: BOTicketType)(implicit session: DBSession) {
    withSQL {
      deleteFrom(BOTicketTypeDao).where.eq(BOTicketTypeDao.column.id, boTicketType.id)
    }.update.apply()
  }
}

object BOProductDao extends SQLSyntaxSupport[BOProduct] with BoService {

  override val tableName = "b_o_product"

  def apply(rs: WrappedResultSet): BOProduct =
    new BOProduct(rs.long("id"),
                  rs.boolean("acquittement"),
                  rs.long("price"),
                  rs.boolean("principal"),
                  rs.long("product_fk"),
                  rs.get("date_created"),
                  rs.get("last_updated"),
                  rs.get("uuid"))

  def create(price: Long, principal: Boolean, productId: Long)(implicit session: DBSession): BOProduct = {
    val newBOProduct = new BOProduct(
        newId(),
        false,
        price,
        principal,
        productId,
        DateTime.now(),
        DateTime.now(),
        UUID.randomUUID().toString
    )

    var boProductId = 0
    applyUpdate {
      insert
        .into(BOProductDao)
        .namedValues(
            BOProductDao.column.id           -> newBOProduct.id,
            BOProductDao.column.acquittement -> newBOProduct.acquittement,
            BOProductDao.column.price        -> newBOProduct.price,
            BOProductDao.column.principal    -> newBOProduct.principal,
            BOProductDao.column.productFk    -> newBOProduct.productFk,
            BOProductDao.column.dateCreated  -> newBOProduct.dateCreated,
            BOProductDao.column.lastUpdated  -> newBOProduct.lastUpdated,
            BOProductDao.column.uuid         -> newBOProduct.uuid
        )
    }

    newBOProduct
  }

  def delete(boProduct: BOProduct)(implicit session: DBSession) {
    withSQL {
      deleteFrom(BOProductDao).where.eq(BOProductDao.column.id, boProduct.id)
    }.update.apply()
  }
}
