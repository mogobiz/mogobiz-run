package com.mogobiz.run.handlers

import java.util.{UUID, Locale}

import com.mogobiz.es.EsClient
import com.mogobiz.pay.model.Mogopay
import com.mogobiz.run.config.HandlersConfig._
import com.mogobiz.run.config.Settings
import com.mogobiz.run.es._
import com.mogobiz.run.exceptions._
import com.mogobiz.run.handlers.EmailHandler.Mail
import com.mogobiz.run.implicits.Json4sProtocol
import com.mogobiz.run.learning.{CartRegistration, UserActionRegistration}
import com.mogobiz.run.model.MogoLearn.UserAction
import com.mogobiz.run.model.Mogobiz.{InsufficientStockException, ProductCalendar, ProductType}
import com.mogobiz.run.model.Render.{Coupon, RegisteredCartItem, CartItem, Cart}
import com.mogobiz.run.model.RequestParameters._
import com.mogobiz.run.model._
import com.mogobiz.run.services.RateBoService
import com.mogobiz.run.utils.Utils
import com.sksamuel.elastic4s.ElasticDsl._
import org.joda.time.DateTime
import org.json4s.ext.JodaTimeSerializers
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization._
import org.json4s.{FieldSerializer, DefaultFormats, Formats}
import scalikejdbc.DB


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
  def queryCartInit(storeCode: String, uuid: String, params: CartParameters, accountId:Option[Mogopay.Document]): Map[String, Any] = {
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
  def queryCartClear(storeCode: String, uuid: String, params: CartParameters, accountId:Option[Mogopay.Document]): Map[String, Any] = {
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
  def queryCartItemAdd(storeCode: String, uuid: String, params: CartParameters, cmd: AddCartItemRequest, accountId:Option[Mogopay.Document]): Map[String, Any] = {
    val locale = _buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val cart = _initCart(storeCode, uuid, accountId)

    val productAndSku = ProductDao.getProductAndSku(cart.storeCode, cmd.skuId)
    if (productAndSku.isEmpty) throw new NotFoundException("unknown sku")

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
    val cartItem = StoreCartItem(newCartItemId, product.id, product.name, product.xtype, product.calendarType, sku.id, sku.name, cmd.quantity,
      sku.price, salePrice, startDate, endDate, registeredItems, product.shipping)

    val updatedCart = _addCartItemIntoCart(_unvalidateCart(cart), cartItem)
    StoreCartDao.save(updatedCart)

    val computeCart = _computeStoreCart(updatedCart, params.country, params.state)
    _renderCart(computeCart, currency, locale)
  }

  /**
   * Valide le panier (et décrémente les stocks)
   * @param storeCode
   * @param uuid
   * @param params
   * @param accountId
   * @return
   */
  def queryCartValidate(storeCode: String, uuid: String, params: CartParameters, accountId:Option[Mogopay.Document]): Map[String, Any] = {
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
  def queryCartItemUpdate(storeCode: String, uuid: String, cartItemId: String, params: CartParameters, cmd: UpdateCartItemRequest, accountId:Option[Mogopay.Document]): Map[String, Any] = {
    val locale = _buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val cart = _initCart(storeCode, uuid, accountId)

    val optCartItem = cart.cartItems.find { item => item.id == cartItemId}
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
  def queryCartItemRemove(storeCode: String, uuid: String, cartItemId: String, params: CartParameters, accountId:Option[Mogopay.Document]): Map[String, Any] = {
    val locale = _buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val cart = _initCart(storeCode, uuid, accountId)

    val optCartItem = cart.cartItems.find { item => item.id == cartItemId}
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
  def queryCartCouponAdd(storeCode: String, uuid: String, couponCode: String, params: CouponParameters, accountId:Option[Mogopay.Document]): Map[String, Any] = {
    val locale = _buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val cart = _initCart(storeCode, uuid, accountId)

    val optCoupon = CouponDao.findByCode(cart.storeCode, couponCode)
    if (optCoupon.isDefined) {
      val coupon = optCoupon.get
      if (cart.coupons.exists { c => couponCode == c.code}) {
        throw new DuplicateException("")
      } else if (!couponHandler.consumeCoupon(cart.storeCode, coupon)) {
        throw new InsufficientStockCouponException()
      }
      else {
        val newCoupon = StoreCoupon(coupon.id, coupon.code)

        val coupons = newCoupon :: cart.coupons
        val updatedCart = _unvalidateCart(cart).copy(coupons = coupons)
        StoreCartDao.save(updatedCart)

        val computeCart = _computeStoreCart( updatedCart, params.country, params.state)
        _renderCart(computeCart, currency, locale)
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
  def queryCartCouponDelete(storeCode: String, uuid: String, couponCode: String, params: CouponParameters, accountId:Option[Mogopay.Document]): Map[String, Any] = {
    val locale = _buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val cart = _initCart(storeCode, uuid, accountId)

    val optCoupon = CouponDao.findByCode(cart.storeCode, couponCode)
    if (optCoupon.isDefined) {
      val existCoupon = cart.coupons.find { c => couponCode == c.code}
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
  def queryCartPaymentPrepare(storeCode: String, uuid: String, params: PrepareTransactionParameters, accountId:Option[Mogopay.Document]): Map[String, Any] = {
    val locale = _buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val cart = _initCart(storeCode, uuid, accountId)

    val company = CompanyDao.findByCode(cart.storeCode)

    // Validation du panier au cas où il ne l'était pas déjà
    val valideCart = _validateCart(cart).copy(countryCode = params.country, stateCode = params.state, rate = Some(currency))

    // Calcul des données du panier
    val cartTTC = _computeStoreCart(valideCart, params.country, params.state)

    // Suppression de la transaction en cours (si elle existe). Une nouvelle transaction sera créé
    if (cart.inTransaction) backOfficeHandler.deletePendingTransaction(cart.transactionUuid)

    // Création de la transaction
    val updatedCart = DB localTx { implicit session =>
      backOfficeHandler.createTransaction(valideCart.copy(inTransaction = true), cart.storeCode, cartTTC, cart.transactionUuid, currency, params.buyer, company.get, params.shippingAddress)
    }

    StoreCartDao.save(updatedCart)

    val renderedCart = _renderTransactionCart(cartTTC, currency, locale)
    Map(
      "amount" -> renderedCart("finalPrice"),
      "currencyCode" -> currency.code,
      "currencyRate" -> currency.rate.doubleValue(),
      "transactionExtra" -> renderedCart
    )
  }

  /**
   * Complète le panier après un paiement réalisé avec succès. Le contenu du panier est envoyé par mail comme justificatif
   * et un nouveau panier est créé
   * @param storeCode
   * @param uuid
   * @param params
   * @param accountId
   */
  def queryCartPaymentCommit(storeCode: String, uuid: String, params: CommitTransactionParameters, accountId:Option[Mogopay.Document]): Unit = {
    val locale = _buildLocal(params.lang, params.country)

    val cart = _initCart(storeCode, uuid, accountId)

    val productIds = cart.cartItems.map { item =>
        UserActionRegistration.register(storeCode, uuid, item.productId.toString, UserAction.Purchase)
        item.productId.toString
    }
    CartRegistration.register(storeCode, uuid, productIds)

    val transactionCart = cart.copy(transactionUuid = params.transactionUuid)
    BOCartDao.findByTransactionUuid(cart.transactionUuid) match {
      case Some(boCart) =>
        DB localTx { implicit session =>
          backOfficeHandler.completeTransaction(boCart, params.transactionUuid)

          cart.cartItems.foreach {cartItem =>
            val productAndSku = ProductDao.getProductAndSku(transactionCart.storeCode, cartItem.skuId)
            val product = productAndSku.get._1
            val sku = productAndSku.get._2
            salesHandler.incrementSales(transactionCart.storeCode, product, sku, cartItem.quantity)
          }
          val updatedCart = StoreCart(storeCode = transactionCart.storeCode, dataUuid = transactionCart.dataUuid, userUuid = transactionCart.userUuid)
          StoreCartDao.save(updatedCart)

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
  def queryCartPaymentCancel(storeCode: String, uuid: String, params: CancelTransactionParameters, accountId:Option[Mogopay.Document]): Map[String, Any] = {
    val locale = _buildLocal(params.lang, params.country)
    val currency = queryCurrency(storeCode, params.currency)

    val cart = _initCart(storeCode, uuid, accountId)

    val updatedCart = _cancelCart(cart)
    val computeCart = _computeStoreCart(updatedCart, params.country, params.state)
    _renderCart(computeCart, currency, locale)
  }

  /**
   * Supprime tous les paniers expirés
   */
  def cleanup(): Unit = {
    StoreCartDao.getExpired.map { cart =>
      StoreCartDao.delete(_clearCart(cart))
    }
  }

  /**
   * Construit un Locale à partir de la langue et du pays.<br/>
   * Si la lang == "_all" alors la langue par défaut est utilisée<br/>
   * Si le pays vaut None alors le pays par défaut est utiulisé
   * @param lang
   * @param country
   * @return
   */
  private def _buildLocal(lang: String, country: Option[String]) : Locale = {
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
    def getOrCreateStoreCart(cart: Option[StoreCart]) : StoreCart = {
      cart match {
        case Some(c) => c
        case None =>
          val c = new StoreCart(storeCode = storeCode, dataUuid = uuid, userUuid = currentAccountId)
          StoreCartDao.save(c)
          c
      }
    }

    if (currentAccountId.isDefined) {
      val cartAnonyme = StoreCartDao.findByDataUuidAndUserUuid(uuid, None);
      val cartAuthentifie = getOrCreateStoreCart(StoreCartDao.findByDataUuidAndUserUuid(uuid, currentAccountId));

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
      getOrCreateStoreCart(StoreCartDao.findByDataUuidAndUserUuid(uuid, None));
    }
  }

  /**
   * Réinitialise le panier (en incrémentant en nombre d'utilisation des coupons)
   * @param cart
   * @return
   */
  private def _clearCart(cart: StoreCart): StoreCart = {
    val unvalidateCart = _unvalidateCart(cart)
    if (unvalidateCart.inTransaction) _cancelCart(unvalidateCart)

    cart.coupons.foreach(coupon => {
      val optCoupon = CouponDao.findByCode(cart.storeCode, coupon.code)
      if (optCoupon.isDefined) couponHandler.releaseCoupon(cart.storeCode, optCoupon.get)
    })

    val updatedCart = new StoreCart(storeCode = cart.storeCode, dataUuid = cart.dataUuid, userUuid = cart.userUuid)
    StoreCartDao.save(updatedCart)
    updatedCart
  }

  /**
   * Annule la transaction courante et génère un nouveua uuid au panier
   * @param cart
   * @return
   */
  private def _cancelCart(cart: StoreCart): StoreCart = {
    val boCart = BOCartDao.findByTransactionUuid(cart.transactionUuid)
    if (boCart.isDefined) backOfficeHandler.failedTransaction(boCart.get)

    val updatedCart = cart.copy(inTransaction = false, transactionUuid = UUID.randomUUID().toString)
    StoreCartDao.save(updatedCart)
    updatedCart
  }

  /**
   * Valide le panier en décrémentant les stocks
   * @param cart
   * @return
   */
  @throws[InsufficientStockException]
  private def _validateCart(cart: StoreCart) : StoreCart = {
    if (!cart.validate) {
      DB localTx { implicit session =>
        cart.cartItems.foreach { cartItem =>
          val productAndSku = ProductDao.getProductAndSku(cart.storeCode, cartItem.skuId)
          val product = productAndSku.get._1
          val sku = productAndSku.get._2
          stockHandler.decrementStock(cart.storeCode, product, sku, cartItem.quantity, cartItem.startDate)
        }
      }

      val updatedCart = cart.copy(validate = true)
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
  private def _unvalidateCart(cart: StoreCart) : StoreCart = {
    if (cart.validate) {
      DB localTx { implicit session =>
        cart.cartItems.foreach { cartItem =>
          val productAndSku = ProductDao.getProductAndSku(cart.storeCode, cartItem.skuId)
          val product = productAndSku.get._1
          val sku = productAndSku.get._2
          stockHandler.incrementStock(cart.storeCode, product, sku, cartItem.quantity, cartItem.startDate)
        }
      }

      val updatedCart = cart.copy(validate = false)
      StoreCartDao.save(updatedCart)
      updatedCart
    }
    else cart
  }

  /**
   * Fusionne le panier source avec le panier cible et renvoie le résultat
   * de la fusion
   * @param source
   * @param target
   * @return
   */
  private def _fusion(source: StoreCart, target: StoreCart) : StoreCart = {
    def _fusionCartItem(source: List[StoreCartItem], target: StoreCart) : StoreCart = {
      if (source.isEmpty) target
      else _fusionCartItem(source.tail, _addCartItemIntoCart(target, source.head))
    }
    def _fusionCoupon(source: List[StoreCoupon], target: StoreCart) : StoreCart = {
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
  private def _addCartItemIntoCart(cart: StoreCart, cartItem : StoreCartItem) : StoreCart = {
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
  private def _findCartItem(cart: StoreCart, cartItem : StoreCartItem) : Option[StoreCartItem] = {
    if (cartItem.xtype == ProductType.SERVICE) None
    else cart.cartItems.find {ci: StoreCartItem => ci.productId == cartItem.productId && ci.skuId == cartItem.skuId}
  }

  /**
   * Ajoute un coupon au panier s'il n'existe pas déjà (en comparant les codes des coupons)
   * @param cart
   * @param coupon
   * @return
   */
  private def _addCouponIntoCart(cart: StoreCart, coupon : StoreCoupon) : StoreCart = {
    val existCoupon = cart.coupons.find {c: StoreCoupon => c.code == coupon.code}
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
  private def _computeStoreCart(cart: StoreCart, countryCode: Option[String], stateCode: Option[String]) : Cart = {
    val priceEndPriceCartItems = _computeCartItem(cart.storeCode, cart.cartItems, countryCode, stateCode)
    val price = priceEndPriceCartItems._1
    val endPrice = priceEndPriceCartItems._2
    val cartItems = priceEndPriceCartItems._3

    val reductionCoupons = couponHandler.computeCoupons(cart.storeCode, cart.coupons, cartItems)

    val promoAvailable = CouponDao.findPromotionsThatOnlyApplyOnCart(cart.storeCode)
    val reductionPromotions = couponHandler.computePromotions(cart.storeCode, promoAvailable, cartItems)

    val reduction = reductionCoupons.reduction + reductionPromotions.reduction
    val coupons = reductionCoupons.coupons ::: reductionPromotions.coupons

    val count = _calculateCount(cartItems)
    Cart(price, endPrice, reduction, endPrice - reduction, count, cart.transactionUuid,
      cartItems.toArray, coupons.toArray)
  }

  /**
   * Calcul les montant de la liste des StoreCartItem et renvoie (prix hors taxe du panier, prix taxé du panier, liste CartItemVO avec prix)
   * @param cartItems
   * @param countryCode
   * @param stateCode
   * @return
   */
  private def _computeCartItem(storeCode: String, cartItems: List[StoreCartItem], countryCode: Option[String], stateCode: Option[String]) : (Long, Long, List[CartItem]) = {
    if (cartItems.isEmpty) (0, 0, List())
    else {
      val priceEndPriceAndCartItems = _computeCartItem(storeCode, cartItems.tail, countryCode, stateCode)

      val cartItem = cartItems.head
      val product = ProductDao.get(storeCode, cartItem.productId).get
      val tax = taxRateHandler.findTaxRateByProduct(product, countryCode, stateCode)
      val price = cartItem.price
      val salePrice = cartItem.salePrice
      val endPrice = taxRateHandler.calculateEndPrice(price, tax)
      val saleEndPrice = taxRateHandler.calculateEndPrice(salePrice, tax)
      val totalPrice = price * cartItem.quantity
      val saleTotalPrice = salePrice * cartItem.quantity
      val totalEndPrice = taxRateHandler.calculateEndPrice(totalPrice, tax)
      val saleTotalEndPrice = taxRateHandler.calculateEndPrice(saleTotalPrice, tax)

      val newCartItem = CartItem(cartItem.id, cartItem.productId, cartItem.productName, cartItem.xtype, cartItem.calendarType,
        cartItem.skuId, cartItem.skuName, cartItem.quantity, price, endPrice, tax, totalPrice, totalEndPrice,
        salePrice, saleEndPrice, saleTotalPrice, saleTotalEndPrice,
        cartItem.startDate, cartItem.endDate, cartItem.registeredCartItems.toArray, cartItem.shipping)

      (priceEndPriceAndCartItems._1 + saleTotalPrice, priceEndPriceAndCartItems._2 + saleTotalEndPrice.getOrElse(saleTotalPrice), newCartItem :: priceEndPriceAndCartItems._3)
    }
  }

  /**
   * Calcule le nombre d'item du panier en prenant en compte les quantités
   * @param list
   * @return
   */
  private def _calculateCount(list: List[CartItem]) : Int = {
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
    val renderCart = _renderTransactionCart(cartTTC, cart.rate.get, locale)

    val template = templateHandler.getTemplate(storeCode, "mail-cart.mustache")

    val mailContent = templateHandler.mustache(template, write(renderCart))
    val eol = mailContent.indexOf('\n')
    require(eol > 0, "No new line found in mustache file to distinguish subject from body")
    val subject = mailContent.substring(0, eol)
    val body = mailContent.substring(eol + 1)

    EmailHandler.Send.to(
      Mail(
        (Settings.Mail.defaultFrom -> storeCode),
        List(email),
        List(),
        List(),
        subject,
        body,
        Some(body)
      ))
  }

  private def _renderCart(cart:Cart, currency:Currency, locale:Locale):Map[String,Any]={
    var map :Map[String,Any]= Map()
    map+=("count" -> cart.count)
    map+=("cartItemVOs" -> cart.cartItemVOs.map(item => _renderCartItem(item,currency,locale)))
    map+=("coupons"-> cart.coupons.map(c => _renderCoupon(c,currency, locale)))
    map++=_renderPriceCart(cart,currency,locale)
    map
  }

  /**
   * Idem que renderCart à quelques différences près d'où la dupplication de code
   * @param cart
   * @param rate
   * @return
   */
  private def _renderTransactionCart(cart:Cart, rate:Currency, locale: Locale):Map[String,Any]={
    var map :Map[String,Any]= Map(
      "uuid" -> cart.uuid,
      "count" -> cart.count,
      "cartItemVOs" -> cart.cartItemVOs.map(item => _renderTransactionCartItem(item,rate, locale)),
      "coupons"-> cart.coupons.map(c => _renderTransactionCoupon(c,rate, locale))
    )
    map++=_renderTransactionPriceCart(cart,rate, locale)
    map
  }

  /**
   * Renvoie un coupon JSONiné augmenté par un calcul de prix formaté
   * @param coupon
   * @param currency
   * @param locale
   * @return
   */
  private def _renderCoupon(coupon:Coupon, currency:Currency, locale:Locale) = {
    implicit def json4sFormats: Formats = DefaultFormats + FieldSerializer[Coupon]() ++ JodaTimeSerializers.all
    val jsonCoupon = parse(write(coupon))

    //code from renderPriceCoupon
    val formatedPrice = rateService.formatPrice(coupon.price, currency, locale)
    val additionalsData = parse(write(Map("formatedPrice" -> formatedPrice)))

    jsonCoupon merge additionalsData
  }

  private def _renderTransactionCoupon(coupon:Coupon, rate:Currency, locale: Locale) = {
    implicit def json4sFormats: Formats = DefaultFormats + FieldSerializer[Coupon]() ++ JodaTimeSerializers.all
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
  private def _renderCartItem(item:CartItem,currency:Currency,locale:Locale) ={
    import org.json4s.native.JsonMethods._
    //import org.json4s.native.Serialization
    import org.json4s.native.Serialization.write
    implicit def json4sFormats: Formats = DefaultFormats + FieldSerializer[CartItem]() + new org.json4s.ext.EnumNameSerializer(ProductCalendar) ++ JodaTimeSerializers.all
    val jsonItem = parse(write(item))

    val formatedPrice = rateService.formatPrice(item.price, currency, locale)
    val formatedEndPrice = if(item.endPrice.isEmpty) None else Some(rateService.formatPrice(item.endPrice.get, currency, locale))
    val formatedTotalPrice = rateService.formatPrice(item.totalPrice, currency, locale)
    val formatedTotalEndPrice = if(item.totalEndPrice.isEmpty) None else Some(rateService.formatPrice(item.totalEndPrice.get, currency, locale))

    val additionalsData = parse(write(Map(
      "formatedPrice" -> formatedPrice,
      "formatedEndPrice" -> formatedEndPrice,
      "formatedTotalPrice" -> formatedTotalPrice,
      "formatedTotalEndPrice" -> formatedTotalEndPrice
    )))

    //TODO Traduction aussi du nom en traduisant le produit et le sku
    /*
    price["productName"] = translateName(cartItem.productId, locale.language, cartItem.productName)
    price["skuName"] = translateName(cartItem.skuId, locale.language, cartItem.skuName)
    */

    jsonItem merge additionalsData
  }

  private def _renderTransactionCartItem(item:CartItem,rate:Currency, locale: Locale) ={
    import org.json4s.native.JsonMethods._
    import org.json4s.native.Serialization.write
    //implicit def json4sFormats: Formats = DefaultFormats + FieldSerializer[CartItemVO]()
    import Json4sProtocol._
    val jsonItem = parse(write(item))

    val price = rateService.calculateAmount(item.price, rate)
    val endPrice = rateService.calculateAmount(item.endPrice.getOrElse(item.price), rate)
    val totalPrice = rateService.calculateAmount(item.totalPrice, rate)
    val totalEndPrice = rateService.calculateAmount(item.totalEndPrice.getOrElse(item.totalPrice), rate)

    val updatedData = parse(write(Map(
      "price" -> price,
      "endPrice" -> endPrice,
      "totalPrice" -> totalPrice,
      "totalEndPrice" -> totalEndPrice,
      "formatedPrice" -> rateService.formatLongPrice(item.price, rate, locale),
      "formatedEndPrice" -> rateService.formatLongPrice(item.endPrice.getOrElse(item.price), rate, locale),
      "formatedTotalPrice" -> rateService.formatLongPrice(item.totalPrice, rate, locale),
      "formatedTotalEndPrice" -> rateService.formatLongPrice(item.totalEndPrice.getOrElse(item.totalPrice), rate, locale)
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
  private def _renderPriceCart(cart:Cart, currency:Currency,locale:Locale)={
    val formatedPrice = rateService.formatLongPrice(cart.price, currency, locale)
    val formatedEndPrice = rateService.formatLongPrice(cart.endPrice, currency, locale)
    val formatedReduction = rateService.formatLongPrice(cart.reduction, currency, locale)
    val formatedFinalPrice =  rateService.formatLongPrice(cart.finalPrice, currency, locale)

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

  private def _renderTransactionPriceCart(cart:Cart, rate:Currency, locale: Locale)={
    val price = rateService.calculateAmount(cart.price, rate)
    val endPrice = rateService.calculateAmount(cart.endPrice, rate)
    val reduction = rateService.calculateAmount(cart.reduction, rate)
    val finalPrice= rateService.calculateAmount(cart.finalPrice, rate)

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
