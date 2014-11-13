package com.mogobiz.run.cart

import java.io.ByteArrayOutputStream
import java.util.{Date, Locale}
import com.mogobiz.pay.model.Mogopay
import com.mogobiz.run
import com.mogobiz.run.config.{Settings, MogobizDBsWithEnv}
import com.mogobiz.run.model._
import com.mogobiz.run.cart.CustomTypes.CartErrors
import com.mogobiz.run.cart.ProductType.ProductType
import com.mogobiz.run.cart.ProductCalendar.ProductCalendar
import com.mogobiz.run.cart.WeightUnit.WeightUnit
import com.mogobiz.run.cart.LinearUnit.LinearUnit
import com.mogobiz.run.cart.transaction._
import com.mogobiz.run.cart.domain._
import com.mogobiz.run.utils.Utils
import com.mogobiz.utils._
import com.sun.org.apache.xml.internal.security.utils.Base64
import com.typesafe.scalalogging.slf4j.Logger
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.slf4j.LoggerFactory
import scalikejdbc.config.DBs
import scalikejdbc._

/**
 * The Cart Back office Service in charge of retrieving/storing data from database
 */
object CartBoService extends BoService {

  type CouponVO = Coupon

  MogobizDBsWithEnv(Settings.Dialect).setupAll()

  private val logger = Logger(LoggerFactory.getLogger("CartBoService"))

  val uuidService = UuidBoService
  val productService = ProductBoService
  val taxRateService = TaxRateBoService
  val couponService = CouponService

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
      val newCartItems = existCartItem.get.copy(quantity = existCartItem.get.quantity + cartItem.quantity) :: Utils.remove(cart.cartItems, existCartItem.get)
      cart.copy(cartItems = newCartItems)
    }
    else {
      val newCartItems = (cartItem :: cart.cartItems)
      cart.copy(cartItems = newCartItems)
    }
  }

  private def _findCoupon(cart: StoreCart, coupon: StoreCoupon) : Option[StoreCoupon] = {
    cart.coupons.find {c: StoreCoupon => c.code == coupon.code}
  }

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
   * Récupère le panier correspondant au uuid et au compte client.
   * La méthode gère le panier anonyme et le panier authentifié.
   * @param uuid
   * @param currentAccountId
   * @return
   */
  def initCart(uuid: String, currentAccountId: Option[String]): StoreCart = {
    def getOrCreateStoreCart(cart: Option[StoreCart]) : StoreCart = {
      cart match {
        case Some(c) => c
        case None =>
          val c = new StoreCart(uuid = uuid, userUuid = currentAccountId)
          uuidService.setCart(c)
          c
      }
    }

    if (currentAccountId.isDefined) {
      val cartAnonyme = uuidService.getCart(uuid, None);
      val cartAuthentifie = getOrCreateStoreCart(uuidService.getCart(uuid, currentAccountId));

      // S'il y a un panier anonyme, il est fusionné avec le panier authentifié et supprimé de la base
      if (cartAnonyme.isDefined) {
        uuidService.removeCart(cartAnonyme.get)
        _fusion(cartAnonyme.get, cartAuthentifie)
      }
      else cartAuthentifie
    }
    else {
      // Utilisateur anonyme
      getOrCreateStoreCart(uuidService.getCart(uuid, None));
    }
  }

  def cleanExpiredCart: Unit = {
    uuidService.getExpired.map { c =>
      incrementProductsStock(c)
      uuidService.removeCart(c)
    }
  }

  def addError(errors: CartErrors, key: String, msg: String, parameters: Seq[Any]): CartErrors = {
    errors + (key + "." + msg -> parameters)
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
  def addItem(cart: StoreCart, ticketTypeId: Long, quantity: Int, dateTime: Option[DateTime], registeredCartItems: List[RegisteredCartItemVO]): StoreCart = {
    logger.info(s"addItem dateTime : $dateTime")
    println("+++++++++++++++++++++++++++++++++++++++++")
    println(s"dateTime=$dateTime")
    println("+++++++++++++++++++++++++++++++++++++++++")

    // init local vars
    val ticketType: TicketType = TicketType.get(ticketTypeId) //TODO error management

    val product = ticketType.product.get
    val startEndDate = Utils.verifyAndExtractStartEndDate(Some(ticketType), dateTime)

    var errors: CartErrors = Map()

    if (ticketType.minOrder > quantity || (ticketType.maxOrder < quantity && ticketType.maxOrder > -1)) {
      println(ticketType.minOrder + ">" + quantity)
      println(ticketType.maxOrder + "<" + quantity)
      errors = addError(errors, "quantity", "min.max.error", List(ticketType.minOrder, ticketType.maxOrder))
    }

    if (!dateTime.isDefined && !ProductCalendar.NO_DATE.equals(product.calendarType)) {
      errors = addError(errors, "dateTime", "nullable.error", null)
    }
    else if (dateTime.isDefined && startEndDate ==(None, None)) {
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

    // decrement stock
    productService.decrementStock(ticketType, quantity, startEndDate._1)

    val newItemId = new Date().getTime.toString
    val registeredItems = registeredCartItems.map { item => item.copy(cartItemId = newItemId)}

    // shipping
    val shipping = product.shipping

    val cartItem = StoreCartItem(newItemId, product.id, product.name, product.xtype, product.calendarType, ticketType.id, ticketType.name, quantity,
      ticketType.price, startEndDate._1, startEndDate._2, registeredItems, shipping)

    val newcart = _addCartItemIntoCart(cart, cartItem)
    uuidService.setCart(newcart)
    newcart
  }

  /**
   * Met à jour l'item dans le panier avec la nouvelle quantité voulu
   * @param cart
   * @param cartItemId
   * @param quantity the new value wanted
   * @return
   */
  @throws[UpdateCartItemException]
  def updateItem(cart: StoreCart, cartItemId: String, quantity: Int): StoreCart = {
    val errors: CartErrors = Map()

    //    if (result.success) {
    //TODO ou faire un map et qd id == renvoyé l'item modifié
    val optCartItem = cart.cartItems.find { item => item.id == cartItemId}

    if (optCartItem.isDefined) {
      val existCartItem = optCartItem.get
      if (ProductType.SERVICE != existCartItem.xtype && existCartItem.quantity != quantity) {

        val sku = TicketType.get(existCartItem.skuId)

        if (sku.minOrder > quantity || (sku.maxOrder < quantity && sku.maxOrder > -1)) {
          addError(errors, "quantity", "min.max.error", List(sku.minOrder, sku.maxOrder))
          throw new UpdateCartItemException(errors)
        }

        // Gestion du stock
        if (existCartItem.quantity < quantity) {
          try {
            // On décrémente le stock
            productService.decrementStock(sku, quantity - existCartItem.quantity, existCartItem.startDate)
          }
          catch {
            case ex: InsufficientStockException =>
              addError(errors, "quantity", "stock.error", null)
              throw new UpdateCartItemException(errors)
          }
        }
        else {
          // On incrémente le stock
          productService.incrementStock(sku, existCartItem.quantity - quantity, existCartItem.startDate)
        }

        // Modification du panier
        val newCartItems = existCartItem.copy(quantity = quantity) :: Utils.remove(cart.cartItems, existCartItem)
        val updatedCart = cart.copy(cartItems = newCartItems)

        uuidService.setCart(updatedCart)
        updatedCart
      } else {
        println("silent op")
        cart
      }
    } else {
      addError(errors, "item", "item.notfound.error", null)
      throw new UpdateCartItemException(errors)
    }
  }

  @throws[RemoveCartItemException]
  def removeItem(cart: StoreCart, cartItemId: String): StoreCart = {
    val errors: CartErrors = Map()

    val optCartItem = cart.cartItems.find { item => item.id == cartItemId}
    if (optCartItem.isDefined) {
      val existCartItem = optCartItem.get

      val sku = TicketType.get(existCartItem.skuId)
      productService.incrementStock(sku, existCartItem.quantity, existCartItem.startDate)

      val newCartItems = Utils.remove(cart.cartItems, existCartItem)
      val updatedCart = cart.copy(cartItems = newCartItems)

      uuidService.setCart(updatedCart)
      updatedCart
    }
    else cart
  }

  private def incrementProductsStock(c: StoreCart): Unit = {
    c.cartItems.foreach { cartItem =>
      val sku = TicketType.get(cartItem.skuId)
      productService.incrementStock(sku, cartItem.quantity, cartItem.startDate)
    }
  }

  @throws[ClearCartException]
  def clear(cart: StoreCart): StoreCart = {
    val errors: CartErrors = Map()

    incrementProductsStock(cart)

    val updatedCart = new StoreCart(uuid = cart.uuid, userUuid = cart.userUuid)
    uuidService.setCart(updatedCart)
    updatedCart
  }

  @throws[AddCouponToCartException]
  def addCoupon(companyCode: String, couponCode: String, cart: StoreCart): StoreCart = {
    val errors: CartErrors = Map()

    val optCoupon = Coupon.findByCode(companyCode, couponCode)

    if (optCoupon.isDefined) {
      val coupon = optCoupon.get

      if (cart.coupons.exists { c => couponCode == c.code}) {
        addError(errors, "coupon", "already.exist", null)
        throw new AddCouponToCartException(errors)
      } else if (!CouponService.consumeCoupon(coupon)) {
        addError(errors, "coupon", "stock.error", null)
        throw new AddCouponToCartException(errors)
      }
      else {
        val newCoupon = StoreCoupon(id = coupon.id, code = coupon.code)

        val coupons = newCoupon :: cart.coupons
        val updatedCart = cart.copy(coupons = coupons)
        uuidService.setCart(updatedCart)
        updatedCart
      }
    }
    else {
      addError(errors, "coupon", "unknown.error", null)
      throw new AddCouponToCartException(errors)
    }
  }


  /**
   * Remove the coupon from the cart
   * @param companyCode
   * @param couponCode
   * @param cart
   * @return
   */
  @throws[RemoveCouponFromCartException]
  def removeCoupon(companyCode: String, couponCode: String, cart: StoreCart): StoreCart = {
    val errors: CartErrors = Map()

    val optCoupon = Coupon.findByCode(companyCode, couponCode)

    if (optCoupon.isDefined) {
      val existCoupon = cart.coupons.find { c => couponCode == c.code}
      if (existCoupon.isEmpty) {
        addError(errors, "coupon", "unknown.error", null)
        throw new RemoveCouponFromCartException(errors)
      }
      else {
        CouponService.releaseCoupon(optCoupon.get)

        // reprise des items existants sauf celui à supprimer
        val coupons = Utils.remove(cart.coupons, existCoupon.get)
        val updatedCart = cart.copy(coupons = coupons)
        uuidService.setCart(updatedCart)
        updatedCart
      }
    }
    else {
      addError(errors, "coupon", "unknown.error", null)
      throw new RemoveCouponFromCartException(errors)
    }
  }

  private def _computeCartItem(cartItems: List[StoreCartItem], countryCode: Option[String], result: (Long, Option[Long], List[CartItemVO])) : (Long, Option[Long], List[CartItemVO]) = {
    if (cartItems.isEmpty) result
    else {
      val cartItem = cartItems.head
      val product = Product.get(cartItem.productId).get
      val tax = taxRateService.findTaxRateByProduct(product, countryCode)
      val endPrice = taxRateService.calculateEndPrix(cartItem.price, tax)
      val totalPrice = cartItem.quantity * cartItem.price
      val totalEndPrice = if (endPrice.isDefined) Some(cartItem.quantity * endPrice.get) else None

      val newCartItem = CartItemVO(cartItem.id, cartItem.productId, cartItem.productName, cartItem.xtype, cartItem.calendarType,
        cartItem.skuId, cartItem.skuName, cartItem.quantity, cartItem.price, endPrice, tax, totalPrice, totalEndPrice,
        cartItem.startDate, cartItem.endDate, cartItem.registeredCartItems.toArray, cartItem.shipping)


      val newResultTotalEndPrice = if (result._2.isDefined && totalEndPrice.isDefined) Some(result._2.get + totalEndPrice.get)
      else if (result._2.isDefined) result._2
      else totalEndPrice
      _computeCartItem(cartItems.tail, countryCode, (result._1 + totalPrice, newResultTotalEndPrice, newCartItem :: result._3))
    }
  }

  def computeStoreCart(companyCode: String, cart: StoreCart, countryCode: Option[String], stateCode: Option[String]) : CartVO = {
    val priceEndPriceCartItems = _computeCartItem(cart.cartItems, countryCode, (0, None, List()))
    val price = priceEndPriceCartItems._1
    val endPrice = priceEndPriceCartItems._2
    val cartItems = priceEndPriceCartItems._3

    val reductionCoupons = couponService.computeCoupons(cart.coupons, cartItems, (0, List()))

    val promoAvailable = Coupon.findPromotionsThatOnlyApplyOnCart(companyCode)
    val reductionCouponsAndPromotions = couponService.computePromotions(promoAvailable, cartItems, reductionCoupons)

    val reduction = reductionCouponsAndPromotions._1
    val coupons = reductionCouponsAndPromotions._2

    val finalPrice = if (endPrice.isDefined) endPrice.get - reduction else price - reduction

    CartVO(price, endPrice, reduction, finalPrice, cartItems.length, cart.uuid, cart.userUuid,
      cartItems.toArray, coupons.toArray, cart.inTransaction)
  }

  def prepareBeforePayment(companyCode: String, countryCode: Option[String], stateCode: Option[String], rate: Currency, cart: StoreCart, buyer: String) = {
    val errors: CartErrors = Map()

    // récup du companyId à partir du storeCode
    val companyId = Company.findByCode(companyCode).get.id

    // Calcul des données du panier
    val cartTTC = computeStoreCart(companyCode, cart, countryCode, stateCode)

    //TRANSACTION 1 : SUPPRESSIONS
    DB localTx { implicit session =>

      if (cartTTC.inTransaction) {
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

    val updatedCart = cart.copy(inTransaction = true)
    val updatedCartTTC = cartTTC.copy(inTransaction = true)

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

    uuidService.setCart(updatedCart)

    val renderedCart = CartRenderService.renderTransactionCart(updatedCartTTC, rate)
    Map(
      "amount" -> renderedCart("finalPrice"),
      "currencyCode" -> rate.code,
      "currencyRate" -> rate.rate.doubleValue(),
      "transactionExtra" -> renderedCart
    )
  }

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
        val updatedCart = StoreCart(uuid = cart.uuid, userUuid = cart.userUuid)
        uuidService.setCart(updatedCart)
        emailingData
      case None => throw new IllegalArgumentException("Unabled to retrieve Cart " + cart.uuid + " into BO. It has not been initialized or has already been validated")
    }
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
    /*
  if (poi){
    strLocation = poi.road1?poi.road1 + ", ":""
    strLocation += poi.road2?poi.road2 + ", ":""
    strLocation += poi.city?poi.city + " ":""
    strLocation += poi.postalCode?poi.postalCode + " ":""
    strLocation += poi.state?poi.state + ". " :""
    strLocation += poi.countryCode?poi.countryCode+".":""
  }*/
  }

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
        uuidService.setCart(updatedCart)
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

case class CartVO(price: Long = 0, endPrice: Option[Long] = Some(0), reduction: Long = 0, finalPrice: Long = 0, count: Int = 0, uuid: String,
                  userUuid: Option[Mogopay.Document],
                  cartItemVOs: Array[CartItemVO] = Array(), coupons: Array[CouponVO] = Array(), inTransaction: Boolean = false)

object ProductType extends Enumeration {

  class ProductTypeType(s: String) extends Val(s)

  type ProductType = ProductTypeType
  val SERVICE = new ProductTypeType("SERVICE")
  val PRODUCT = new ProductTypeType("PRODUCT")
  val DOWNLOADABLE = new ProductTypeType("DOWNLOADABLE")
  val PACKAGE = new ProductTypeType("PACKAGE")
  val OTHER = new ProductTypeType("OTHER")

  def valueOf(str: String): ProductType = str match {
    case "SERVICE" => SERVICE
    case "PRODUCT" => PRODUCT
    case "DOWNLOADABLE" => DOWNLOADABLE
    case "PACKAGE" => PACKAGE
    case _ => OTHER
  }

}

object ProductCalendar extends Enumeration {

  class ProductCalendarType(s: String) extends Val(s)

  type ProductCalendar = ProductCalendarType
  val NO_DATE = new ProductCalendarType("NO_DATE")
  val DATE_ONLY = new ProductCalendarType("DATE_ONLY")
  val DATE_TIME = new ProductCalendarType("DATE_TIME")

  def valueOf(str: String): ProductCalendar = str match {
    case "DATE_ONLY" => DATE_ONLY
    case "DATE_TIME" => DATE_TIME
    case _ => NO_DATE
  }
}

case class RegisteredCartItemVO
(cartItemId: String = "", id: String, email: Option[String], firstname: Option[String] = None, lastname: Option[String] = None, phone: Option[String] = None, birthdate: Option[DateTime] = None)

case class CartItemVO
(id: String, productId: Long, productName: String, xtype: ProductType, calendarType: ProductCalendar, skuId: Long, skuName: String,
 quantity: Int, price: Long, endPrice: Option[Long], tax: Option[Float], totalPrice: Long, totalEndPrice: Option[Long],
 startDate: Option[DateTime], endDate: Option[DateTime], registeredCartItemVOs: Array[RegisteredCartItemVO], shipping: Option[ShippingVO])


object WeightUnit extends Enumeration {

  class WeightUnitType(s: String) extends Val(s)

  type WeightUnit = WeightUnitType
  val KG = new WeightUnitType("KG")
  val LB = new WeightUnitType("LB")
  val G = new WeightUnitType("G")

  def apply(str: String) = str match {
    case "KG" => KG
    case "LB" => LB
    case "G" => G
    case _ => throw new RuntimeException("unexpected WeightUnit value")
  }
}

object LinearUnit extends Enumeration {

  class LinearUnitType(s: String) extends Val(s)

  type LinearUnit = LinearUnitType
  val CM = new LinearUnitType("CM")
  val IN = new LinearUnitType("IN")

  def apply(str: String) = str match {
    case "CM" => CM
    case "IN" => IN
    case _ => throw new RuntimeException("unexpected LinearUnit value")
  }
}

case class ShippingVO
(id: Long, weight: Long, weightUnit: WeightUnit, width: Long, height: Long, depth: Long, linearUnit: LinearUnit, amount: Long, free: Boolean)

case class ReductionSold(id: Long, sold: Long = 0, dateCreated: DateTime = DateTime.now, lastUpdated: DateTime = DateTime.now
                         , override val uuid: String = java.util.UUID.randomUUID().toString) extends Entity with DateAware

//TODO find a way to avoid repeating the uuid here and compliant with ScalikeJDBC SQLSyntaxSupport

object ReductionSold extends SQLSyntaxSupport[ReductionSold] {

  def apply(rn: ResultName[ReductionSold])(rs: WrappedResultSet): ReductionSold = ReductionSold(
    id = rs.get(rn.id), sold = rs.get(rn.sold), dateCreated = rs.get(rn.dateCreated), lastUpdated = rs.get(rn.lastUpdated))

  def get(id: Long)(implicit session: DBSession): Option[ReductionSold] = {
    val c = ReductionSold.syntax("c")
    //DB readOnly { implicit session =>
    withSQL {
      select.from(ReductionSold as c).where.eq(c.id, id)
    }.map(ReductionSold(c.resultName)).single().apply()
    //}
  }

  /*
  def create = {
    withSQL {
    val newid = newId()
      val d = ReductionSold(newid)
      insert.into(ReductionSold).values(d.id,d.sold,d.dateCreated,d.lastUpdated )
    }.update.apply()
  }*/

}

case class CouponVO(id: Long, name: String, code: String, startDate: Option[DateTime] = None, endDate: Option[DateTime] = None, active: Boolean = false, price: Long = 0)

object CouponVO {
  def apply(coupon: Coupon): CouponVO = {
    CouponVO(id = coupon.id, name = coupon.name, code = coupon.code, startDate = coupon.startDate, endDate = coupon.endDate, active = coupon.active)
  }
}


object CouponService extends BoService {

  private val logger = Logger(LoggerFactory.getLogger("CouponService"))
  /*
  private def getOrCreate(coupon:Coupon) : ReductionSold = {
    def get(id:Long):Option[ReductionSold]={
      val c = Coupon.syntax("c")
      DB readOnly {
        implicit session =>
          withSQL {
            select.from(Coupon as c).where.eq(c.code, couponCode).and.eq(c.companyFk,companyId)
          }.map(Coupon(c.resultName)).single().apply()
      }
    }
  }*/

  /**
   * Verify if the coupon is available (from the point of view of the number of uses)
   * If the number of uses is verify, the sold of the coupon is increase
   * @param coupon
   * @return
   */
  def consumeCoupon(coupon: Coupon): Boolean = {
    DB localTx { implicit s =>

      val reducId = if (coupon.reductionSoldFk.isEmpty) {
        var id = 0
        withSQL {
          id = newId()
          val d = ReductionSold(id)
          val col = ReductionSold.column
          insert.into(ReductionSold).namedValues(col.id -> d.id, col.sold -> d.sold, col.dateCreated -> d.dateCreated, col.lastUpdated -> d.lastUpdated, col.uuid -> d.uuid)
        }.update.apply()

        val c = Coupon.column
        withSQL {
          update(Coupon).set(c.reductionSoldFk -> id, c.lastUpdated -> DateTime.now).where.eq(c.id, coupon.id)
        }.update.apply()
        id
      } else {
        coupon.reductionSoldFk.get
      }
      val reduc = ReductionSold.get(reducId).get
      val canConsume = coupon.numberOfUses match {
        case Some(nb) =>
          nb > reduc.sold
        case _ => true
      }

      if (canConsume) {
        val c = ReductionSold.column
        withSQL {
          update(ReductionSold).set(c.sold -> (reduc.sold + 1), c.lastUpdated -> DateTime.now).where.eq(c.id, reducId)
        }.update.apply()
      }
      canConsume
    }
  }

  def releaseCoupon(coupon: Coupon): Unit = {
    DB localTx { implicit s =>
      coupon.reductionSoldFk.foreach {
        reducId => {
          ReductionSold.get(reducId).foreach { reduc =>
            if (reduc.sold > 0) {
              val c = ReductionSold.column
              withSQL {
                update(ReductionSold).set(c.sold -> (reduc.sold - 1), c.lastUpdated -> DateTime.now).where.eq(c.id, reducId)
              }.update.apply()
            }
          }
        }
      }
    }
  }

  def getTicketTypesIdsWhereCouponApply(couponId: Long): List[Long] = DB readOnly {
    implicit session => {
      sql"""
         select id from ticket_type tt
         where tt.product_fk in(select product_id from coupon_product where products_fk=$couponId)
         or tt.product_fk in (select id from product where category_fk in (select category_id from coupon_category where categories_fk=$couponId))
       """.map(rs => rs.long("id")).list.apply()
    }
  }

  def computePromotions(coupons: List[Coupon], cartItems: List[CartItemVO], result: (Long, List[run.cart.CouponVO])): (Long, List[run.cart.CouponVO]) = {
    if (coupons.isEmpty) result
    else {
      val coupon = coupons.head

      val couponVO = _computeCoupon(coupon, cartItems)
      (result._1 + couponVO.price, couponVO :: result._2)
    }
  }

  def computeCoupons(coupons: List[StoreCoupon], cartItems: List[CartItemVO], result: (Long, List[run.cart.CouponVO])): (Long, List[run.cart.CouponVO]) = {
    if (coupons.isEmpty) result
    else {
      val c : StoreCoupon = coupons.head
      val coupon = Coupon.get(c.id).get

      val couponVO = _computeCoupon(coupon, cartItems)
      (result._1 + couponVO.price, couponVO :: result._2)
    }
  }

  private def _computeCoupon(coupon: Coupon, cartItems: List[CartItemVO]): run.cart.CouponVO = {
    if (_isCouponActive(coupon)) {
      val listTicketTypeIds = getTicketTypesIdsWhereCouponApply(coupon.id)

      if (listTicketTypeIds.size > 0) {
        // Méthode de calcul de la quantity et du prix à utiliser pour les règles
        def _searchReductionQuantityAndPrice(cartItems: List[CartItemVO], result : (Long, Long, Long)) : (Long, Long, Long) = {
          if (cartItems.isEmpty) if (result._2 == java.lang.Long.MAX_VALUE) (result._1, 0, result._3) else result
          else {
            val cartItem = cartItems.head
            if (listTicketTypeIds.contains(cartItem.skuId)) {
              val price = if (cartItem.endPrice.getOrElse(0l) > 0) Math.min(result._2, cartItem.endPrice.get) else result._2
              val totalPrice = result._3 + cartItem.totalEndPrice.getOrElse(0l)

              _searchReductionQuantityAndPrice(cartItems.tail, (result._1 + cartItem.quantity, price, totalPrice))
            }
            else _searchReductionQuantityAndPrice(cartItems.tail, result)
          }
        }

        val quantityAndPrice = _searchReductionQuantityAndPrice(cartItems, (0, java.lang.Long.MAX_VALUE, 0))
        if (quantityAndPrice._1 > 0) {
          // Calcul la sommes des réductions de chaque règle du coupon
          def _calculateReduction(rules: List[ReductionRule]) : Long = {
            if (rules.isEmpty) 0
            else _calculateReduction(rules.tail) + _applyReductionRule(rules.head, quantityAndPrice._1, quantityAndPrice._2, quantityAndPrice._3)
          }

          val couponPrice = _calculateReduction(coupon.rules)
          _createCouponVOFromCoupon(coupon, true, couponPrice)
        }
        else {
          // Par de quantité, cela signifie que la réduction ne s'applique pas donc prix = 0
          _createCouponVOFromCoupon(coupon, true, 0)
        }
      }
      else {
        // Le coupon ne s'applique sur aucun SKU, donc prix = 0
        _createCouponVOFromCoupon(coupon, true, 0)
      }
    }
    else {
      // Le coupon n'est pas actif, donc prix = 0 et active = false
      _createCouponVOFromCoupon(coupon, false, 0)
    }
  }


  private def _createCouponVOFromCoupon(coupon: Coupon, active: Boolean, price: Long) : CouponVO = {
    CouponVO(id = coupon.id, name = coupon.name, code = coupon.code, startDate = coupon.startDate, endDate = coupon.endDate, active = active, price = price)
  }

  private def _isCouponActive(coupon : Coupon) : Boolean = {
    if (coupon.startDate.isEmpty && coupon.endDate.isEmpty) true
    else if (coupon.startDate.isDefined && coupon.endDate.isEmpty) coupon.startDate.get.isBeforeNow || coupon.startDate.get.isEqualNow
    else if (coupon.startDate.isEmpty && coupon.endDate.isDefined) coupon.endDate.get.isAfterNow || coupon.endDate.get.isEqualNow
    else (coupon.startDate.get.isBeforeNow || coupon.startDate.get.isEqualNow) && (coupon.endDate.get.isAfterNow || coupon.endDate.get.isEqualNow)
  }

  /**
   * Applique la règle sur le montant total (DISCOUNT) ou sur le montant uniquement en fonction de la quantité (X_PURCHASED_Y_OFFERED)
   * @param rule : règle à appliquer
   * @param quantity : quantité
   * @param price : prix unitaire
   * @param totalPrice : prix total
   * @return
   */
  private def _applyReductionRule(rule: ReductionRule, quantity: Long, price: Long, totalPrice: Long) : Long = {
    rule.xtype match {
      case ReductionRuleType.DISCOUNT =>
        computeDiscount(rule.discount, totalPrice)
      case ReductionRuleType.X_PURCHASED_Y_OFFERED =>
        val multiple = quantity / rule.xPurchased.getOrElse(1L)
        price * rule.yOffered.getOrElse(1L) * multiple
      case _ => 0L
    }
  }

  /**
   * update the active attribute and calculate the reduction price
   * using the content of the cartVO and the current date (to define if coupon
   * is active or not)
   * @param c
   * @param cart
   */
  def updateCoupon(c: CouponVO, cart: CartVO): CouponVO = {
    logger.info(s"updateCoupon1 couponId=${c.id}")
    val coupon = Coupon.get(c.id).get
    logger.info(s"updateCoupon2: $coupon")

    val couponVO = CouponVO(coupon)
    logger.info(s"updateCoupon3: $couponVO")

    logger.info(s"updateCoupon: (${coupon.active} ${coupon.startDate} ${coupon.endDate})")

    //the coupon must be active with no date or with valid date between startDate and endDate
    if ((coupon.startDate.isEmpty && coupon.endDate.isEmpty) ||
      ((coupon.startDate.get.isBeforeNow || coupon.startDate.get.isEqualNow) && (coupon.endDate.get.isAfterNow || coupon.endDate.get.isEqualNow))) {
      val listTicketTypeIds = getTicketTypesIdsWhereCouponApply(c.id)
      logger.info(s"updateCoupon(${c.id}) listTicketTypeIds:$listTicketTypeIds")

      if (listTicketTypeIds.size > 0) {
        var quantity = 0l
        var xPurchasedPrice = java.lang.Long.MAX_VALUE
        cart.cartItemVOs.foreach { cartItem =>
          if (listTicketTypeIds.contains(cartItem.skuId)) {
            quantity += cartItem.quantity
            if (cartItem.endPrice.getOrElse(0l) > 0) {
              xPurchasedPrice = Math.min(xPurchasedPrice, cartItem.endPrice.get)
              logger.info(s"updateCoupon(${c.id}) xPurchasedPrice:$xPurchasedPrice")
            }
          }
        }
        if (xPurchasedPrice == java.lang.Long.MAX_VALUE) {
          xPurchasedPrice = 0
        }

        if (quantity > 0) {
          var couponVOprice = couponVO.price
          logger.info(s"updateCoupon(${c.id}) initialPrice:$couponVOprice")
          val rules = coupon.rules
          logger.info(s"updateCoupon(${c.id}) rules:$rules")
          rules.foreach {
            //TODO foldLeft later
            rule => {
              logger.info(s"updateCoupon(${c.id}) rule.xtype:${rule.xtype}")

              couponVOprice = rule.xtype match {
                case ReductionRuleType.DISCOUNT =>
                  cart.endPrice match {
                    case Some(endprice) => couponVOprice + computeDiscount(rule.discount, endprice)
                    case None => couponVOprice + computeDiscount(rule.discount, cart.price)
                  }
                case ReductionRuleType.X_PURCHASED_Y_OFFERED =>
                  val multiple = quantity / rule.xPurchased.get //FIXME None.get possible
                  couponVO.price + (xPurchasedPrice * rule.yOffered.get * multiple) //FIXME None.get possible
                case _ => couponVOprice
              }
              logger.info(s"updateCoupon(${c.id}) couponVOprice:$couponVOprice")
              couponVOprice
            }
          }
          logger.info(s"updateCoupon(${c.id}) finalPrice:$couponVOprice")
          couponVO.copy(active = true, price = couponVOprice)

        } else {
          couponVO
        }
      } else {
        couponVO
      }
    } else {
      couponVO
    }
  }


  def getPromotions(cart: CartVO, companyCode: String): List[CouponVO] = {
    val promoAvailable = Coupon.findPromotionsThatOnlyApplyOnCart(companyCode)
    logger.info("getPromotions: " + promoAvailable.size)

    //convert to CouponVO in order to be used by updateCoupon
    //not very effcient but that will do for the time being
    val couponsVO = promoAvailable.map(CouponVO(_))
    val updatedPromo = couponsVO.map(updateCoupon(_, cart))

    updatedPromo
  }

  /**
   *
   * @param discountRule
   * @param prixDeBase
   * @return
   */
  //TODO translate variable label
  def computeDiscount(discountRule: Option[String], prixDeBase: Long) : Long = {

    discountRule match {
      case Some(regle) => {
        if (regle.endsWith("%")) {
          val pourcentage = java.lang.Float.parseFloat(regle.substring(0, regle.length() - 1))
          (prixDeBase * pourcentage / 100).toLong //TODO recheck the rounded value computed
        }
        else if (regle.startsWith("+")) {

          val increment = java.lang.Long.parseLong(regle.substring(1))
          prixDeBase + increment
        }
        else if (regle.startsWith("-")) {
          val decrement = java.lang.Long.parseLong(regle.substring(1))
          prixDeBase - decrement
        }
        else {
          java.lang.Long.parseLong(regle)
        }
      }
      case None => prixDeBase
    }
  }
}


