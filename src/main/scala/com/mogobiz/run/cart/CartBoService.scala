package com.mogobiz.run.cart

import java.io.ByteArrayOutputStream
import java.util.{UUID, Locale}
import com.mogobiz.run.config.{Settings, MogobizDBsWithEnv}
import com.mogobiz.run.handlers.{CouponDao, ProductDao, StoreCartDao}
import com.mogobiz.run.model.Mogobiz.{ProductCalendar, ProductType}
import com.mogobiz.run.model.Render.{Cart, RegisteredCartItem, CartItem}
import com.mogobiz.run.model._
import com.mogobiz.run.cart.CustomTypes.CartErrors
import com.mogobiz.run.cart.transaction._
import com.mogobiz.run.cart.domain._
import com.mogobiz.run.utils.Utils
import com.mogobiz.utils._
import com.sun.org.apache.xml.internal.security.utils.Base64
import com.typesafe.scalalogging.slf4j.Logger
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.slf4j.LoggerFactory
import scalikejdbc._

/**
 * The Cart Back office Service in charge of retrieving/storing data from database
 */
object CartBoService extends BoService {

  // Injection des Handlers
  import com.mogobiz.run.config.HandlersConfig._

  MogobizDBsWithEnv(Settings.Dialect).setupAll()

  private val logger = Logger(LoggerFactory.getLogger("CartBoService"))

  val productService = ProductBoService

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
   * Retrouve un coupon dans la liste des coupons du panier à partir de son code
   * @param cart
   * @param coupon
   * @return
   */
  private def _findCoupon(cart: StoreCart, coupon: StoreCoupon) : Option[StoreCoupon] = {
    cart.coupons.find {c: StoreCoupon => c.code == coupon.code}
  }

  /**
   * Ajoute un coupon au panier s'il n'existe pas déjà (en comparant les codes des coupons)
   * @param cart
   * @param coupon
   * @return
   */
  private def _addCouponIntoCart(cart: StoreCart, coupon : StoreCoupon) : StoreCart = {
    val existCoupon = _findCoupon(cart, coupon)
    if (existCoupon.isDefined) {
      cart
    }
    else {
      val newCoupons = (coupon :: cart.coupons)
      cart.copy(coupons = newCoupons)
    }
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

  private def addError(errors: CartErrors, key: String, msg: String, parameters: Seq[Any]): CartErrors = {
    errors + (key + "." + msg -> parameters)
  }

  /**
   * Calcul les montant de la liste des StoreCartItem et renvoie (prix hors taxe du panier, prix taxé du panier, liste CartItemVO avec prix)
   * @param cartItems
   * @param countryCode
   * @param stateCode
   * @param result
   * @return
   */
  private def _computeCartItem(storeCode: String, cartItems: List[StoreCartItem], countryCode: Option[String], stateCode: Option[String], result: (Long, Option[Long], List[CartItem])) : (Long, Option[Long], List[CartItem]) = {
    if (cartItems.isEmpty) result
    else {
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

      val newResultTotalEndPrice = if (result._2.isDefined && saleTotalEndPrice.isDefined) Some(result._2.get + saleTotalEndPrice.get)
      else if (result._2.isDefined) result._2
      else saleTotalEndPrice
      _computeCartItem(storeCode, cartItems.tail, countryCode, stateCode, (result._1 + saleTotalPrice, newResultTotalEndPrice, newCartItem :: result._3))
    }
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
          val sku = TicketType.get(cartItem.skuId)
          productService.incrementStock(sku, cartItem.quantity, cartItem.startDate)
        }
      }

      val updatedCart = cart.copy(validate = false)
      StoreCartDao.save(updatedCart)
      updatedCart
    }
    else cart
  }

  /**
   * @param poiOpt
   * @return formated location
   */
  private def toEventLocationVO(poiOpt: Option[Poi]) = poiOpt match {
    case Some(poi) =>
      poi.road1.getOrElse("") + " " +
        poi.road2.getOrElse("") + " " +
        poi.city.getOrElse("") + " " +
        poi.postalCode.getOrElse("") + " " +
        poi.state.getOrElse("") + " " +
        poi.city.getOrElse("") + " " +
        poi.countryCode.getOrElse("") + " "
    case None => ""
  }

  /**
   * Récupère le panier correspondant au uuid et au compte client.
   * La méthode gère le panier anonyme et le panier authentifié.
   * @param uuid
   * @param currentAccountId
   * @return
   */
  def initCart(storeCode: String, uuid: String, currentAccountId: Option[String]): StoreCart = {
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
   * Libère tous les paniers expirés
   */
  def cleanExpiredCart: Unit = {
    StoreCartDao.getExpired.map { cart =>
      StoreCartDao.delete(clear(cart))
    }
  }

  /**
   * Crée et ajoute un item au panier
   * @param cart
   * @param ticketTypeId
   * @param quantity
   * @param dateTime
   * @param registeredCartItems
   * @return send back the new cart with the added item
   */
  @throws[AddCartItemException]
  def addItem(cart: StoreCart, ticketTypeId: Long, quantity: Int, dateTime: Option[DateTime], registeredCartItems: List[RegisteredCartItem]): StoreCart = {
    logger.info(s"addItem dateTime : $dateTime")
    println("+++++++++++++++++++++++++++++++++++++++++")
    println(s"dateTime=$dateTime")
    println("+++++++++++++++++++++++++++++++++++++++++")

    var errors: CartErrors = Map()

    // init local vars
    val productAndSku = ProductDao.getProductAndSku(cart.storeCode, ticketTypeId)

    if (productAndSku.isEmpty) {
      errors = addError(errors, "ticketTypeId", "unknown", null)
      throw new AddCartItemException(errors)
    }

    val product = productAndSku.get._1
    val sku = productAndSku.get._2
    val startEndDate = Utils.verifyAndExtractStartEndDate(product, sku, dateTime)

    if (sku.minOrder > quantity || (sku.maxOrder < quantity && sku.maxOrder > -1)) {
      println(sku.minOrder + ">" + quantity)
      println(sku.maxOrder + "<" + quantity)
      errors = addError(errors, "quantity", "min.max.error", List(sku.minOrder, sku.maxOrder))
    }
    if (!dateTime.isDefined && !ProductCalendar.NO_DATE.equals(product.calendarType)) {
      errors = addError(errors, "dateTime", "nullable.error", null)
    }
    else if (dateTime.isDefined && startEndDate == None) {
      errors = addError(errors, "dateTime", "unsaleable.error", null)
    }
    if (product.xtype == ProductType.SERVICE) {
      if (registeredCartItems.size != quantity) {
        errors = addError(errors, "registeredCartItems", "size.error", null)
      }
      else {
        val emptyMails = for {
          item <- registeredCartItems
          if item.email.isEmpty
        } yield item
        if (emptyMails.nonEmpty)
          errors = addError(errors, "registeredCartItems", "email.error", null)
      }
    }

    if (errors.size > 0)
      throw new AddCartItemException(errors)

    val newItemId = UUID.randomUUID().toString
    val registeredItems = registeredCartItems.map { item => item.copy(cartItemId = newItemId)}

    val startDate = if (startEndDate.isDefined) Some(startEndDate.get._1) else None
    val endDate = if (startEndDate.isDefined) Some(startEndDate.get._2) else None
    val salePrice = if (sku.salePrice > 0) sku.salePrice else sku.price
    val cartItem = StoreCartItem(newItemId, product.id, product.name, product.xtype, product.calendarType, sku.id, sku.name, quantity,
      sku.price, salePrice, startDate, endDate, registeredItems, product.shipping)

    val newcart = _addCartItemIntoCart(_unvalidateCart(cart), cartItem)
    StoreCartDao.save(newcart)
    newcart
  }

  /**
   * Met à jour l'item dans le panier avec la nouvelle quantité voulue
   * @param cart
   * @param cartItemId
   * @param quantity the new value wanted
   * @return
   */
  @throws[UpdateCartItemException]
  def updateItem(cart: StoreCart, cartItemId: String, quantity: Int): StoreCart = {
    var errors: CartErrors = Map()

    val optCartItem = cart.cartItems.find { item => item.id == cartItemId}

    if (optCartItem.isDefined) {
      val existCartItem = optCartItem.get
      if (ProductType.SERVICE != existCartItem.xtype && existCartItem.quantity != quantity) {

        // Modification du panier
        val newCartItems = existCartItem.copy(quantity = quantity) :: Utils.remove(cart.cartItems, existCartItem)
        val updatedCart = _unvalidateCart(cart).copy(cartItems = newCartItems)

        StoreCartDao.save(updatedCart)
        updatedCart
      } else {
        println("silent op")
        cart
      }
    } else {
      errors = addError(errors, "item", "item.notfound.error", null)
      throw new UpdateCartItemException(errors)
    }
  }

  /**
   * Supprime l'item du panier
   * @param cart
   * @param cartItemId
   * @return
   */
  @throws[RemoveCartItemException]
  def removeItem(cart: StoreCart, cartItemId: String): StoreCart = {
    val errors: CartErrors = Map()

    val optCartItem = cart.cartItems.find { item => item.id == cartItemId}
    if (optCartItem.isDefined) {
      val existCartItem = optCartItem.get

      val newCartItems = Utils.remove(cart.cartItems, existCartItem)
      val updatedCart = _unvalidateCart(cart).copy(cartItems = newCartItems)

      StoreCartDao.save(updatedCart)
      updatedCart
    }
    else cart
  }

  /**
   * Réinitialise le panier (en incrémentant en nombre d'utilisation des coupons)
   * @param cart
   * @return
   */
  @throws[ClearCartException]
  def clear(cart: StoreCart): StoreCart = {
    val unvalidateCart = _unvalidateCart(cart)
    if (unvalidateCart.inTransaction) cancel(unvalidateCart)

    cart.coupons.foreach(coupon => {
      val optCoupon = CouponDao.findByCode(cart.storeCode, coupon.code)
      if (optCoupon.isDefined) couponHandler.releaseCoupon(cart.storeCode, optCoupon.get)
    })

    val updatedCart = new StoreCart(storeCode = cart.storeCode, dataUuid = cart.dataUuid, userUuid = cart.userUuid)
    StoreCartDao.save(updatedCart)
    updatedCart
  }

  /**
   * Ajoute un coupon au panier
   * @param couponCode
   * @param cart
   * @return
   */
  @throws[AddCouponToCartException]
  def addCoupon(couponCode: String, cart: StoreCart): StoreCart = {
    var errors: CartErrors = Map()

    val optCoupon = CouponDao.findByCode(cart.storeCode, couponCode)

    if (optCoupon.isDefined) {
      val coupon = optCoupon.get

      if (cart.coupons.exists { c => couponCode == c.code}) {
        errors = addError(errors, "coupon", "already.exist", null)
        throw new AddCouponToCartException(errors)
      } else if (!couponHandler.consumeCoupon(cart.storeCode, coupon)) {
        errors = addError(errors, "coupon", "stock.error", null)
        throw new AddCouponToCartException(errors)
      }
      else {
        val newCoupon = StoreCoupon(coupon.id, coupon.code)

        val coupons = newCoupon :: cart.coupons
        val updatedCart = _unvalidateCart(cart).copy(coupons = coupons)
        StoreCartDao.save(updatedCart)
        updatedCart
      }
    }
    else {
      errors = addError(errors, "coupon", "unknown.error", null)
      throw new AddCouponToCartException(errors)
    }
  }


  /**
   * Remove the coupon from the cart
   * @param couponCode
   * @param cart
   * @return
   */
  @throws[RemoveCouponFromCartException]
  def removeCoupon(couponCode: String, cart: StoreCart): StoreCart = {
    var errors: CartErrors = Map()

    val optCoupon = CouponDao.findByCode(cart.storeCode, couponCode)

    if (optCoupon.isDefined) {
      val existCoupon = cart.coupons.find { c => couponCode == c.code}
      if (existCoupon.isEmpty) {
        errors = addError(errors, "coupon", "unknown.error", null)
        throw new RemoveCouponFromCartException(errors)
      }
      else {
        couponHandler.releaseCoupon(cart.storeCode, optCoupon.get)

        // reprise des items existants sauf celui à supprimer
        val coupons = Utils.remove(cart.coupons, existCoupon.get)
        val updatedCart = _unvalidateCart(cart).copy(coupons = coupons)
        StoreCartDao.save(updatedCart)
        updatedCart
      }
    }
    else {
      errors = addError(errors, "coupon", "unknown.error", null)
      throw new RemoveCouponFromCartException(errors)
    }
  }

  /**
   * Transforme le StoreCart en un CartVO en calculant les montants
   * @param cart
   * @param countryCode
   * @param stateCode
   * @return
   */
  def computeStoreCart(cart: StoreCart, countryCode: Option[String], stateCode: Option[String]) : Cart = {
    val priceEndPriceCartItems = _computeCartItem(cart.storeCode, cart.cartItems, countryCode, stateCode, (0, None, List()))
    val price = priceEndPriceCartItems._1
    val endPrice = priceEndPriceCartItems._2
    val cartItems = priceEndPriceCartItems._3

    val reductionCoupons = couponHandler.computeCoupons(cart.storeCode, cart.coupons, cartItems)

    val promoAvailable = CouponDao.findPromotionsThatOnlyApplyOnCart(cart.storeCode)
    val reductionPromotions = couponHandler.computePromotions(cart.storeCode, promoAvailable, cartItems)

    val reduction = reductionCoupons.reduction + reductionPromotions.reduction
    val coupons = reductionCoupons.coupons ::: reductionPromotions.coupons

    val finalPrice = if (endPrice.isDefined) endPrice.get - reduction else price - reduction

    Cart(price, endPrice, reduction, finalPrice, cartItems.length, cart.transactionUuid,
      cartItems.toArray, coupons.toArray)
  }

  /**
   * Valide le panier en décrémentant les stocks
   * @param cart
   * @return
   */
  @throws[ValidateCartException]
  def validateCart(cart: StoreCart) : StoreCart = {

    if (!cart.validate) {
      var errors: CartErrors = Map()

      try {
        DB localTx { implicit session =>
          cart.cartItems.foreach { cartItem =>
            val sku = TicketType.get(cartItem.skuId)
            try {
              productService.decrementStock(sku, cartItem.quantity, cartItem.startDate)
            }
            catch {
              case ex: InsufficientStockException => {
                errors = addError(errors, "quantity", "stock.error", cartItem.id)
                throw ex
              }
            }
          }
        }
      }
      catch {
        case ex: InsufficientStockException => {}
        case ex: Exception => throw ex
      }

      if (errors.size > 0) throw new ValidateCartException(errors)

      val updatedCart = cart.copy(validate = true)
      StoreCartDao.save(updatedCart)
      updatedCart
    }
    else cart
  }

  /**
   * Prépare le panier pour la paiement. Une transaction est créée en base. Si ce n'est pas le cas, le panier est validé
   * @param countryCode
   * @param stateCode
   * @param rate
   * @param cart
   * @param buyer
   * @return
   */
  @throws[ValidateCartException]
  def prepareBeforePayment(countryCode: Option[String], stateCode: Option[String], rate: Currency, cart: StoreCart, buyer: String) = {
    val errors: CartErrors = Map()

    // récup du companyId à partir du storeCode
    val companyId = Company.findByCode(cart.storeCode).get.id

    // Validation du panier au cas où il ne l'était pas déjà
    val valideCart = validateCart(cart)

    // Calcul des données du panier
    val cartTTC = computeStoreCart(valideCart, countryCode, stateCode)

    //TRANSACTION 1 : SUPPRESSIONS
    DB localTx { implicit session =>

      if (cart.inTransaction) {
        // On supprime tout ce qui concerne l'ancien BOCart (s'il est en attente)
        val boCart = BOCart.findByTransactionUuidAndStatus(cartTTC.uuid, TransactionStatus.PENDING)
        if (boCart.isDefined) {

          BOCartItem.findByBOCart(boCart.get).foreach { boCartItem =>
            BOCartItem.bOProducts(boCartItem).foreach { boProduct =>

              //b_o_cart_item_b_o_product (b_o_products_fk,boproduct_id) values(${saleId},${boProductId})
              sql"delete from b_o_cart_item_b_o_product where boproduct_id=${boProduct.id}".update.apply()

              //Product product = boProduct.product;
              BOTicketType.findByBOProduct(boProduct.id).foreach { boTicketType =>
                boTicketType.delete()
              }
              boProduct.delete()
            }
            boCartItem.delete()
          }
          boCart.get.delete()
        }
      }
    }

    val updatedCart = valideCart.copy(inTransaction = true)

    //TRANSACTION 2 : INSERTIONS
    DB localTx { implicit session =>

      val boCartTmp = BOCart(
        id = 0,
        buyer = buyer,
        transactionUuid = cartTTC.uuid,
        xdate = DateTime.now,
        price = cartTTC.price,
        status = TransactionStatus.PENDING,
        currencyCode = rate.code,
        currencyRate = rate.rate,
        companyFk = companyId
      )

      val boCart = BOCart.insertTo(boCartTmp)

      cartTTC.cartItemVOs.foreach { cartItem => {
        val ticketType = TicketType.get(cartItem.skuId)

        // Création du BOProduct correspondant au produit principal
        val boProductTmp = BOProduct(id = 0, principal = true, productFk = cartItem.productId, price = cartItem.totalEndPrice.getOrElse(-1))
        val boProduct = BOProduct.insert2(boProductTmp)
        val boProductId = boProduct.id

        cartItem.registeredCartItemVOs.foreach {
          registeredCartItem => {
            // Création des BOTicketType (SKU)
            var boTicketId = 0
            val boTicketTmp = BOTicketType(id = boTicketId,
              quantity = 1, price = cartItem.totalEndPrice.getOrElse(-1), shortCode = None,
              ticketType = Some(ticketType.name), firstname = registeredCartItem.firstname,
              lastname = registeredCartItem.lastname, email = registeredCartItem.email,
              phone = registeredCartItem.phone, age = 0,
              birthdate = registeredCartItem.birthdate, startDate = cartItem.startDate, endDate = cartItem.endDate,
              dateCreated = DateTime.now, lastUpdated = DateTime.now,
              //bOProduct = boProduct
              bOProductFk = boProductId
            )

            withSQL {
              val b = BOTicketType.column
              boTicketId = newId()

              //génération du qr code uniquement pour les services
              val product = Product.get(cartItem.productId).get

              val boTicket = product.xtype match {
                case ProductType.SERVICE =>
                  val startDateStr = cartItem.startDate.map(d => d.toString(DateTimeFormat.forPattern("dd/MM/yyyy HH:mm")))

                  val shortCode = "P" + boProductId + "T" + boTicketId
                  val qrCodeContent = "EventId:" + product.id + ";BoProductId:" + boProductId + ";BoTicketId:" + boTicketId +
                    ";EventName:" + product.name + ";EventDate:" + startDateStr + ";FirstName:" +
                    boTicketTmp.firstname + ";LastName:" + boTicketTmp.lastname + ";Phone:" + boTicketTmp.phone +
                    ";TicketType:" + boTicketTmp.ticketType + ";shortCode:" + shortCode

                  val encryptedQrCodeContent = SymmetricCrypt.encrypt(qrCodeContent, product.company.get.aesPassword, "AES")
                  val output = new ByteArrayOutputStream()
                  QRCodeUtils.createQrCode(output, encryptedQrCodeContent, 256, "png")
                  val qrCodeBase64 = Base64.encode(output.toByteArray)

                  boTicketTmp.copy(id = boTicketId, shortCode = Some(shortCode), qrcode = Some(qrCodeBase64), qrcodeContent = Some(encryptedQrCodeContent))
                case _ => boTicketTmp.copy(id = boTicketId)
              }

              insert.into(BOTicketType).namedValues(
                b.uuid -> boTicket.uuid,
                b.id -> boTicketId, b.shortCode -> boTicket.shortCode, b.quantity -> boTicket.quantity,
                b.price -> boTicket.price, b.ticketType -> boTicket.ticketType, b.firstname -> boTicket.firstname,
                b.lastname -> boTicket.lastname, b.email -> boTicket.email, b.phone -> boTicket.phone, b.age -> boTicket.age,
                b.birthdate -> boTicket.birthdate, b.startDate -> boTicket.startDate, b.endDate -> boTicket.endDate,
                b.qrcode -> boTicket.qrcode, b.qrcodeContent -> boTicket.qrcodeContent,
                b.dateCreated -> DateTime.now, b.lastUpdated -> DateTime.now,
                b.bOProductFk -> boProductId
              )
            }.update.apply()
          }
        }

        //create Sale
        var saleId = 0
        val sale = BOCartItem(id = saleId,
          code = "SALE_" + boCart.id + "_" + boProductId,
          price = cartItem.price,
          tax = cartItem.tax.get,
          endPrice = cartItem.endPrice.get,
          totalPrice = cartItem.totalPrice,
          totalEndPrice = cartItem.totalEndPrice.get,
          hidden = false,
          quantity = cartItem.quantity,
          startDate = ticketType.startDate,
          endDate = ticketType.stopDate,
          ticketTypeFk = ticketType.id,
          bOCartFk = boCart.id
          //bOCart = boCart
          //bOProducts = [boProduct]
        )
        withSQL {
          val b = BOCartItem.column
          saleId = newId()
          insert.into(BOCartItem).namedValues(
            b.uuid -> sale.uuid,
            b.id -> saleId,
            b.code -> ("SALE_" + boCart.id + "_" + boProductId),
            b.price -> cartItem.price,
            b.tax -> cartItem.tax.get,
            b.endPrice -> cartItem.endPrice.get,
            b.totalPrice -> cartItem.totalPrice,
            b.totalEndPrice -> cartItem.totalEndPrice.get,
            b.hidden -> false,
            b.quantity -> cartItem.quantity,
            b.startDate -> ticketType.startDate,
            b.endDate -> ticketType.stopDate,
            b.ticketTypeFk -> ticketType.id,
            b.bOCartFk -> boCart.id,
            b.dateCreated -> DateTime.now, b.lastUpdated -> DateTime.now
          )
        }.update.apply()

        sql"insert into b_o_cart_item_b_o_product(b_o_products_fk,boproduct_id) values($saleId,$boProductId)".update.apply()
      }
      }
    }

    StoreCartDao.save(updatedCart)

    val renderedCart = CartRenderService.renderTransactionCart(cartTTC, rate)
    Map(
      "amount" -> renderedCart("finalPrice"),
      "currencyCode" -> rate.code,
      "currencyRate" -> rate.rate.doubleValue(),
      "transactionExtra" -> renderedCart
    )
  }

  /**
   * Valide la transaction et crée un nouveau panier vide
   * @param cart
   * @param transactionUuid
   * @return
   */
  def commit(cart: StoreCart, transactionUuid: String): List[Map[String, Any]] = {

    assert(!transactionUuid.isEmpty, "transactionUuid should not be empty")

    BOCart.findByTransactionUuid(cart.uuid) match {
      case Some(boCart) =>

        val boCartItems = BOCartItem.findByBOCart(boCart)
        val emailingData = boCartItems.map { boCartItem =>

          //browse boTicketType to send confirmation mails
          BOCartItem.bOProducts(boCartItem).map { boProduct =>
            val product = boProduct.product
            BOTicketType.findByBOProduct(boProduct.id).map { boTicketType =>
              var map: Map[String, Any] = Map()
              map += ("email" -> boTicketType.email)
              map += ("eventName" -> product.name)
              map += ("startDate" -> boTicketType.startDate)
              map += ("stopDate" -> boTicketType.endDate)
              map += ("location" -> toEventLocationVO(product.poi))
              map += ("type" -> boTicketType.ticketType)
              map += ("price" -> boTicketType.price)
              map += ("qrcode" -> boTicketType.qrcodeContent)
              map += ("shortCode" -> boTicketType.shortCode)

              map
            }
          }.flatten
        }.flatten

        DB localTx { implicit session =>
          // update status and transactionUUID
          withSQL {
            update(BOCart).set(
              BOCart.column.transactionUuid -> transactionUuid,
              BOCart.column.status -> TransactionStatus.COMPLETE.toString,
              BOCart.column.lastUpdated -> DateTime.now
            ).where.eq(BOCart.column.id, boCart.id)
          }.update.apply()

          //update nbSales
          boCartItems.map { boCartItem =>
            val ticketType = TicketType.get(boCartItem.ticketTypeFk)
            productService.incrementSales(ticketType, boCartItem.quantity)
          }
        }
        val updatedCart = StoreCart(storeCode = cart.storeCode, dataUuid = cart.dataUuid, userUuid = cart.userUuid)
        StoreCartDao.save(updatedCart)
        emailingData
      case None => throw new IllegalArgumentException("Unabled to retrieve Cart " + cart.uuid + " into BO. It has not been initialized or has already been validated")
    }
  }

  /**
   * Annule la transaction courante et génère un nouveua uuid au panier
   * @param cart
   * @return
   */
  def cancel(cart: StoreCart): StoreCart = {
    BOCart.findByTransactionUuid(cart.uuid) match {
      case Some(boCart) =>
        DB localTx { implicit session =>
          // Mise à jour du statut et du transactionUUID
          withSQL {
            update(BOCart).set(
              BOCart.column.status -> TransactionStatus.FAILED.toString,
              BOCart.column.lastUpdated -> DateTime.now
            ).where.eq(BOCart.column.id, boCart.id)
          }.update.apply()
        }

        val updatedCart = cart.copy(inTransaction = false) //cartVO.uuid = null;
        StoreCartDao.save(updatedCart)
        updatedCart
      case None => throw new IllegalArgumentException("Unabled to retrieve Cart " + cart.uuid + " into BO. It has not been initialized or has already been validated")
    }
  }
}

object CustomTypes {
  type CartErrors = Map[String, Seq[Any]]

}

class CartException(val errors: CartErrors) extends Exception {

  val bundle = ResourceBundle("i18n.errors")

  def getErrors(locale: Locale): List[String] = {

    val res = for {
      (key, value) <- errors
    } yield {
      if (value == null)
        bundle.get(key, locale)
      else
        bundle.getWithParams(key, locale, value)
    }

    res.toList

  }
}

case class AddCartItemException(override val errors: CartErrors) extends CartException(errors) {
  override def toString = errors.toString()
}

case class UpdateCartItemException(override val errors: CartErrors) extends CartException(errors)

case class RemoveCartItemException(override val errors: CartErrors) extends CartException(errors)

case class ClearCartException(override val errors: CartErrors) extends CartException(errors)

case class AddCouponToCartException(override val errors: CartErrors) extends CartException(errors)

case class RemoveCouponFromCartException(override val errors: CartErrors) extends CartException(errors)

case class ValidateCartException(override val errors: CartErrors) extends CartException(errors)
