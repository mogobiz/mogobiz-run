/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import java.io.ByteArrayOutputStream
import java.util.{UUID, Locale}

import akka.actor.Props
import com.mogobiz.es.EsClient
import com.mogobiz.pay.model.Mogopay
import com.mogobiz.run.config.MogobizHandlers._
import com.mogobiz.run.actors.EsUpdateActor.{ProductStockAvailabilityUpdateRequest, StockUpdateRequest}
import com.mogobiz.run.actors.{EsUpdateActor, ActorSystemLocator}
import com.mogobiz.run.config.Settings
import com.mogobiz.run.dashboard.Dashboard
import com.mogobiz.run.es._
import com.mogobiz.run.exceptions._
import com.mogobiz.run.handlers.EmailHandler.Mail
import com.mogobiz.run.implicits.Json4sProtocol
import com.mogobiz.run.learning.{CartRegistration, UserActionRegistration}
import com.mogobiz.run.model.Learning.UserAction
import com.mogobiz.run.model.Mogobiz._
import com.mogobiz.run.model.ES.{BOCart => BOCartES, BOCartItem => BOCartItemES, BODelivery => BODeliveryES, BOReturnedItem => BOReturnedItemES, BOReturn => BOReturnES, BOProduct => BOProductES, BORegisteredCartItem => BORegisteredCartItemES, BOCartItemEx, BOCartEx}
import com.mogobiz.run.model.Render.{Coupon, RegisteredCartItem, CartItem, Cart}
import com.mogobiz.pay.common.{Cart => CartPay, CartItem => CartItemPay, Coupon => CouponPay, Shipping => ShippingPay, RegisteredCartItem => RegisteredCartItemPay, CompanyAddress, CartRate}
import com.mogobiz.run.model.RequestParameters._
import com.mogobiz.run.model._
import com.mogobiz.run.services.RateBoService
import com.mogobiz.run.utils.Utils
import com.mogobiz.utils.{QRCodeUtils, SymmetricCrypt}
import com.sksamuel.elastic4s.ElasticDsl.{get, tuple2indexestypes}
import com.sun.org.apache.xml.internal.security.utils.Base64
import org.elasticsearch.common.bytes.ChannelBufferBytesReference
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.json4s.ext.JodaTimeSerializers
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization._
import org.json4s.{FieldSerializer, DefaultFormats, Formats}
import scalikejdbc._

class CartHandler {
  val rateService = RateBoService

  /**
   * Permet de récupérer le contenu du panier<br/>
   * Si le panier n'existe pas, il est créé<br/>
   * @param storeCode
   * @param uuid
   * @param params
   * @param accountId
   * @return
   */
  def queryCartInit(storeCode: String, uuid: String, params: CartParameters, accountId: Option[Mogopay.Document]): Map[String, Any] = {
    val locale = _buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val cart = _initCart(storeCode, uuid, accountId)

    val computeCart = _computeStoreCart(cart, params.country, params.state)
    _renderCart(computeCart, currency, locale)
  }

  /**
   * Vide le contenu du panier
   * @param storeCode
   * @param uuid
   * @param params
   * @param accountId
   * @return
   */
  def queryCartClear(storeCode: String, uuid: String, params: CartParameters, accountId: Option[Mogopay.Document]): Map[String, Any] = {
    val locale = _buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val cart = _initCart(storeCode, uuid, accountId)

    val updatedCart = _clearCart(cart)
    val computeCart = _computeStoreCart(updatedCart, params.country, params.state)
    _renderCart(computeCart, currency, locale)
  }

  /**
   * Ajoute un item au panier
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
  def queryCartItemAdd(storeCode: String, uuid: String, params: CartParameters, cmd: AddCartItemRequest, accountId: Option[Mogopay.Document]): Map[String, Any] = {
    val locale = _buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val cart = _initCart(storeCode, uuid, accountId)

    val productAndSku = ProductDao.getProductAndSku(cart.storeCode, cmd.skuId)
    if (productAndSku.isEmpty) throw new NotFoundException("unknown sku")

    if (!_checkProductAndSkuSalabality(productAndSku.get))
      throw new UnsaleableProductException()

    val product = productAndSku.get._1
    val sku = productAndSku.get._2
    val startEndDate = Utils.verifyAndExtractStartEndDate(product, sku, cmd.dateTime)
    val startDate = if (startEndDate.isDefined) Some(startEndDate.get._1) else None
    val endDate = if (startEndDate.isDefined) Some(startEndDate.get._2) else None

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

    val salePrice = if (sku.salePrice > 0) sku.salePrice else sku.price
    val indexEs = EsClient.getUniqueIndexByAlias(storeCode).getOrElse(storeCode)
    val cartItem = StoreCartItem(indexEs, newCartItemId, product.id, product.name, cmd.productUrl, product.xtype, product.calendarType, sku.id, sku.name, cmd.quantity,
      sku.price, salePrice, startDate, endDate, registeredItems, product.shipping, None, None)

    val updatedCart = _addCartItemIntoCart(_unvalidateCart(cart), cartItem)
    StoreCartDao.save(updatedCart)

    val computeCart = _computeStoreCart(updatedCart, params.country, params.state)
    _renderCart(computeCart, currency, locale)
  }

  private def _checkProductAndSkuSalabality(productAndSku: (Mogobiz.Product, Mogobiz.Sku)): Boolean = {
    val now = DateTime.now().toLocalDate
    val sku = productAndSku._2
    val skuStartDate = sku.startDate.getOrElse(DateTime.now()).toLocalDate
    val skuEndDate = sku.stopDate.getOrElse(DateTime.now()).toLocalDate

    !skuStartDate.isAfter(now) && !skuEndDate.isBefore(now)
  }

  /**
   * Valide le panier (et décrémente les stocks)
   * @param storeCode
   * @param uuid
   * @param params
   * @param accountId
   * @return
   */
  def queryCartValidate(storeCode: String, uuid: String, params: CartParameters, accountId: Option[Mogopay.Document]): Map[String, Any] = {
    val locale = _buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val cart = _initCart(storeCode, uuid, accountId)

    val updatedCart = _validateCart(cart)
    val computeCart = _computeStoreCart(updatedCart, params.country, params.state)
    _renderCart(computeCart, currency, locale)
  }

  /**
   * Met à jour la quantité d'un item du panier
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
  def queryCartItemUpdate(storeCode: String, uuid: String, cartItemId: String, params: CartParameters, cmd: UpdateCartItemRequest, accountId: Option[Mogopay.Document]): Map[String, Any] = {
    val locale = _buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val cart = _initCart(storeCode, uuid, accountId)

    val optCartItem = cart.cartItems.find { item => item.id == cartItemId }
    if (optCartItem.isDefined) {
      val existCartItem = optCartItem.get
      val updatedCart = if (ProductType.SERVICE != existCartItem.xtype && existCartItem.quantity != cmd.quantity) {
        val productAndSku = ProductDao.getProductAndSku(cart.storeCode, existCartItem.skuId)
        val product = productAndSku.get._1
        val sku = productAndSku.get._2

        if (!stockHandler.checkStock(cart.storeCode, product, sku, cmd.quantity, existCartItem.startDate)) {
          throw new InsufficientStockCartItemException()
        }

        // Modification du panier
        val newCartItems = existCartItem.copy(quantity = cmd.quantity) :: Utils.remove(cart.cartItems, existCartItem)
        val updatedCart = _unvalidateCart(cart).copy(cartItems = newCartItems)

        StoreCartDao.save(updatedCart)
        updatedCart
      } else {
        println("silent op")
        cart
      }
      val computeCart = _computeStoreCart(updatedCart, params.country, params.state)
      _renderCart(computeCart, currency, locale)
    } else throw new NotFoundException("")
  }

  /**
   * Supprime un item du panier
   * @param storeCode
   * @param uuid
   * @param cartItemId
   * @param params
   * @param accountId
   * @return
   */
  def queryCartItemRemove(storeCode: String, uuid: String, cartItemId: String, params: CartParameters, accountId: Option[Mogopay.Document]): Map[String, Any] = {
    val locale = _buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val cart = _initCart(storeCode, uuid, accountId)

    val optCartItem = cart.cartItems.find { item => item.id == cartItemId }
    val updatedCart = if (optCartItem.isDefined) {
      val existCartItem = optCartItem.get

      val newCartItems = Utils.remove(cart.cartItems, existCartItem)
      val updatedCart = _unvalidateCart(cart).copy(cartItems = newCartItems)

      StoreCartDao.save(updatedCart)
      updatedCart
    }
    else cart
    val computeCart = _computeStoreCart(updatedCart, params.country, params.state)
    _renderCart(computeCart, currency, locale)
  }

  /**
   * Ajoute un coupon au panier
   * @param storeCode
   * @param uuid
   * @param couponCode
   * @param params
   * @param accountId
   * @return
   */
  @throws[DuplicateException]
  @throws[InsufficientStockCouponException]
  def queryCartCouponAdd(storeCode: String, uuid: String, couponCode: String, params: CouponParameters, accountId: Option[Mogopay.Document]): Map[String, Any] = {
    val locale = _buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val cart = _initCart(storeCode, uuid, accountId)

    val optCoupon = CouponDao.findByCode(cart.storeCode, couponCode)
    if (optCoupon.isDefined) {
      val coupon = optCoupon.get
      if (!coupon.anonymous) {
        if (cart.coupons.exists { c => couponCode == c.code }) {
          throw new DuplicateException("")
        } else if (!couponHandler.consumeCoupon(cart.storeCode, coupon)) {
          throw new InsufficientStockCouponException()
        }
        else {
          val newCoupon = StoreCoupon(coupon.id, coupon.code)

          val coupons = newCoupon :: cart.coupons
          val updatedCart = _unvalidateCart(cart).copy(coupons = coupons)
          StoreCartDao.save(updatedCart)

          val computeCart = _computeStoreCart(updatedCart, params.country, params.state)
          _renderCart(computeCart, currency, locale)
        }
      }
      else {
        throw new NotFoundException("")
      }
    }
    else {
      throw new NotFoundException("")
    }
  }

  /**
   * Supprime un coupon du panier
   * @param storeCode
   * @param uuid
   * @param couponCode
   * @param params
   * @param accountId
   * @return
   */
  @throws[NotFoundException]
  def queryCartCouponDelete(storeCode: String, uuid: String, couponCode: String, params: CouponParameters, accountId: Option[Mogopay.Document]): Map[String, Any] = {
    val locale = _buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val cart = _initCart(storeCode, uuid, accountId)

    val optCoupon = CouponDao.findByCode(cart.storeCode, couponCode)
    if (optCoupon.isDefined) {
      val existCoupon = cart.coupons.find { c => couponCode == c.code }
      if (existCoupon.isEmpty) throw new NotFoundException("")
      else {
        couponHandler.releaseCoupon(cart.storeCode, optCoupon.get)

        // reprise des items existants sauf celui à supprimer
        val coupons = Utils.remove(cart.coupons, existCoupon.get)
        val updatedCart = _unvalidateCart(cart).copy(coupons = coupons)
        StoreCartDao.save(updatedCart)

        val computeCart = _computeStoreCart(updatedCart, params.country, params.state)
        _renderCart(computeCart, currency, locale)
      }
    }
    else throw new NotFoundException("")
  }

  /**
   * Prépare le panier pour le paiement
   * @param storeCode
   * @param uuid
   * @param params
   * @param accountId
   * @return
   */
  @throws[InsufficientStockException]
  def queryCartPaymentPrepare(storeCode: String, uuid: String, params: PrepareTransactionParameters, accountId: Option[Mogopay.Document]): Map[String, Any] = {
    val locale = _buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val cart = _initCart(storeCode, uuid, accountId)

    val company = CompanyDao.findByCode(cart.storeCode)

    // Validation du panier au cas où il ne l'était pas déjà
    val valideCart = _validateCart(cart).copy(countryCode = params.country, stateCode = params.state, rate = Some(currency))
    StoreCartDao.save(valideCart)

    // Suppression de la transaction en cours (si elle existe). Une nouvelle transaction sera créé
    val cartWithoutBOCart = _deletePendingBOCart(valideCart)

    // Calcul des données du panier
    val cartTTC = _computeStoreCart(cartWithoutBOCart, params.country, params.state)

    // Création de la transaction
    val updatedCart = _createBOCart(cartWithoutBOCart, cartTTC, currency, params.buyer, company.get, params.shippingAddress)

    StoreCartDao.save(updatedCart)

    val renderedCart = _renderTransactionCart(updatedCart, cartTTC, currency, locale)
    Map(
      "amount" -> rateService.calculateAmount(cartTTC.finalPrice, currency),
      "currencyCode" -> currency.code,
      "currencyRate" -> currency.rate.doubleValue(),
      "transactionExtra" -> renderedCart,
      "cartProvider" -> "mogobizRun",
      "cartKeys" -> s"$storeCode|||$uuid|||${accountId.getOrElse("")}"
    )
  }

  def getCartForPay(storeCode: String, uuid: String, accountId: Option[String]): CartPay = {
    val cart = _initCart(storeCode, uuid, accountId)

    // Calcul des données du panier
    val cartTTC = _computeStoreCart(cart, cart.countryCode, cart.stateCode)

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
        }.flatten
      )
    }

    val shippingRulePrice = computeShippingRulePrice(cart.storeCode, cart.countryCode, cartTTC.finalPrice)

    transformCartForCartPay(companyAddress, cartTTC, cart.rate.get, shippingRulePrice)
  }

  private def computeShippingRulePrice(storeCode: String, countryCode: Option[String], cartPice: Long): Option[Long] = {
    countryCode.map { country =>
      val shippingRules = ShippingRuleDao.findByCompany(storeCode)
      val v = shippingRules.find(sr => sr.countryCode == country && sr.minAmount <= cartPice && cartPice <= sr.maxAmount)
      v.map {
        _.price
      }
    }.getOrElse(None)
  }

  /**
   * Complète le panier après un paiement réalisé avec succès. Le contenu du panier est envoyé par mail comme justificatif
   * et un nouveau panier est créé
   * @param storeCode
   * @param uuid
   * @param params
   * @param accountId
   */
  def queryCartPaymentCommit(storeCode: String, uuid: String,
                             params: CommitTransactionParameters, accountId: Option[Mogopay.Document]): Unit = {
    val locale = _buildLocal(params.lang, params.country)

    val cart = _initCart(storeCode, uuid, accountId)

    val productIds = cart.cartItems.map { item =>
      UserActionRegistration.register(storeCode, uuid, item.productId.toString, UserAction.Purchase)
      item.productId.toString
    }
    CartRegistration.register(storeCode, uuid, productIds)

    val transactionCart = cart.copy(transactionUuid = Some(params.transactionUuid))
    BOCartDao.load(transactionCart.boCartUuid.get) match {
      case Some(boCart) =>
        DB localTx { implicit session =>
          val transactionBoCart = boCart.copy(transactionUuid = Some(params.transactionUuid), status = TransactionStatus.COMPLETE)
          BOCartDao.updateStatus(transactionBoCart)
          exportBOCartIntoES(storeCode, transactionBoCart)

          cart.cartItems.foreach { cartItem =>
            val productAndSku = ProductDao.getProductAndSku(transactionCart.storeCode, cartItem.skuId)
            val product = productAndSku.get._1
            val sku = productAndSku.get._2
            salesHandler.incrementSales(transactionCart.storeCode, product, sku, cartItem.quantity)
          }
          val updatedCart = StoreCart(storeCode = transactionCart.storeCode, dataUuid = transactionCart.dataUuid, userUuid = transactionCart.userUuid)
          StoreCartDao.save(updatedCart)

          accountId.map(Dashboard.index(storeCode, boCartToESBOCart(storeCode, transactionBoCart), _))

          _notifyCartCommit(transactionCart.storeCode, boCart.buyer, transactionCart, locale)
        }
      case None => throw new IllegalArgumentException("Unabled to retrieve Cart " + cart.uuid + " into BO. It has not been initialized or has already been validated")
    }

  }

  /**
   * Met à jour le panier suite à l'abandon ou l'échec du paiement
   * @param storeCode
   * @param uuid
   * @param params
   * @param accountId
   * @return
   */
  def queryCartPaymentCancel(storeCode: String, uuid: String, params: CancelTransactionParameters, accountId: Option[Mogopay.Document]): Map[String, Any] = {
    val locale = _buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val cart = _initCart(storeCode, uuid, accountId)

    val updatedCart = _cancelCart(_unvalidateCart(cart))
    val computeCart = _computeStoreCart(updatedCart, params.country, params.state)
    _renderCart(computeCart, currency, locale)
  }

  /**
   * Supprime tous les paniers expirés pour l'index fourni
   */
  def cleanup(index: String, querySize: Int): Unit = {
    StoreCartDao.getExpired(index, querySize).map { cart =>
      try {
        StoreCartDao.delete(_clearCart(_removeAllUnsalabledItem(cart)))
      }
      catch {
        case t: Throwable => t.printStackTrace()
      }
    }
  }

  /**
   * Crée un BOCart à partir des données fournis en générant si nécessaire les QRCode correspondant. Renvoi
   * un panier en lien avec le BOCart créé
   * @param storeCart
   * @param cart
   * @param rate
   * @param buyer
   * @param company
   * @param shippingAddress
   * @return
   */
  private def _createBOCart(storeCart: StoreCart, cart: Render.Cart, rate: Currency, buyer: String, company: Company, shippingAddress: String): StoreCart = {
    val boCartAndStoreCart = DB localTx { implicit session => {
      val storeCode = storeCart.storeCode
      val boCart = BOCartDao.create(buyer, company.id, rate, cart.finalPrice)

      val newStoreCartItems = cart.cartItemVOs.map { cartItem =>
        val storeCartItem = storeCart.cartItems.find(i => (i.productId == cartItem.productId) && (i.skuId == cartItem.skuId)).get

        val productAndSku = ProductDao.getProductAndSku(storeCode, cartItem.skuId)
        val product = productAndSku.get._1
        val sku = productAndSku.get._2

        // Création du BOProduct correspondant au produit principal
        val boProduct = BOProductDao.create(cartItem.saleTotalEndPrice.getOrElse(cartItem.saleTotalPrice), true, cartItem.productId)

        val newStoreRegistedCartItems = cartItem.registeredCartItemVOs.map { registeredCartItem =>
          val storeRegistedCartItem = storeCartItem.registeredCartItems.find(r => r.email == registeredCartItem.email).get
          val boTicketId = BOTicketTypeDao.newId()

          val shortCodeAndQrCode = product.xtype match {
            case ProductType.SERVICE => {
              val startDateStr = cartItem.startDate.map(d => d.toString(DateTimeFormat.forPattern("dd/MM/yyyy HH:mm")))
              val shortCode = "P" + boProduct.id + "T" + boTicketId
              val qrCodeContent = "EventId:" + product.id + ";BoProductId:" + boProduct.id + ";BoTicketId:" + boTicketId +
                ";EventName:" + product.name + ";EventDate:" + startDateStr + ";FirstName:" +
                registeredCartItem.firstname.getOrElse("") + ";LastName:" + registeredCartItem.lastname.getOrElse("") +
                ";Phone:" + registeredCartItem.phone.getOrElse("") + ";TicketType:" + sku.name + ";shortCode:" + shortCode

              val encryptedQrCodeContent = SymmetricCrypt.encrypt(qrCodeContent, company.aesPassword, "AES", true)
              val output = new ByteArrayOutputStream()
              QRCodeUtils.createQrCode(output, encryptedQrCodeContent, 256, "png")
              val qrCodeBase64 = Base64.encode(output.toByteArray)

              /* for debug purpose only
              val qrCodeBase64 = "test"
              val encryptedQrCodeContent = "test"
              */
              (Some(shortCode), Some(qrCodeBase64), Some(encryptedQrCodeContent))
            }
            case _ => (None, None, None)
          }

          BOTicketTypeDao.create(boTicketId, sku, cartItem, registeredCartItem, shortCodeAndQrCode._1, shortCodeAndQrCode._2, shortCodeAndQrCode._3, boProduct.id)
          if (shortCodeAndQrCode._3.isDefined)
            storeRegistedCartItem.copy(qrCodeContent = Some(product.name + ":" + registeredCartItem.email + "||" + shortCodeAndQrCode._3.get))
          else storeRegistedCartItem
        }

        //create Sale
        val boDelivery = BODeliveryDao.create(boCart, Some(shippingAddress))

        // Downloadable Link
        val boCartItemUuid = BOCartItemDao.create(sku, cartItem, storeCartItem, boCart, Some(boDelivery), boProduct.id).uuid
        val downloadableLink = product.xtype match {
          case ProductType.DOWNLOADABLE => {
            val params = s"boCartItemUuid:$boCartItemUuid;skuId:${sku.id};storeCode:$storeCode;maxDelay:${product.downloadMaxDelay};maxTimes:${product.downloadMaxTimes}"
            val encryptedParams = SymmetricCrypt.encrypt(params, company.aesPassword, "AES", true)
            Some(s"${Settings.AccessUrl}/$storeCode/download/$encryptedParams")
          }
          case _ => None
        }
        storeCartItem.copy(registeredCartItems = newStoreRegistedCartItems.toList, boCartItemUuid = Some(boCartItemUuid), downloadableLink = downloadableLink)
      }
      (boCart, storeCart.copy(boCartUuid = Some(boCart.uuid), cartItems = newStoreCartItems.toList))
    }
    }

    exportBOCartIntoES(storeCart.storeCode, boCartAndStoreCart._1)

    boCartAndStoreCart._2
  }

  /**
   * Supprime le BOCart correspondant au panier s'il est en statut Pending
   * et renvoi un panier sans lien avec le boCart supprimé
   * @param cart
   * @return
   */
  private def _deletePendingBOCart(cart: StoreCart): StoreCart = {
    if (cart.boCartUuid.isDefined) {
      DB localTx { implicit session =>
        val boCart = BOCartDao.load(cart.boCartUuid.get)
        if (boCart.isDefined && boCart.get.status == TransactionStatus.PENDING) {
          BOCartItemDao.findByBOCart(boCart.get).foreach { boCartItem =>
            BOCartItemDao.delete(boCartItem)

            BOCartItemDao.getBOProducts(boCartItem).foreach { boProduct =>
              BOTicketTypeDao.findByBOProduct(boProduct.id).foreach { boTicketType =>
                BOTicketTypeDao.delete(boTicketType)
              }
              BOProductDao.delete(boProduct)
            }
          }
          BOCartDao.delete(boCart.get)
          BOCartESDao.delete(cart.storeCode, boCart.get.uuid)

          cart.copy(boCartUuid = None)
        }
        else cart
      }
    }
    else cart
  }

  /**
   * Construit un Locale à partir de la langue et du pays.<br/>
   * Si la lang == "_all" alors la langue par défaut est utilisée<br/>
   * Si le pays vaut None alors le pays par défaut est utiulisé
   * @param lang
   * @param country
   * @return
   */
  private def _buildLocal(lang: String, country: Option[String]): Locale = {
    val defaultLocal = Locale.getDefault
    val l = if (lang == "_all") defaultLocal.getLanguage else lang
    val c = if (country.isEmpty) defaultLocal.getCountry else country.get
    new Locale(l, c)
  }

  /**
   * Récupère le panier correspondant au uuid et au compte client.
   * La méthode gère le panier anonyme et le panier authentifié.
   * @param uuid
   * @param currentAccountId
   * @return
   */
  private def _initCart(storeCode: String, uuid: String, currentAccountId: Option[String]): StoreCart = {
    def getOrCreateStoreCart(cart: Option[StoreCart]): StoreCart = {
      cart match {
        case Some(c) => _removeAllUnsalabledItem(c)
        case None =>
          val c = new StoreCart(storeCode = storeCode, dataUuid = uuid, userUuid = currentAccountId)
          StoreCartDao.save(c)
          c
      }
    }

    if (currentAccountId.isDefined) {
      val cartAnonyme = StoreCartDao.findByDataUuidAndUserUuid(storeCode, uuid, None);
      val cartAuthentifie = getOrCreateStoreCart(StoreCartDao.findByDataUuidAndUserUuid(storeCode, uuid, currentAccountId));

      // S'il y a un panier anonyme, il est fusionné avec le panier authentifié et supprimé de la base
      if (cartAnonyme.isDefined) {
        StoreCartDao.delete(cartAnonyme.get)
        val fusionCart = _fusion(cartAnonyme.get, cartAuthentifie)
        StoreCartDao.save(fusionCart)
        fusionCart
      }
      else cartAuthentifie
    }
    else {
      // Utilisateur anonyme
      getOrCreateStoreCart(StoreCartDao.findByDataUuidAndUserUuid(storeCode, uuid, None));
    }
  }

  private def _removeAllUnsalabledItem(cart: StoreCart): StoreCart = {
    val currentIndex = EsClient.getUniqueIndexByAlias(cart.storeCode).getOrElse(cart.storeCode)
    val indexEsAndProductsToUpdate = scala.collection.mutable.Set[(String, Long)]()
    val newCartItems = DB localTx { implicit session =>
      cart.cartItems.flatMap { cartItem =>
        val cartItemWithIndex = cartItem.copy(indexEs = Option(cartItem.indexEs).getOrElse(cart.storeCode))
        val productAndSku = ProductDao.getProductAndSku(cart.storeCode, cartItem.skuId)
        if (productAndSku.isEmpty || !_checkProductAndSkuSalabality(productAndSku.get)) {
          if (cart.validate) {
            ProductDao.getProductAndSku(cartItemWithIndex.indexEs, cartItem.skuId).map { realProductAndSku =>
              indexEsAndProductsToUpdate += _unvalidateCartItem(cartItemWithIndex, realProductAndSku)
            }
          }
          None
        }
        else Some(cartItemWithIndex)
      }
    }

    _updateProductStockAvailability(indexEsAndProductsToUpdate.toSet)

    val updatedCart = cart.copy(cartItems = newCartItems)
    StoreCartDao.save(updatedCart)
    updatedCart
  }

  /**
   * Réinitialise le panier (en incrémentant en nombre d'utilisation des coupons)
   * @param cart
   * @return
   */
  private def _clearCart(cart: StoreCart): StoreCart = {
    _cancelCart(_unvalidateCart(cart))

    cart.coupons.foreach(coupon => {
      val optCoupon = CouponDao.findByCode(cart.storeCode, coupon.code)
      if (optCoupon.isDefined) couponHandler.releaseCoupon(cart.storeCode, optCoupon.get)
    })

    val updatedCart = new StoreCart(storeCode = cart.storeCode, dataUuid = cart.dataUuid, userUuid = cart.userUuid)
    StoreCartDao.save(updatedCart)
    updatedCart
  }

  /**
   * Annule la transaction courante et génère un nouveau uuid au panier
   * @param cart
   * @return
   */
  private def _cancelCart(cart: StoreCart): StoreCart = {
    cart.boCartUuid.map { boCartUuid =>
      BOCartDao.load(cart.boCartUuid.get).map { boCart =>
        // Mise à jour du statut
        val newBoCart = boCart.copy(status = TransactionStatus.FAILED)
        BOCartDao.updateStatus(newBoCart)
        exportBOCartIntoES(cart.storeCode, newBoCart)
      }

      val updatedCart = cart.copy(boCartUuid = None, transactionUuid = None)
      StoreCartDao.save(updatedCart)
      updatedCart
    } getOrElse (cart)
  }

  /**
   * Valide le panier en décrémentant les stocks
   * @param cart
   * @return
   */
  @throws[InsufficientStockException]
  private def _validateCart(cart: StoreCart): StoreCart = {
    if (!cart.validate) {
      val indexEsAndProductsToUpdate = scala.collection.mutable.Set[(String, Long)]()
      DB localTx { implicit session =>
        cart.cartItems.foreach { cartItem =>
          val productAndSku = ProductDao.getProductAndSku(cartItem.indexEs, cartItem.skuId)
          val product = productAndSku.get._1
          val sku = productAndSku.get._2
          stockHandler.decrementStock(cartItem.indexEs, product, sku, cartItem.quantity, cartItem.startDate)

          val indexEsAndProduct = (cartItem.indexEs, product.id)
          indexEsAndProductsToUpdate += indexEsAndProduct
        }
      }

      _updateProductStockAvailability(indexEsAndProductsToUpdate.toSet)

      val updatedCart = cart.copy(validate = true, validateUuid = Some(UUID.randomUUID().toString()))
      StoreCartDao.save(updatedCart)
      updatedCart
    }
    else cart
  }

  /**
   * Invalide le panier et libère les stocks si le panier était validé.
   * @param cart
   * @return
   */
  private def _unvalidateCart(cart: StoreCart): StoreCart = {
    if (cart.validate) {
      val indexEsAndProductsToUpdate = scala.collection.mutable.Set[(String, Long)]()
      DB localTx { implicit session =>
        cart.cartItems.foreach { cartItem =>
          indexEsAndProductsToUpdate += _unvalidateCartItem(cartItem)
        }
      }

      _updateProductStockAvailability(indexEsAndProductsToUpdate.toSet)

      val updatedCart = cart.copy(validate = false, validateUuid = None)
      StoreCartDao.save(updatedCart)
      updatedCart
    }
    else cart
  }

  private def _unvalidateCartItem(cartItem: StoreCartItem)(implicit session: DBSession): (String, Long) = {
    val productAndSku = ProductDao.getProductAndSku(cartItem.indexEs, cartItem.skuId)
    _unvalidateCartItem(cartItem, productAndSku.get)
  }

  private def _unvalidateCartItem(cartItem: StoreCartItem, productAndSku: (Mogobiz.Product, Mogobiz.Sku))(implicit session: DBSession): (String, Long) = {
    val product = productAndSku._1
    val sku = productAndSku._2
    stockHandler.incrementStock(cartItem.indexEs, product, sku, cartItem.quantity, cartItem.startDate)

    (cartItem.indexEs, product.id)
  }

  /**
   * Update products stock availability
   * @param indexEsAndProductId
   */
  private def _updateProductStockAvailability(indexEsAndProductId: Set[(String, Long)]) = {
    import scala.concurrent.duration._
    import system.dispatcher
    val system = ActorSystemLocator.get
    val stockActor = system.actorOf(Props[EsUpdateActor])
    indexEsAndProductId.foreach { indexEsAndProduct =>
      system.scheduler.scheduleOnce(2 seconds) {
        stockActor ! ProductStockAvailabilityUpdateRequest(indexEsAndProduct._1, indexEsAndProduct._2)
      }
    }
  }

  /**
   * Fusionne le panier source avec le panier cible et renvoie le résultat
   * de la fusion
   * @param source
   * @param target
   * @return
   */
  private def _fusion(source: StoreCart, target: StoreCart): StoreCart = {
    def _fusionCartItem(source: List[StoreCartItem], target: StoreCart): StoreCart = {
      if (source.isEmpty) target
      else _fusionCartItem(source.tail, _addCartItemIntoCart(target, source.head))
    }
    def _fusionCoupon(source: List[StoreCoupon], target: StoreCart): StoreCart = {
      if (source.isEmpty) target
      else _fusionCoupon(source.tail, _addCouponIntoCart(target, source.head))
    }
    _fusionCoupon(source.coupons.toList, _fusionCartItem(source.cartItems.toList, target))
  }

  /**
   * Ajoute un item au panier. Si un item existe déjà (sauf pour le SERVICE), la quantité
   * de l'item existant est modifié
   * @param cart
   * @param cartItem
   * @return
   */
  private def _addCartItemIntoCart(cart: StoreCart, cartItem: StoreCartItem): StoreCart = {
    val existCartItem = _findCartItem(cart, cartItem)
    if (existCartItem.isDefined) {
      val newCartItems = existCartItem.get.copy(id = cartItem.id, quantity = existCartItem.get.quantity + cartItem.quantity) :: Utils.remove(cart.cartItems, existCartItem.get)
      cart.copy(cartItems = newCartItems)
    }
    else {
      val newCartItems = (cartItem :: cart.cartItems)
      cart.copy(cartItems = newCartItems)
    }
  }

  /**
   * Retrouve un item parmi la liste des items du panier. L'item est recherche si le type
   * n'est pas SERVICE et si l'id du produit et du sku sont identiques
   * @param cart
   * @param cartItem
   * @return
   */
  private def _findCartItem(cart: StoreCart, cartItem: StoreCartItem): Option[StoreCartItem] = {
    if (cartItem.xtype == ProductType.SERVICE) None
    else cart.cartItems.find { ci: StoreCartItem => ci.productId == cartItem.productId && ci.skuId == cartItem.skuId }
  }

  /**
   * Ajoute un coupon au panier s'il n'existe pas déjà (en comparant les codes des coupons)
   * @param cart
   * @param coupon
   * @return
   */
  private def _addCouponIntoCart(cart: StoreCart, coupon: StoreCoupon): StoreCart = {
    val existCoupon = cart.coupons.find { c: StoreCoupon => c.code == coupon.code }
    if (existCoupon.isDefined) {
      cart
    }
    else {
      val newCoupons = (coupon :: cart.coupons)
      cart.copy(coupons = newCoupons)
    }
  }

  /**
   * Transforme le StoreCart en un CartVO en calculant les montants
   * @param cart
   * @param countryCode
   * @param stateCode
   * @return
   */
  private def _computeStoreCart(cart: StoreCart, countryCode: Option[String], stateCode: Option[String]): Cart = {
    val priceEndPriceCartItems = _computeCartItem(cart, cart.cartItems, countryCode, stateCode)
    val price = priceEndPriceCartItems._1
    val endPrice = priceEndPriceCartItems._2
    val cartItems = priceEndPriceCartItems._3

    val reductionCoupons = couponHandler.computeCoupons(cart.storeCode, cart.coupons, cartItems)

    val promoAvailable = CouponDao.findPromotionsThatOnlyApplyOnCart(cart.storeCode)
    val reductionPromotions = couponHandler.computePromotions(cart.storeCode, promoAvailable, cartItems)

    val reduction = reductionCoupons.reduction + reductionPromotions.reduction
    val coupons = reductionCoupons.coupons ::: reductionPromotions.coupons

    val count = _calculateCount(cartItems)
    val validateUuid = if (cart.validate) cart.validateUuid else None
    Cart(validateUuid, price, endPrice, reduction, endPrice - reduction, count, cartItems.toArray, coupons.toArray)
  }

  private def _findSuggestionDiscount(cart: StoreCart, productId: Long): List[String] = {
    def extractDiscout(l: List[Mogobiz.Suggestion]): List[String] = {
      if (l.isEmpty) List()
      else {
        val s: Mogobiz.Suggestion = l.head
        val ci = cart.cartItems.find { ci => ci.productId == s.parentId }
        if (ci.isDefined) s.discount :: extractDiscout(l.tail)
        else extractDiscout(l.tail)
      }
    }
    val suggestions: List[Mogobiz.Suggestion] = SuggestionDao.getSuggestionsbyId(cart.storeCode, productId)
    extractDiscout(suggestions)
  }

  private def computeDiscounts(price: Long, discounts: List[String]): Long = {
    if (discounts.isEmpty) Math.max(price, 0)
    else {
      val discountPrice = Math.max(price - couponHandler.computeDiscount(Some(discounts.head), price), 0)
      Math.min(discountPrice, computeDiscounts(price, discounts.tail))
    }
  }

  /**
   * Calcul les montant de la liste des StoreCartItem et renvoie (prix hors taxe du panier, prix taxé du panier, liste CartItemVO avec prix)
   * @param cartItems
   * @param countryCode
   * @param stateCode
   * @return
   */
  private def _computeCartItem(cart: StoreCart, cartItems: List[StoreCartItem], countryCode: Option[String], stateCode: Option[String]): (Long, Long, List[CartItem]) = {
    if (cartItems.isEmpty) (0, 0, List())
    else {
      val storeCode = cart.storeCode
      val priceEndPriceAndCartItems = _computeCartItem(cart, cartItems.tail, countryCode, stateCode)

      val cartItem = cartItems.head
      val product = ProductDao.get(storeCode, cartItem.productId).get
      val tax = taxRateHandler.findTaxRateByProduct(product, countryCode, stateCode)
      val discounts = _findSuggestionDiscount(cart, cartItem.productId)
      val price = cartItem.price
      val salePrice = computeDiscounts(cartItem.salePrice, discounts)
      val endPrice = taxRateHandler.calculateEndPrice(price, tax)
      val saleEndPrice = taxRateHandler.calculateEndPrice(salePrice, tax)
      val totalPrice = price * cartItem.quantity
      val saleTotalPrice = salePrice * cartItem.quantity
      val totalEndPrice = taxRateHandler.calculateEndPrice(totalPrice, tax)
      val saleTotalEndPrice = taxRateHandler.calculateEndPrice(saleTotalPrice, tax)

      val newCartItem = CartItem(cartItem.id, cartItem.productId, cartItem.productName, cartItem.xtype, cartItem.calendarType,
        cartItem.skuId, cartItem.skuName, cartItem.quantity, price, endPrice, tax, totalPrice, totalEndPrice,
        salePrice, saleEndPrice, saleTotalPrice, saleTotalEndPrice,
        cartItem.startDate, cartItem.endDate, cartItem.registeredCartItems.toArray, cartItem.shipping, cartItem.downloadableLink.getOrElse(null))

      (priceEndPriceAndCartItems._1 + saleTotalPrice, priceEndPriceAndCartItems._2 + saleTotalEndPrice.getOrElse(saleTotalPrice), newCartItem :: priceEndPriceAndCartItems._3)
    }
  }

  /**
   * Calcule le nombre d'item du panier en prenant en compte les quantités
   * @param list
   * @return
   */
  private def _calculateCount(list: List[CartItem]): Int = {
    if (list.isEmpty) 0
    else {
      list.head.quantity + _calculateCount(list.tail)
    }
  }

  /**
   * Envoie par mail le contenu du panier
   * @param storeCode
   * @param email
   * @param cart
   * @param locale
   */
  private def _notifyCartCommit(storeCode: String, email: String, cart: StoreCart, locale: Locale): Unit = {
    import org.json4s.native.Serialization.write
    import com.mogobiz.run.implicits.Json4sProtocol._

    val cartTTC = _computeStoreCart(cart, cart.countryCode, cart.stateCode)
    val renderCart = _renderTransactionCart(cart, cartTTC, cart.rate.get, locale)

    val template = templateHandler.getTemplate(storeCode, "mail-cart", Some(locale.toString))

    val (subject, body) = templateHandler.mustache(template, write(renderCart))
    EmailHandler.Send.to(
      Mail(
        from = (Settings.Mail.DefaultFrom -> storeCode),
        to = Seq(email),
        subject = subject,
        message = body,
        richMessage = Some(body)
      ))
  }

  private def _renderCart(cart: Cart, currency: Currency, locale: Locale): Map[String, Any] = {
    var map: Map[String, Any] = Map()
    map += ("validateUuid" -> cart.validateUuid.getOrElse(""))
    map += ("count" -> cart.count)
    map += ("cartItemVOs" -> cart.cartItemVOs.map(item => _renderCartItem(item, currency, locale)))
    map += ("coupons" -> cart.coupons.map(c => _renderCoupon(c, currency, locale)))
    map ++= _renderPriceCart(cart, currency, locale)
    map
  }

  private def transformCartForCartPay(compagnyAddress: Option[CompanyAddress], cart: Cart, rate: Currency, shippingRulePrice: Option[Long]): CartPay = {
    val cartItemsPay = cart.cartItemVOs.map { cartItem =>
      val registeredCartItemsPay = cartItem.registeredCartItemVOs.map { rci =>
        new RegisteredCartItemPay(rci.id, rci.email, rci.firstname, rci.lastname, rci.phone, rci.birthdate, Map())
      }
      val shippingPay = cartItem.shipping.map { shipping =>
        new ShippingPay(shipping.weight, shipping.weightUnit.toString(), shipping.width, shipping.height, shipping.depth,
          shipping.linearUnit.toString(), shipping.amount, shipping.free, Map())
      }
      val customCartItem = Map("productId" -> cartItem.productId,
        "productName" -> cartItem.productName,
        "xtype" -> cartItem.xtype.toString(),
        "calendarType" -> cartItem.calendarType.toString(),
        "skuId" -> cartItem.skuId,
        "skuName" -> cartItem.skuName,
        "startDate" -> cartItem.startDate,
        "endDate" -> cartItem.endDate)
      val name = s"${cartItem.productName} ${cartItem.skuName}"
      val endPrice = cartItem.endPrice.getOrElse(cartItem.price)
      val totalEndPrice = cartItem.totalEndPrice.getOrElse(cartItem.totalPrice)
      val saleEndPrice = cartItem.saleEndPrice.getOrElse(cartItem.salePrice)
      val saleTotalEndPrice = cartItem.saleTotalEndPrice.getOrElse(cartItem.saleTotalPrice)
      new CartItemPay(cartItem.id, name, cartItem.quantity, cartItem.price, endPrice, cartItem.tax.getOrElse(0), endPrice - cartItem.price,
        cartItem.totalPrice, totalEndPrice, totalEndPrice - cartItem.totalPrice,
        cartItem.salePrice, saleEndPrice, saleEndPrice - cartItem.salePrice,
        cartItem.saleTotalPrice, saleTotalEndPrice, saleTotalEndPrice - cartItem.saleTotalPrice,
        registeredCartItemsPay, shippingPay, customCartItem)
    }
    val couponsPay = cart.coupons.map { coupon =>
      val customCoupon = Map("name" -> coupon.name, "active" -> coupon.active)
      new CouponPay(coupon.code, coupon.startDate, coupon.endDate, coupon.price, customCoupon)
    }

    val cartRate = CartRate(rate.code, rate.numericCode, rate.rate, rate.currencyFractionDigits)

    new CartPay(cart.count, cartRate, cart.price, cart.endPrice, cart.endPrice - cart.price, cart.reduction, cart.finalPrice, shippingRulePrice, cartItemsPay, couponsPay, Map(), compagnyAddress)
  }

  /**
   * Idem que renderCart à quelques différences près d'où la dupplication de code
   * @param cart
   * @param rate
   * @return
   */
  private def _renderTransactionCart(storeCart: StoreCart, cart: Cart, rate: Currency, locale: Locale): Map[String, Any] = {
    var map: Map[String, Any] = Map(
      "boCartUuid" -> storeCart.boCartUuid.getOrElse(""),
      "transactionUuid" -> storeCart.transactionUuid.getOrElse(""),
      "count" -> cart.count,
      "cartItemVOs" -> cart.cartItemVOs.map(item => _renderTransactionCartItem(item, rate, locale)),
      "coupons" -> cart.coupons.map(c => _renderTransactionCoupon(c, rate, locale))
    )
    map ++= _renderTransactionPriceCart(cart, rate, locale)
    map
  }

  /**
   * Renvoie un coupon JSONiné augmenté par un calcul de prix formaté
   * @param coupon
   * @param currency
   * @param locale
   * @return
   */
  private def _renderCoupon(coupon: Coupon, currency: Currency, locale: Locale) = {
    implicit def json4sFormats: Formats = DefaultFormats ++ JodaTimeSerializers.all + FieldSerializer[Coupon]()
    val jsonCoupon = parse(write(coupon))

    //code from renderPriceCoupon
    val formatedPrice = rateService.formatPrice(coupon.price, currency, locale)
    val additionalsData = parse(write(Map("formatedPrice" -> formatedPrice)))

    jsonCoupon merge additionalsData
  }

  private def _renderTransactionCoupon(coupon: Coupon, rate: Currency, locale: Locale) = {
    implicit def json4sFormats: Formats = DefaultFormats ++ JodaTimeSerializers.all + FieldSerializer[Coupon]()
    val jsonCoupon = parse(write(coupon))

    val price = rateService.calculateAmount(coupon.price, rate)
    val updatedData = parse(write(Map(
      "price" -> price,
      "formatedPrice" -> rateService.formatPrice(coupon.price, rate, locale)
    )))

    jsonCoupon merge updatedData
  }

  /**
   * Renvoie un cartItem JSONinsé augmenté par le calcul des prix formattés
   * @param item item du panier
   * @param currency
   * @param locale
   * @return
   */
  private def _renderCartItem(item: CartItem, currency: Currency, locale: Locale) = {
    import org.json4s.native.JsonMethods._
    //import org.json4s.native.Serialization
    import org.json4s.native.Serialization.write
    implicit def json4sFormats: Formats = DefaultFormats ++ JodaTimeSerializers.all + FieldSerializer[CartItem]() + new org.json4s.ext.EnumNameSerializer(ProductCalendar)
    val jsonItem = parse(write(item))

    val additionalsData = parse(write(Map(
      "formatedPrice" -> rateService.formatPrice(item.price, currency, locale),
      "formatedSalePrice" -> rateService.formatPrice(item.salePrice, currency, locale),
      "formatedEndPrice" -> item.endPrice.map {
        rateService.formatPrice(_, currency, locale)
      },
      "formatedSaleEndPrice" -> item.saleEndPrice.map {
        rateService.formatPrice(_, currency, locale)
      },
      "formatedTotalPrice" -> rateService.formatPrice(item.totalPrice, currency, locale),
      "formatedSaleTotalPrice" -> rateService.formatPrice(item.saleTotalPrice, currency, locale),
      "formatedTotalEndPrice" -> item.totalEndPrice.map {
        rateService.formatPrice(_, currency, locale)
      },
      "formatedSaleTotalEndPrice" -> item.saleTotalEndPrice.map {
        rateService.formatPrice(_, currency, locale)
      }
    )))

    //TODO Traduction aussi du nom en traduisant le produit et le sku
    /*
    price["productName"] = translateName(cartItem.productId, locale.language, cartItem.productName)
    price["skuName"] = translateName(cartItem.skuId, locale.language, cartItem.skuName)
    */

    jsonItem merge additionalsData
  }

  private def _renderTransactionCartItem(item: CartItem, rate: Currency, locale: Locale) = {
    import org.json4s.native.JsonMethods._
    import org.json4s.native.Serialization.write
    //implicit def json4sFormats: Formats = DefaultFormats + FieldSerializer[CartItemVO]()
    import Json4sProtocol._
    val jsonItem = parse(write(item))

    val price = rateService.calculateAmount(item.price, rate)
    val salePrice = rateService.calculateAmount(item.salePrice, rate)
    val endPrice = rateService.calculateAmount(item.endPrice.getOrElse(item.price), rate)
    val saleEndPrice = rateService.calculateAmount(item.saleEndPrice.getOrElse(item.salePrice), rate)
    val totalPrice = rateService.calculateAmount(item.totalPrice, rate)
    val saleTotalPrice = rateService.calculateAmount(item.saleTotalPrice, rate)
    val totalEndPrice = rateService.calculateAmount(item.totalEndPrice.getOrElse(item.totalPrice), rate)
    val saleTotalEndPrice = rateService.calculateAmount(item.saleTotalEndPrice.getOrElse(item.saleTotalPrice), rate)

    val updatedData = parse(write(Map(
      "price" -> price,
      "salePrice" -> salePrice,
      "endPrice" -> endPrice,
      "saleEndPrice" -> saleEndPrice,
      "totalPrice" -> totalPrice,
      "saleTotalPrice" -> saleTotalPrice,
      "totalEndPrice" -> totalEndPrice,
      "saleTotalEndPrice" -> saleTotalEndPrice,
      "formatedPrice" -> rateService.formatLongPrice(item.price, rate, locale),
      "formatedSalePrice" -> rateService.formatLongPrice(item.salePrice, rate, locale),
      "formatedEndPrice" -> rateService.formatLongPrice(item.endPrice.getOrElse(item.price), rate, locale),
      "formatedSaleEndPrice" -> rateService.formatLongPrice(item.saleEndPrice.getOrElse(item.salePrice), rate, locale),
      "formatedTotalPrice" -> rateService.formatLongPrice(item.totalPrice, rate, locale),
      "formatedSaleTotalPrice" -> rateService.formatLongPrice(item.saleTotalPrice, rate, locale),
      "formatedTotalEndPrice" -> rateService.formatLongPrice(item.totalEndPrice.getOrElse(item.totalPrice), rate, locale),
      "formatedSaleTotalEndPrice" -> rateService.formatLongPrice(item.saleTotalEndPrice.getOrElse(item.saleTotalPrice), rate, locale)
    )))

    jsonItem merge updatedData
  }

  /**
   * Renvoie tous les champs prix calculé sur le panier
   * @param cart
   * @param currency
   * @param locale
   * @return
   */
  private def _renderPriceCart(cart: Cart, currency: Currency, locale: Locale) = {
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

  private def _renderTransactionPriceCart(cart: Cart, rate: Currency, locale: Locale) = {
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

  def exportBOCartIntoES(storeCode: String, boCart: BOCart, refresh: Boolean = false)(implicit session: DBSession = AutoSession) = {
    val boCartES = boCartToESBOCart(storeCode, boCart)
    BOCartESDao.save(storeCode, boCartES, refresh)
  }

  def boCartToESBOCart(storeCode: String, boCart: BOCart)(implicit session: DBSession = AutoSession): ES.BOCart = {
    // Conversion des BOCartItem
    val cartItems = BOCartItemDao.findByBOCart(boCart).map { boCartItem =>
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
        ProductDao.get(storeCode, boProduct.productFk) map {  product =>
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
          BOReturnES(
            motivation = boReturn.motivation,
            status = boReturn.status,
            dateCreated = boReturn.dateCreated.toDate,
            lastUpdated = boReturn.lastUpdated.toDate,
            uuid = boReturn.uuid)
        }

        BOReturnedItemES(
          quantity = boReturnedItem.quantity,
          refunded = boReturnedItem.refunded,
          totalRefunded = boReturnedItem.totalRefunded,
          status = boReturnedItem.status,
          boReturns = boReturns,
          dateCreated = boReturnedItem.dateCreated.toDate,
          lastUpdated = boReturnedItem.lastUpdated.toDate,
          uuid = boReturnedItem.uuid)
      }

      val (principal, secondary) = boProducts.partition(_.principal)
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
        sku = ProductDao.getProductAndSku(storeCode, boCartItem.ticketTypeFk).get._2,
        principal = principal.head,
        secondary = secondary,
        bODelivery = boDelivery,
        bOReturnedItems = boReturnedItems,
        uuid = boCartItem.uuid,
        url = boCartItem.url)
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

  private def buildIndex(storeCode: String) = s"${storeCode}_cart"

  def findByDataUuidAndUserUuid(storeCode: String, dataUuid: String, userUuid: Option[Mogopay.Document]): Option[StoreCart] = {
    val uuid = dataUuid + "--" + userUuid.getOrElse("None")
    EsClient.load[StoreCart](buildIndex(storeCode), uuid)
  }

  def save(entity: StoreCart): Boolean = {
    EsClient.update[StoreCart](buildIndex(entity.storeCode), entity.copy(expireDate = DateTime.now.plusSeconds(60 * Settings.Cart.Lifetime)), true, false)
  }

  def delete(cart: StoreCart): Unit = {
    EsClient.delete[StoreCart](buildIndex(cart.storeCode), cart.uuid, false)
  }

  def getExpired(index: String, querySize: Int): List[StoreCart] = {
    val req = search in index -> "StoreCart" postFilter (rangeFilter("expireDate") lt "now") size (querySize)
    EsClient.searchAll[StoreCart](req).toList
  }
}

object DownloadableDao {

  def load(storeCode: String, skuId: String): Option[ChannelBufferBytesReference] = {
    EsClient.loadRaw(get id skuId from storeCode -> "downloadable" fields "file.content" fetchSourceContext true) match {
      case Some(response) =>
        if (response.getFields.containsKey("file.content")) {
          Some(response.getField("file.content").getValue.asInstanceOf[ChannelBufferBytesReference])
        }
        else None
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

  def apply(rn: ResultName[BOCart])(rs: WrappedResultSet): BOCart = BOCart(
    rs.get(rn.id),
    rs.get(rn.buyer),
    rs.get(rn.companyFk),
    rs.get(rn.currencyCode),
    rs.get(rn.currencyRate),
    rs.get(rn.xdate),
    rs.get(rn.dateCreated),
    rs.get(rn.lastUpdated),
    rs.get(rn.price),
    TransactionStatus.valueOf(rs.get(rn.status)),
    rs.get(rn.transactionUuid),
    rs.get(rn.uuid))

  def load(uuid: String): Option[BOCart] = {
    val t = BOCartDao.syntax("t")
    DB readOnly { implicit session =>
      withSQL {
        select.from(BOCartDao as t).where.eq(t.uuid, uuid)
      }.map(BOCartDao(t.resultName)).single().apply()
    }
  }

  def findByTransactionUuid(transactionUuid: String)(implicit session: DBSession = AutoSession): Option[BOCart] = {
    val t = BOCartDao.syntax("t")
    withSQL {
      select.from(BOCartDao as t).where.eq(t.transactionUuid, transactionUuid)
    }.map(BOCartDao(t.resultName)).single().apply()
  }

  def updateStatus(boCart: BOCart): Unit = {
    DB localTx { implicit session =>
      withSQL {
        update(BOCartDao).set(
          BOCartDao.column.status -> boCart.status.toString(),
          BOCartDao.column.transactionUuid -> boCart.transactionUuid,
          BOCartDao.column.lastUpdated -> DateTime.now
        ).where.eq(BOCartDao.column.id, boCart.id)
      }.update.apply()
    }
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
      insert.into(BOCartDao).namedValues(
        BOCartDao.column.id -> newBoCart.id,
        BOCartDao.column.buyer -> newBoCart.buyer,
        BOCartDao.column.companyFk -> newBoCart.companyFk,
        BOCartDao.column.currencyCode -> newBoCart.currencyCode,
        BOCartDao.column.currencyRate -> newBoCart.currencyRate,
        BOCartDao.column.xdate -> newBoCart.xdate,
        BOCartDao.column.dateCreated -> newBoCart.dateCreated,
        BOCartDao.column.lastUpdated -> newBoCart.lastUpdated,
        BOCartDao.column.price -> newBoCart.price,
        BOCartDao.column.status -> newBoCart.status.toString(),
        BOCartDao.column.transactionUuid -> newBoCart.transactionUuid,
        BOCartDao.column.uuid -> newBoCart.uuid
      )
    }

    newBoCart
  }

  def delete(boCart: BOCart)(implicit session: DBSession) = {
    withSQL {
      deleteFrom(BOCartDao).where.eq(BOCartDao.column.id, boCart.id)
    }.update.apply()
  }

}

object BODeliveryDao extends SQLSyntaxSupport[BODelivery] with BoService {

  override val tableName = "b_o_delivery"

  def apply(rn: ResultName[BODelivery])(rs: WrappedResultSet): BODelivery = new BODelivery(
    rs.get(rn.id),
    rs.get(rn.bOCartFk),
    DeliveryStatus(rs.get(rn.status)),
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
      insert.into(BODeliveryDao).namedValues(
        BODeliveryDao.column.id -> newBODelivery.id,
        BODeliveryDao.column.bOCartFk -> newBODelivery.bOCartFk,
        BODeliveryDao.column.status -> newBODelivery.status.toString(),
        BODeliveryDao.column.tracking -> newBODelivery.tracking,
        BODeliveryDao.column.extra -> newBODelivery.extra,
        BODeliveryDao.column.dateCreated -> newBODelivery.dateCreated,
        BODeliveryDao.column.lastUpdated -> newBODelivery.lastUpdated,
        BODeliveryDao.column.uuid -> newBODelivery.uuid
      )
    }

    newBODelivery
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

  def apply(rn: ResultName[BOReturnedItem])(rs: WrappedResultSet): BOReturnedItem = new BOReturnedItem(
    rs.get(rn.id),
    rs.get(rn.bOCartItemFk),
    rs.get(rn.quantity),
    rs.get(rn.refunded),
    rs.get(rn.totalRefunded),
    ReturnedItemStatus(rs.get(rn.status)),
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
      insert.into(BOReturnedItemDao).namedValues(
        BOReturnedItemDao.column.id -> boReturnedItem.id,
        BOReturnedItemDao.column.bOCartItemFk -> boReturnedItem.bOCartItemFk,
        BOReturnedItemDao.column.quantity -> boReturnedItem.quantity,
        BOReturnedItemDao.column.refunded -> boReturnedItem.refunded,
        BOReturnedItemDao.column.totalRefunded -> boReturnedItem.totalRefunded,
        BOReturnedItemDao.column.status -> boReturnedItem.status.toString(),
        BOReturnedItemDao.column.dateCreated -> boReturnedItem.dateCreated,
        BOReturnedItemDao.column.lastUpdated -> boReturnedItem.lastUpdated,
        BOReturnedItemDao.column.uuid -> boReturnedItem.uuid
      )
    }

    boReturnedItem
  }

  def save(boReturnedItem: BOReturnedItem)(implicit session: DBSession): BOReturnedItem = {
    val updatedBoReturnedItem = boReturnedItem.copy(lastUpdated = DateTime.now())

    withSQL {
      update(BOReturnedItemDao).set(
        BOReturnedItemDao.column.refunded -> updatedBoReturnedItem.refunded,
        BOReturnedItemDao.column.totalRefunded -> updatedBoReturnedItem.totalRefunded,
        BOReturnedItemDao.column.status -> updatedBoReturnedItem.status.toString(),
        BOReturnedItemDao.column.lastUpdated -> updatedBoReturnedItem.lastUpdated
      ).where.eq(BOReturnedItemDao.column.id, updatedBoReturnedItem.id)
    }.update.apply()

    updatedBoReturnedItem
  }
}

object BOReturnDao extends SQLSyntaxSupport[BOReturn] with BoService {

  override val tableName = "b_o_return"

  def apply(rn: ResultName[BOReturn])(rs: WrappedResultSet): BOReturn = new BOReturn(
    rs.get(rn.id),
    rs.get(rn.bOReturnedItemFk),
    rs.get(rn.motivation),
    ReturnStatus(rs.get(rn.status)),
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
      insert.into(BOReturnDao).namedValues(
        BOReturnDao.column.id -> boReturn.id,
        BOReturnDao.column.bOReturnedItemFk -> boReturn.bOReturnedItemFk,
        BOReturnDao.column.motivation -> boReturn.motivation,
        BOReturnDao.column.status -> boReturn.status.toString(),
        BOReturnDao.column.dateCreated -> boReturn.dateCreated,
        BOReturnDao.column.lastUpdated -> boReturn.lastUpdated,
        BOReturnDao.column.uuid -> boReturn.uuid
      )
    }

    boReturn
  }
}

object BOCartItemDao extends SQLSyntaxSupport[BOCartItem] with BoService {

  override val tableName = "b_o_cart_item"

  def apply(rn: ResultName[BOCartItem])(rs: WrappedResultSet): BOCartItem = new BOCartItem(
    rs.get(rn.id),
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
    rs.get(rn.url))

  def create(sku: Mogobiz.Sku, cartItem: Render.CartItem, storeCartItem: StoreCartItem, boCart: BOCart, bODelivery: Option[BODelivery], boProductId: Long)(implicit session: DBSession): BOCartItem = {
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
      cartItem.startDate,
      cartItem.endDate,
      sku.id,
      boCart.id,
      if (bODelivery.isDefined) Some(bODelivery.get.id) else None,
      DateTime.now,
      DateTime.now,
      UUID.randomUUID().toString,
      storeCartItem.productUrl
    )

    applyUpdate {
      insert.into(BOCartItemDao).namedValues(
        BOCartItemDao.column.id -> newBOCartItem.id,
        BOCartItemDao.column.code -> newBOCartItem.code,
        BOCartItemDao.column.price -> newBOCartItem.price,
        BOCartItemDao.column.tax -> newBOCartItem.tax,
        BOCartItemDao.column.endPrice -> newBOCartItem.endPrice,
        BOCartItemDao.column.totalPrice -> newBOCartItem.totalPrice,
        BOCartItemDao.column.totalEndPrice -> newBOCartItem.totalEndPrice,
        BOCartItemDao.column.hidden -> newBOCartItem.hidden,
        BOCartItemDao.column.quantity -> newBOCartItem.quantity,
        BOCartItemDao.column.startDate -> newBOCartItem.startDate,
        BOCartItemDao.column.endDate -> newBOCartItem.endDate,
        BOCartItemDao.column.ticketTypeFk -> newBOCartItem.ticketTypeFk,
        BOCartItemDao.column.bOCartFk -> newBOCartItem.bOCartFk,
        BOCartItemDao.column.bODeliveryFk -> newBOCartItem.bODeliveryFk,
        BOCartItemDao.column.dateCreated -> newBOCartItem.dateCreated,
        BOCartItemDao.column.lastUpdated -> newBOCartItem.lastUpdated,
        BOCartItemDao.column.uuid -> newBOCartItem.uuid,
        BOCartItemDao.column.url -> newBOCartItem.url
      )
    }

    sql"insert into b_o_cart_item_b_o_product(b_o_products_fk, boproduct_id) values(${newBOCartItem.id},$boProductId)"
      .update.apply()

    newBOCartItem
  }

  def findByBOCart(boCart: BOCart)(implicit session: DBSession = AutoSession): List[BOCartItem] = {
    val t = BOCartItemDao.syntax("t")
    withSQL {
      select.from(BOCartItemDao as t).where.eq(t.bOCartFk, boCart.id)
    }.map(BOCartItemDao(t.resultName)).list().apply()
  }

  def findByBOReturnedItem(boReturnedItem: BOReturnedItem)(implicit session: DBSession = AutoSession): Option[BOCartItem] = {
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
      .map(rs => BOProductDao(rs)).list().apply()
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

  def apply(rn: ResultName[BOTicketType])(rs: WrappedResultSet): BOTicketType = new BOTicketType(
    rs.get(rn.id),
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

  def create(boTicketId: Long, sku: Sku, cartItem: Render.CartItem, registeredCartItem: Render.RegisteredCartItem, shortCode: Option[String], qrCode: Option[String], qrCodeContent: Option[String], boProductId: Long)(implicit session: DBSession): BOTicketType = {
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
      cartItem.startDate,
      cartItem.endDate,
      qrCode,
      qrCodeContent,
      boProductId,
      DateTime.now,
      DateTime.now,
      UUID.randomUUID().toString
    )

    applyUpdate {
      insert.into(BOTicketTypeDao).namedValues(
        BOTicketTypeDao.column.uuid -> newBOTicketType.uuid,
        BOTicketTypeDao.column.id -> newBOTicketType.id,
        BOTicketTypeDao.column.shortCode -> newBOTicketType.shortCode,
        BOTicketTypeDao.column.quantity -> newBOTicketType.quantity,
        BOTicketTypeDao.column.price -> newBOTicketType.price,
        BOTicketTypeDao.column.ticketType -> newBOTicketType.ticketType,
        BOTicketTypeDao.column.firstname -> newBOTicketType.firstname,
        BOTicketTypeDao.column.lastname -> newBOTicketType.lastname,
        BOTicketTypeDao.column.email -> newBOTicketType.email,
        BOTicketTypeDao.column.phone -> newBOTicketType.phone,
        BOTicketTypeDao.column.age -> newBOTicketType.age,
        BOTicketTypeDao.column.birthdate -> newBOTicketType.birthdate,
        BOTicketTypeDao.column.startDate -> newBOTicketType.startDate,
        BOTicketTypeDao.column.endDate -> newBOTicketType.endDate,
        BOTicketTypeDao.column.qrcode -> newBOTicketType.qrcode,
        BOTicketTypeDao.column.qrcodeContent -> newBOTicketType.qrcodeContent,
        BOTicketTypeDao.column.dateCreated -> newBOTicketType.dateCreated,
        BOTicketTypeDao.column.lastUpdated -> newBOTicketType.lastUpdated,
        BOTicketTypeDao.column.bOProductFk -> newBOTicketType.bOProductFk
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

  def apply(rs: WrappedResultSet): BOProduct = new BOProduct(
    rs.long("id"),
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
      insert.into(BOProductDao).namedValues(
        BOProductDao.column.id -> newBOProduct.id,
        BOProductDao.column.acquittement -> newBOProduct.acquittement,
        BOProductDao.column.price -> newBOProduct.price,
        BOProductDao.column.principal -> newBOProduct.principal,
        BOProductDao.column.productFk -> newBOProduct.productFk,
        BOProductDao.column.dateCreated -> newBOProduct.dateCreated,
        BOProductDao.column.lastUpdated -> newBOProduct.lastUpdated,
        BOProductDao.column.uuid -> newBOProduct.uuid
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