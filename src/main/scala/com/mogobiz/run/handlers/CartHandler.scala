package com.mogobiz.run.handlers

import java.io.ByteArrayOutputStream
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
import com.mogobiz.run.model.Mogobiz.DeliveryStatus
import com.mogobiz.run.model.Mogobiz.TransactionStatus
import com.mogobiz.run.model.Mogobiz.TransactionStatus._
import com.mogobiz.run.model.Mogobiz._
import com.mogobiz.run.model.ES.{BOCart => BOCartES, BOCartItem => BOCartItemES, BODelivery => BODeliveryES, BOReturnedItem => BOReturnedItemES, BOProduct => BOProductES, BORegisteredCartItem => BORegisteredCartItemES}
import com.mogobiz.run.model.Render.{Coupon, RegisteredCartItem, CartItem, Cart}
import com.mogobiz.run.model.RequestParameters._
import com.mogobiz.run.model._
import com.mogobiz.run.services.RateBoService
import com.mogobiz.run.utils.Utils
import com.mogobiz.utils.{QRCodeUtils, SymmetricCrypt}
import com.sun.org.apache.xml.internal.security.utils.Base64
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

    // Suppression de la transaction en cours (si elle existe). Une nouvelle transaction sera créé
    val cartWithoutBOCart = _deletePendingBOCart(valideCart)

    // Calcul des données du panier
    val cartTTC = _computeStoreCart(cartWithoutBOCart, params.country, params.state)

    // Création de la transaction
    val updatedCart = DB localTx { implicit session =>
      _createBOCart(cartWithoutBOCart, cartTTC, currency, params.buyer, company.get, params.shippingAddress)
    }

    StoreCartDao.save(updatedCart)

    val renderedCart = _renderTransactionCart(updatedCart, cartTTC, currency, locale)
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

    val transactionCart = cart.copy(transactionUuid = Some(params.transactionUuid))
    BOCartDao.load(transactionCart.boCartUuid.get) match {
      case Some(boCart) =>
        DB localTx { implicit session =>
          val transactionBoCart = boCart.copy(transactionUuid = Some(params.transactionUuid), status = TransactionStatus.COMPLETE)
          BOCartDao.updateStatus(transactionBoCart)
          exportBOCartIntoES(storeCode, transactionBoCart)

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
   * Crée un BOCart à partir des données fournis en générant si nécessaire les QRCode correspondant. Renvoi
   * un panier en lien avec le BOCart créé
   * @param storeCart
   * @param cart
   * @param rate
   * @param buyer
   * @param company
   * @param shippingAddress
   * @param session
   * @return
   */
  private def _createBOCart(storeCart: StoreCart, cart: Render.Cart, rate: Currency, buyer: String, company: Company, shippingAddress: String)(implicit session: DBSession) : StoreCart = {
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

            val encryptedQrCodeContent = SymmetricCrypt.encrypt(qrCodeContent, company.aesPassword, "AES")
            val output = new ByteArrayOutputStream()
            QRCodeUtils.createQrCode(output, encryptedQrCodeContent, 256, "png")
            val qrCodeBase64 = Base64.encode(output.toByteArray)

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

      BOCartItemDao.create(sku, cartItem, boCart, Some(boDelivery), boProduct.id)

      storeCartItem.copy(registeredCartItems = newStoreRegistedCartItems.toList)
    }

    exportBOCartIntoES(storeCart.storeCode, boCart)

    storeCart.copy(boCartUuid = Some(boCart.uuid), cartItems = newStoreCartItems.toList)
  }

  /**
   * Supprime le BOCart correspondant au panier s'il est en statut Pending
   * et renvoi un panier sans lien avec le boCart supprimé
   * @param cart
   * @return
   */
  private def _deletePendingBOCart(cart: StoreCart) : StoreCart = {
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
          BOCartESDao.delete(boCart.get.uuid)

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
    if (cart.boCartUuid.isDefined) {
      val boCart = BOCartDao.load(cart.boCartUuid.get)
      if (boCart.isDefined) {
        // Mise à jour du statut
        val newBoCart = boCart.get.copy(status = TransactionStatus.FAILED)
        BOCartDao.updateStatus(newBoCart)
        exportBOCartIntoES(cart.storeCode, newBoCart)
      }

      val updatedCart = cart.copy(boCartUuid = None, transactionUuid = None)
      StoreCartDao.save(updatedCart)
      updatedCart
    }
    else cart
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
    Cart(price, endPrice, reduction, endPrice - reduction, count, cartItems.toArray, coupons.toArray)
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
    val renderCart = _renderTransactionCart(cart, cartTTC, cart.rate.get, locale)

    val template = templateHandler.getTemplate(storeCode, "mail-cart.mustache")

    val (subject, body) = templateHandler.mustache(template, write(renderCart))
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
  private def _renderTransactionCart(storeCart: StoreCart, cart:Cart, rate:Currency, locale: Locale):Map[String,Any]={
    var map :Map[String,Any]= Map(
      "boCartUuid" -> storeCart.boCartUuid.getOrElse(""),
      "transactionUuid" -> storeCart.transactionUuid.getOrElse(""),
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

  def exportBOCartIntoES(storeCode: String, boCart: BOCart) = {
    DB readOnly { implicit session =>

      // Conversion des BOCartItem
      val cartItems = BOCartItemDao.findByBOCart(boCart).map { boCartItem =>
        // Conversion des BOProducts
        val boProducts = BOCartItemDao.getBOProducts(boCartItem).map { boProduct =>
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
          BOProductES(acquittement = boProduct.acquittement,
            principal = boProduct.principal,
            price = boProduct.price,
            product = ProductDao.get(storeCode, boProduct.productFk).get,
            registeredCartItem = boRegisteredCartItems,
            uuid = boProduct.uuid)
        }

        // Convertion du BODelivery pour ES
        val boDeliveryEs = BODeliveryDao.findByBOCartItem(boCartItem) match {
          case Some(boDelivery) =>
            Some(new BODeliveryES(status = boDelivery.status,
              tracking = boDelivery.tracking,
              extra = boDelivery.extra,
              uuid = boDelivery.uuid))
          case _ => None
        }

        // Concertion des BOReturnedItem pour ES
        val boReturnedItems = BOReturnedItemDao.findByBOCartItem(boCartItem).map { boReturnedItem =>
          val boreturn = BOReturnDao.findByBOReturnedItem(boReturnedItem).get

          BOReturnedItemES(motivation = boreturn.motivation,
            returnStatus = boreturn.status,
            quantity = boReturnedItem.quantity,
            refunded = boReturnedItem.refunded,
            totalRefunded = boReturnedItem.totalRefunded,
            status = boReturnedItem.status,
            uuid = boReturnedItem.uuid)
        }

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
          bOProduct = boProducts,
          bODelivery = boDeliveryEs,
          bOReturnedItem = boReturnedItems,
          uuid = boCartItem.uuid)
      }

      val boCartES = new BOCartES(transactionUuid = boCart.transactionUuid,
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

      EsClient.update[BOCartES](Settings.backoffice.EsIndex, boCartES, true, false)
    }
  }
}

object StoreCartDao {

  import com.sksamuel.elastic4s.ElasticDsl._

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

object BOCartESDao {

  val index = Settings.backoffice.EsIndex

  def save(boCart: BOCartES): Boolean = {
    EsClient.update[BOCartES](index, boCart, true, false)
  }

  def delete(uuid: String) : Unit = {
    EsClient.delete[StoreCart](index, uuid, false)
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
      withSQL {select.from(BOCartDao as t).where.eq(t.uuid, uuid)}.map(BOCartDao(t.resultName)).single().apply()
    }
  }

  def updateStatus(boCart: BOCart) : Unit = {
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

  def create(buyer: String, companyId: Long, rate: Currency, price: Long)(implicit session: DBSession):BOCart = {

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
      deleteFrom(BOCartDao).where.eq(BOCartDao.column.id,  boCart.id)
    }.update.apply()
  }

}

object BODeliveryDao extends SQLSyntaxSupport[BODelivery] with BoService {

  override val tableName = "b_o_delivery"

  def apply(rn: ResultName[BODelivery])(rs:WrappedResultSet): BODelivery = new BODelivery(
    rs.get(rn.id),
    rs.get(rn.bOCartFk),
    DeliveryStatus(rs.get(rn.status)),
    rs.get(rn.tracking),
    rs.get(rn.extra),
    rs.get(rn.dateCreated),
    rs.get(rn.lastUpdated),
    rs.get(rn.uuid))

  def create(boCart: BOCart, extra: Option[String])(implicit session: DBSession) : BODelivery = {
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
      select.from(BODeliveryDao as t).where.eq(t.id, boCartItem.id)
    }.map(BODeliveryDao(t.resultName)).single().apply()
  }

  def delete(boDeliveryId : Long)(implicit session: DBSession) = {
    withSQL {
      deleteFrom(BODeliveryDao).where.eq(BODeliveryDao.column.id, boDeliveryId)
    }.update.apply()
  }
}

object BOReturnedItemDao extends SQLSyntaxSupport[BOReturnedItem] with BoService {

  override val tableName = "b_o_returned_item"

  def apply(rn: ResultName[BOReturnedItem])(rs:WrappedResultSet): BOReturnedItem = new BOReturnedItem(
    rs.get(rn.id),
    rs.get(rn.bOCartItemFk),
    rs.get(rn.bOReturnFk),
    rs.get(rn.quantity),
    rs.get(rn.refunded),
    rs.get(rn.totalRefunded),
    ReturnedItemStatus(rs.get(rn.status)),
    rs.get(rn.dateCreated),
    rs.get(rn.lastUpdated),
    rs.get(rn.uuid))

  def findByBOCartItem(boCartItem: BOCartItem)(implicit session: DBSession): List[BOReturnedItem] = {
    val t = BOReturnedItemDao.syntax("t")
    withSQL {
      select.from(BOReturnedItemDao as t).where.eq(t.bOCartItemFk, boCartItem.id)
    }.map(BOReturnedItemDao(t.resultName)).list().apply()
  }
}

object BOReturnDao extends SQLSyntaxSupport[BOReturn] with BoService {

  override val tableName = "b_o_return"

  def apply(rn: ResultName[BOReturn])(rs:WrappedResultSet): BOReturn = new BOReturn(
    rs.get(rn.id),
    rs.get(rn.bOCartFk),
    rs.get(rn.motivation),
    ReturnStatus(rs.get(rn.status)),
    rs.get(rn.dateCreated),
    rs.get(rn.lastUpdated),
    rs.get(rn.uuid))

  def findByBOReturnedItem(boReturnedItem: BOReturnedItem)(implicit session: DBSession): Option[BOReturn] = {
    val t = BOReturnDao.syntax("t")
    withSQL {
      select.from(BOReturnDao as t).where.eq(t.id, boReturnedItem.bOReturnFk)
    }.map(BOReturnDao(t.resultName)).single().apply()
  }
}

object BOCartItemDao extends SQLSyntaxSupport[BOCartItem] with BoService {

  override val tableName = "b_o_cart_item"

  def apply(rn: ResultName[BOCartItem])(rs:WrappedResultSet): BOCartItem = new BOCartItem(
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
    rs.get(rn.uuid))

  def create(sku: Mogobiz.Sku, cartItem : Render.CartItem, boCart: BOCart, bODelivery: Option[BODelivery], boProductId : Long)(implicit session: DBSession) : BOCartItem = {
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
      UUID.randomUUID().toString
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
        BOCartItemDao.column.uuid -> newBOCartItem.uuid
      )
    }

    sql"insert into b_o_cart_item_b_o_product(b_o_products_fk, boproduct_id) values(${newBOCartItem.id},$boProductId)"
      .update.apply()

    newBOCartItem
  }

  def findByBOCart(boCart:BOCart)(implicit session: DBSession):List[BOCartItem] = {
    val t = BOCartItemDao.syntax("t")
    withSQL {
      select.from(BOCartItemDao as t).where.eq(t.bOCartFk, boCart.id)
    }.map(BOCartItemDao(t.resultName)).list().apply()
  }

  def getBOProducts(boCartItem: BOCartItem)(implicit session: DBSession): List[BOProduct] = {
    sql"select p.* from b_o_cart_item_b_o_product ass inner join b_o_product p on ass.boproduct_id=p.id where b_o_products_fk=${boCartItem.id}"
      .map(rs => BOProductDao(rs)).list().apply()
  }

  def delete(boCartItem : BOCartItem)(implicit session: DBSession) = {
    sql"delete from b_o_cart_item_b_o_product where b_o_products_fk=${boCartItem.id}".update.apply()

    val result = withSQL {
      deleteFrom(BOCartItemDao).where.eq(BOCartItemDao.column.id, boCartItem.id)
    }.update.apply()
    if (boCartItem.bODeliveryFk.isDefined) BODeliveryDao.delete(boCartItem.bODeliveryFk.get) else result
  }
}

object BOTicketTypeDao extends SQLSyntaxSupport[BOTicketType] with BoService {

  override val tableName = "b_o_ticket_type"

  def apply(rn: ResultName[BOTicketType])(rs:WrappedResultSet): BOTicketType = new BOTicketType(
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

  def create(boTicketId: Long, sku: Sku, cartItem: Render.CartItem, registeredCartItem: Render.RegisteredCartItem, shortCode: Option[String], qrCode: Option[String], qrCodeContent: Option[String], boProductId: Long)(implicit session: DBSession) : BOTicketType = {
    val newBOTicketType = new BOTicketType(
      boTicketId,
      1,  // Un seul ticket par bénéficiaire
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

  def findByBOProduct(boProductId:Long)(implicit session: DBSession):List[BOTicketType]={
    val t = BOTicketTypeDao.syntax("t")
    withSQL {
      select.from(BOTicketTypeDao as t).where.eq(t.bOProductFk, boProductId)
    }.map(BOTicketTypeDao(t.resultName)).list().apply()
  }

  def delete(boTicketType:BOTicketType)(implicit session: DBSession) {
    withSQL {
      deleteFrom(BOTicketTypeDao).where.eq(BOTicketTypeDao.column.id, boTicketType.id)
    }.update.apply()
  }
}

object BOProductDao extends SQLSyntaxSupport[BOProduct] with BoService {

  override val tableName = "b_o_product"

  def apply(rs:WrappedResultSet): BOProduct = new BOProduct(
    rs.long("id"),
    rs.boolean("acquittement"),
    rs.long("price"),
    rs.boolean("principal"),
    rs.long("product_fk"),
    rs.get("date_created"),
    rs.get("last_updated"),
    rs.get("uuid"))

  def create(price:Long, principal:Boolean, productId:Long)(implicit session: DBSession) : BOProduct = {
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

  def delete(boProduct:BOProduct)(implicit session: DBSession) {
    withSQL {
      deleteFrom(BOProductDao).where.eq(BOProductDao.column.id, boProduct.id)
    }.update.apply()
  }
}