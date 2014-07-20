package com.mogobiz.cart

import java.io.ByteArrayOutputStream
import java.util.{Date, Locale}
import com.mogobiz.cart.ProductType.ProductType
import com.mogobiz.cart.ProductCalendar.ProductCalendar
import com.mogobiz.cart.WeightUnit.WeightUnit
import com.mogobiz.cart.LinearUnit.LinearUnit
import com.mogobiz.utils.{Utils, QRCodeUtils, SecureCodec}
import com.sun.org.apache.xml.internal.security.utils.Base64
import com.typesafe.scalalogging.slf4j.Logger
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import org.json4s.{DefaultFormats, Formats}
import com.mogobiz.{RateBoService, Currency}
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import scalikejdbc.config.DBs
import com.mogobiz.cart.TransactionStatus.TransactionStatus
import scalikejdbc.SQLInterpolation._
import scala.Some
import scalikejdbc.{DBSession, DB, WrappedResultSet}
import com.mogobiz.cart.ReductionRuleType.ReductionRuleType
import org.json4s.native.Serialization._
import scala.Some
import scalikejdbc.WrappedResultSet

/**
 * Created by Christophe on 05/05/2014.
 * The Cart Back office Service in charge of retrieving/storing data from database
 */
object CartBoService extends BoService {

  type CouponVO = Coupon

  DBs.setupAll()

  private val logger = Logger(LoggerFactory.getLogger("CartBoService"))

  val uuidService = UuidBoService
  val productService = ProductBoService
  val taxRateService = TaxRateBoService

  def initCart(uuid:String): CartVO = {

    val cartVO = uuidService.getCart(uuid)
    cartVO match {
      case Some(c) => c
      case None => {
        val c = CartVO(uuid = uuid)
        uuidService.setCart(c)
        c
      }
    }
  }

  def addError(errors:Map[String,String],key:String,msg:String,parameters:List[Any],locale:Locale):Map[String,String]={
    //TODO translate msg
    errors+(key -> msg)
  }

  /**
   *
   * @param locale
   * @param currencyCode
   * @param cartVO
   * @param ticketTypeId
   * @param quantity
   * @param dateTime
   * @param registeredCartItems
   * @return send back the new cart with the added item
   */
  @throws[AddCartItemException]
  def addItem(locale:Locale , currencyCode:String , cartVO:CartVO , ticketTypeId:Long, quantity:Int, dateTime:Option[DateTime], registeredCartItems:List[RegisteredCartItemVO]):CartVO  = {

    assert(!currencyCode.isEmpty,"currencyCode should not be empty")
    assert(!locale.getCountry.isEmpty,"locale.getCountry should not be empty")

    logger.info(s"addItem dateTime : ${dateTime}")
    println("+++++++++++++++++++++++++++++++++++++++++")
    println(s"dateTime=${dateTime}")
    println("+++++++++++++++++++++++++++++++++++++++++")

    // init local vars
    val ticketType:TicketType = TicketType.get(ticketTypeId) //TODO error management

    val product = ticketType.product.get;
    val startEndDate = Utils.verifyAndExtractStartEndDate(Some(ticketType), dateTime);

    var errors:Map[String,String] = Map()

    if(cartVO.inTransaction){ //if(cartVO.uuid){
      errors = addError(errors, "cart", "initiate.payment.error", null, locale)
    }

    if (ticketType.minOrder > quantity || (ticketType.maxOrder < quantity && ticketType.maxOrder > -1))
    {
      println(ticketType.minOrder+">"+quantity)
      println(ticketType.maxOrder+"<"+quantity)
      errors = addError(errors, "quantity", "min.max.error", List(ticketType.minOrder, ticketType.maxOrder), locale)
    }

    if (!dateTime.isDefined && !ProductCalendar.NO_DATE.equals(product.calendarType))
    {
      errors = addError(errors, "dateTime", "nullable.error", null, locale)
    }
    else if (dateTime.isDefined && startEndDate == (None,None))
    {
      errors = addError(errors, "dateTime", "unsaleable.error", null, locale)
    }
    if (product.xtype == ProductType.SERVICE) {
      if (registeredCartItems.size != quantity) {
        errors = addError(errors, "registeredCartItems", "size.error", null, locale)
      }
      else {
        val emptyMails = for {
          item <- registeredCartItems
          if (item.email.isEmpty)
        }yield item
        if(!emptyMails.isEmpty)
          errors = addError(errors, "registeredCartItems", "email.error", null, locale)
      }
    }

    if(errors.size>0)
      throw new AddCartItemException(errors)

    // decrement stock
    productService.decrement(ticketType,quantity,startEndDate._1)

    //resume existing items
    var oldCartPrice = 0l;
    var oldCartEndPrice: Option[Long] = Some(0l);
    cartVO.cartItemVOs.foreach{
      item => {
        (oldCartEndPrice,item.totalEndPrice) match{
          case (Some(o),Some(t)) => {
            oldCartEndPrice = Some(o+t)
          }
          case _ => oldCartEndPrice = None
        }
        oldCartPrice += item.totalPrice
      }
    }

    // value cartItem
    val itemPrice = ticketType.price
    val tax = taxRateService.findTaxRateByProduct(product, locale.getCountry) //TODO revoir la validité de ce dernier paramètre par rapports aux appels
    val endPrice = taxRateService.calculateEndPrix(itemPrice, tax)
    val totalPrice = quantity * itemPrice;
    val totalEndPrice = endPrice match {
      case Some(p) => Some(quantity * endPrice.get)
      case _ => None
    }

    val newItemId = (new Date()).getTime.toString
    val registeredItems = registeredCartItems.map{
      item => new RegisteredCartItemVO(newItemId, item.id,item.email,item.firstname,item.lastname,item.phone,item.birthdate)
    }

    // shipping
    val shipping = product.shipping

    val item = CartItemVO(newItemId,product.id,product.name,product.xtype,product.calendarType,ticketType.id,ticketType.name,quantity,
      itemPrice,endPrice,tax,totalPrice,totalEndPrice,startEndDate._1,startEndDate._2,registeredItems.toArray,shipping)

    //val items = cartVO.cartItemVOs:+item //WARNING not optimal
    val items = item::cartVO.cartItemVOs.toList

    val newEndPrice = (oldCartEndPrice,item.totalEndPrice) match {
      case (Some(o),Some(t))=> Some(o+t)
      case _ => None
    }
    val newcart = cartVO.copy(
        price = oldCartPrice + item.totalPrice,//(cartVO.price + item.totalPrice),
        endPrice = newEndPrice,
        count = items.size,
        cartItemVOs = items.toArray)

    uuidService.setCart(newcart)
    newcart
  }

  /**
   * Met à jour l'item dans le panier avec la nouvelle quantité voulu
   * @param locale
   * @param currencyCode
   * @param cartVO
   * @param cartItemId
   * @param quantity the new value wanted
   * @return
   */
  @throws[UpdateCartItemException]
  def updateItem(locale:Locale, currencyCode:String, cartVO:CartVO , cartItemId:String , quantity:Int ) : CartVO = {

    val errors:Map[String,String] = Map()

    if(cartVO.inTransaction){ //if (cartVO.uuid) {
      // un paiement a été initialisé, on ne peut plus modifier le contenu du panier avant la fin du paiement (ou l'abandon)
      addError(errors, "cart", "initiate.payment.error", null, locale)
      throw new UpdateCartItemException(errors)
    }

//    if (result.success) {
    //TODO ou faire un map et qd id == renvoyé l'item modifié
    val optCartItem = cartVO.cartItemVOs.find{ item => item.id == cartItemId }

    if(optCartItem .isDefined) {
      val cartItem = optCartItem.get
      if(ProductType.SERVICE != cartItem.xtype && cartItem.quantity != quantity) {

        val sku = TicketType.get(cartItem.skuId)

        if (sku.minOrder > quantity || (sku.maxOrder < quantity && sku.maxOrder > -1))
        {
          addError(errors, "quantity", "min.max.error", List(sku.minOrder, sku.maxOrder), locale)
          throw new UpdateCartItemException(errors)
        }

        val oldQuantity = cartItem.quantity;
        val oldTotalPrice = cartItem.totalPrice
        val oldTotalEndPrice = cartItem.totalEndPrice
        if (oldQuantity < quantity) {
          try
          {
            // On décrémente le stock
            productService.decrement(sku, quantity - oldQuantity, cartItem.startDate)
          }
          catch {
            case ex:InsufficientStockException =>
              {
                addError(errors, "quantity", "stock.error", null, locale)
                throw new UpdateCartItemException(errors)
              }
          }
        }
        else
        {
          // On incrémente le stock
          productService.increment(sku, oldQuantity - quantity, cartItem.startDate)
        }

         val item = cartItem
        /*
         val updatedItem = CartItemVO(
           id=item.id,productId=item.productId,productName = item.productName, xtype = item.xtype, calendarType = item.calendarType, skuId = item.skuId, skuName = item.skuName,
           quantity = quantity, price = item.price, endPrice = item.endPrice,tax = item.tax, totalPrice = (quantity * item.price),
           totalEndPrice = if(item.totalEndPrice.isDefined && item.endPrice.isDefined){Some(quantity * item.endPrice.get)}else{None},
           startDate = item.startDate, endDate = item.endDate, registeredCartItemVOs = item.registeredCartItemVOs,shipping = item.shipping
         )*/
        val newTotalEndPrice = item.endPrice match {
          case Some(endPrice) => Some(endPrice * quantity)
          case None => None
        }
        val updatedItem = item.copy(
          quantity = quantity,
          totalPrice = (quantity * item.price),
          totalEndPrice = newTotalEndPrice)


        /* iper
        if (oldTotalEndPrice != null && cartVO.endPrice != null && cartItem.endPrice != null) {
          cartItem.totalEndPrice = quantity * cartItem.endPrice;
          cartVO.endPrice = cartVO.endPrice - oldTotalEndPrice + cartItem.totalEndPrice;
        }*/
        val updatedItems = cartVO.cartItemVOs.map{ it => if(it.id==updatedItem.id) updatedItem else it}

        /*
        val updatedCart = CartVO(
          price = (cartVO.price - oldTotalPrice + cartItem.totalPrice),
          endPrice = if(item.totalEndPrice.isDefined && oldTotalEndPrice.isDefined){ cartVO.endPrice - oldTotalEndPrice.get + item.totalEndPrice.get}else{cartVO.endPrice},
          reduction = cartVO.reduction,finalPrice=cartVO.finalPrice,count=cartVO.count,uuid=cartVO.uuid, cartItemVOs = updatedItems,coupons = cartVO.coupons )
        */
        val newEndPrice = (cartVO.endPrice,oldTotalEndPrice,newTotalEndPrice) match{
          case (Some(ep),Some(otep),Some(ntep)) => Some(ep - otep + ntep)
          case _ => None
        }

        val updatedCart = cartVO.copy(
          price = (cartVO.price - oldTotalPrice + updatedItem.totalPrice),
          endPrice = newEndPrice,
          cartItemVOs = updatedItems
        )

        uuidService.setCart(updatedCart)
        //cartVO.cartItemVOs.foreach(println)
        updatedCart
      }else{
        /*
        addError(errors, "product", "item.notfound.error", null, locale)
        throw new UpdateCartItemException(errors)
        */
        println("silent op")
        cartVO
      }
    }else{
      addError(errors, "item", "item.notfound.error", null, locale)
      throw new UpdateCartItemException(errors)
    }

    //renvoie le panier tel que ???
    //cartVO
  }

  @throws[RemoveCartItemException]
  def removeItem(locale:Locale , currencyCode:String , cartVO:CartVO , cartItemId:String ) : CartVO = {

    val errors:Map[String,String] = Map()

    if(cartVO.inTransaction){ //if (cartVO.uuid) {
      // un paiement a été initialisé, on ne peut plus modifier le contenu du panier avant la fin du paiement (ou l'abandon)
      addError(errors, "cart", "initiate.payment.error", null, locale)
      throw new RemoveCartItemException(errors)
    }

    val parts = cartVO.cartItemVOs.partition { cartItem  => cartItem.id == cartItemId }
    val removed = parts._1.headOption
    val items = parts._2


    val cartItem = removed.get //FIXME manage None.get

    val sku = TicketType.get(cartItem.skuId)

    productService.increment(sku, cartItem.quantity, cartItem.startDate)

    val newEndPrice:Option[Long] = (cartVO.endPrice,cartItem.totalEndPrice) match {
      case (Some(cartendprice),Some(itemtotalendprice)) => Some(cartendprice - itemtotalendprice)
      case _ => None
    }
    val updatedCart = cartVO.copy(cartItemVOs = items, price = (cartVO.price - cartItem.totalPrice), endPrice = newEndPrice, count = (cartVO.count - 1))
    uuidService.setCart(updatedCart);

    updatedCart
  }

  @throws[ClearCartException]
  def clear(locale:Locale, currencyCode:String, cartVO:CartVO ) : CartVO = {
    val errors:Map[String,String] = Map()

    if(cartVO.inTransaction){ //if (cartVO.uuid) {
      // un paiement a été initialisé, on ne peut plus modifier le contenu du panier avant la fin du paiement (ou l'abandon)
      addError(errors, "cart", "initiate.payment.error", null, locale)
      throw new ClearCartException(errors)
    }


      cartVO.cartItemVOs.foreach { cartItem =>
        val sku = TicketType.get(cartItem.skuId)
        productService.increment(sku, cartItem.quantity, cartItem.startDate)
      }
      //TODO ??? uuidDataService.removeCart(); not implemented in iper

    val updatedCart = new CartVO(uuid=cartVO.uuid)
    uuidService.setCart(updatedCart);
    updatedCart
  }

  @throws[AddCouponToCartException]
  def addCoupon(companyCode:String, couponCode:String, cartVO:CartVO, locale:Locale, currencyCode:String) : CartVO = {
    val errors:Map[String,String] = Map()

    val optCoupon = Coupon.findByCode(companyCode, couponCode)

    val updatedCart = optCoupon match {
      case Some(coupon) => {
        if(cartVO.coupons.exists{ c => couponCode == c.code }){
          addError(errors, "coupon", "already.exist", null, locale)
          throw new AddCouponToCartException(errors)
        }else if (!CouponService.consumeCoupon(coupon)) {
          addError(errors, "coupon", "stock.error", null, locale)
          throw new AddCouponToCartException(errors)
        }
        else {

          val coupons = CouponVO(coupon)::(cartVO.coupons.toList)
          val cart = cartVO.copy( coupons = coupons.toArray)
          uuidService.setCart(cart);
          cart
        }
      }
      case None => {
        addError(errors, "coupon", "unknown.error", null, locale)
        throw new AddCouponToCartException(errors)
      }
    }

    updatedCart
  }


  /**
   * Remove the coupon from the cart
   * @param companyCode
   * @param locale
   * @param currencyCode
   * @param cartVO
   * @param couponCode
   * @return
   */
  @throws[RemoveCouponFromCartException]
  def removeCoupon(companyCode:String, couponCode:String, cartVO:CartVO,locale:Locale, currencyCode: String ):CartVO = {
    val errors:Map[String,String] = Map()

    val optCoupon = Coupon.findByCode(companyCode, couponCode)
    val updatedCart = optCoupon match {
      case None => {
        addError(errors, "coupon", "unknown.error", null, locale)
        throw new RemoveCouponFromCartException(errors)
      }
      case Some(coupon) => {
        if(!cartVO.coupons.exists{ c => couponCode == c.code }){
          addError(errors, "coupon", "unknown.error", null, locale)
          throw new RemoveCouponFromCartException(errors)
        }else {
          CouponService.releaseCoupon(coupon)

          // reprise des items existants sauf celui à supprimer
          val coupons = cartVO.coupons.filter{ c => couponCode != c.code }
          val cart = cartVO.copy( coupons = coupons.toArray)
          uuidService.setCart(cart);
          cart
        }
      }
    }

    updatedCart

  }

  /**
   * Calcul des montants TTC
   * @param cartVO
   * @param countryCode
   * @param stateCode
   * @return
   */
  private def calculAmountAllTaxIncluded(cartVO: CartVO,countryCode:String, stateCode:Option[String]) : CartVO = {
    assert(!countryCode.isEmpty)

    var newEndPrice = 0l
    val newCartItemVOs = cartVO.cartItemVOs.map { cartItem =>
      val product = Product.get(cartItem.productId).get

      val tax = taxRateService.findTaxRateByProduct(product, countryCode, stateCode)
      val endPrice = taxRateService.calculateEndPrix(cartItem.price, tax)
      val totalEndPrice = endPrice match {
        case Some(p) => Some(cartItem.quantity * p)
        case _ => None
      }
      newEndPrice = newEndPrice + totalEndPrice.getOrElse(0l)

      cartItem.copy(endPrice = endPrice, totalEndPrice = totalEndPrice, tax = tax)
    }

    cartVO.copy(endPrice = Some(newEndPrice), cartItemVOs = newCartItemVOs)
  }

  def prepareBeforePayment(companyCode:String, countryCode:String, stateCode:Option[String], currencyCode:String, cartVO:CartVO,rate:Currency) = { //:Map[String,Any]=

    assert(!companyCode.isEmpty)
    assert(!countryCode.isEmpty)
    assert(!currencyCode.isEmpty)

    val errors:Map[String,String] = Map()

    // récup du companyId à partir du storeCode
    val companyId = Company.findByCode(companyCode).get.id

    // Calcul des montants TTC
    val cartTTC = calculAmountAllTaxIncluded(cartVO,countryCode, stateCode)

    //TRANSACTION 1 : SUPPRESSIONS
    DB localTx { implicit session =>

      if (cartTTC.inTransaction) {
        //if (cartTTC.uuid) {
        // On supprime tout ce qui concerne l'ancien BOCart (s'il est en attente)
        val boCart = BOCart.findByTransactionUuidAndStatus(cartTTC.uuid, TransactionStatus.PENDING)
        if (boCart.isDefined) {

        BOCartItem.findByBOCart(boCart.get).foreach { boCartItem =>
          BOCartItem.bOProducts(boCartItem).foreach { boProduct =>

            //b_o_cart_item_b_o_product (b_o_products_fk,boproduct_id) values(${saleId},${boProductId})
            sql"delete from b_o_cart_item_b_o_product where boproduct_id=${boProduct.id}".update.apply()

            //Product product = boProduct.product;
            BOTicketType.findByBOProduct(boProduct.id).foreach {  boTicketType =>
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

//    if (result.success) {
      //cartTTC.uuid = UUID.randomUUID();

    val updatedCart = cartTTC.copy(inTransaction = true)

    //TRANSACTION 2 : INSERTIONS
    DB localTx { implicit session =>

      var boCartId = 0
      withSQL {
        // =boCart.save()
        val newid = newId()
        boCartId = newid
        val b = BOCart.column
        insert.into(BOCart).namedValues(
          b.id -> boCartId,
          b.buyer -> "christophe.galant@ebiznext.com", //FIXME
          b.companyFk -> companyId,
          b.currencyCode -> currencyCode,
          b.currencyRate -> rate.rate,
          b.date -> DateTime.now,
          b.dateCreated -> DateTime.now,
          b.lastUpdated -> DateTime.now,
          b.price -> cartTTC.price,
          b.status -> TransactionStatus.PENDING.toString,
          b.transactionUuid -> cartTTC.uuid
        )
      }.update.apply()

      val boCart = new BOCart(
        id = boCartId,
        buyer = "christophe.galant@ebiznext.com", //FIXME
        transactionUuid = cartTTC.uuid,
        date = DateTime.now,
        price = cartTTC.price,
        status = TransactionStatus.PENDING,
        currencyCode = currencyCode,
        currencyRate = rate.rate,
        companyFk = companyId
      )


      cartTTC.cartItemVOs.foreach { cartItem => {
        val ticketType = TicketType.get(cartItem.skuId)

        // Création du BOProduct correspondant au produit principal
        var boProductId = 0
        withSQL {
          val boProduct = new BOProduct(id = boProductId, principal = true, productFk = cartItem.productId, price = cartItem.totalEndPrice.getOrElse(-1))
          //=boProduct.save()
          val newid = newId()
          boProductId = newid
          //acquittement:Boolean=false,price:Long=0,principal:Boolean=false,productFk:Long,dateCreated:DateTime = DateTime.now,lastUpdate:DateTime = DateTime.now
          val b = BOProduct.column
          insert.into(BOProduct).namedValues(
            b.id -> newid,
            b.acquittement -> boProduct.acquittement,
            b.price -> boProduct.price,
            b.principal -> boProduct.principal,
            b.productFk -> boProduct.productFk,
            b.dateCreated -> boProduct.dateCreated,
            b.lastUpdated -> boProduct.lastUpdated)
        }.update.apply()


        cartItem.registeredCartItemVOs.foreach {
          registeredCartItem => {
            // Création des BOTicketType (SKU)
            var boTicketId = 0
            val boTicketTmp = new BOTicketType(id = boTicketId,
              quantity = 1, price = cartItem.totalEndPrice.getOrElse(-1),shortCode = None,
              ticketType = Some(ticketType.name), firstname = registeredCartItem.firstname,
              lastname = registeredCartItem.lastname, email = registeredCartItem.email,
              phone = registeredCartItem.phone, age = 0,
              birthdate = registeredCartItem.birthdate, startDate = cartItem.startDate, endDate = cartItem.endDate,
              dateCreated = DateTime.now, lastUpdated = DateTime.now,
              //bOProduct = boProduct
              bOProductFk = boProductId
            )

            withSQL {
              //=boTicket.save()
              val b = BOTicketType.column
              val newid = newId()
              boTicketId = newid

              //génération du qr code uniquement pour les services
              val product = Product.get(cartItem.productId).get

              val boTicket = product.xtype match{
                case ProductType.SERVICE => {
                  val startDateStr = cartItem.startDate.map(d => d.toString(DateTimeFormat.forPattern("dd/MM/yyyy HH:mm")))

                  val shortCode = "P" + boProductId + "T" + boTicketId
                  val qrCodeContent = "EventId:" + product.id + ";BoProductId:" + boProductId + ";BoTicketId:" + boTicketId +
                    ";EventName:" + product.name + ";EventDate:" + startDateStr + ";FirstName:" +
                    boTicketTmp.firstname + ";LastName:" + boTicketTmp.lastname + ";Phone:" + boTicketTmp.phone +
                    ";TicketType:" + boTicketTmp.ticketType + ";shortCode:" + shortCode

                  val encryptedQrCodeContent = SecureCodec.encrypt(qrCodeContent, product.company.get.aesPassword);
                  val output = new ByteArrayOutputStream()
                  QRCodeUtils.createQrCode(output, encryptedQrCodeContent, 256, "png")
                  val qrCodeBase64 = Base64.encode(output.toByteArray())

                  boTicketTmp.copy(id = boTicketId, shortCode = Some(shortCode), qrcode = Some(qrCodeBase64), qrcodeContent = Some(encryptedQrCodeContent))
                }
                case _ => boTicketTmp.copy(id=boTicketId)
              }

              insert.into(BOTicketType).namedValues(
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
        val sale = new BOCartItem(id = saleId,
          code = "SALE_" + boCart.id + "_" + boProductId,
          price = cartItem.price,
          tax = cartItem.tax.get,
          endPrice = cartItem.endPrice.get,
          totalPrice = cartItem.totalPrice,
          totalEndPrice = cartItem.totalEndPrice.get,
          hidden = false,
          quantity = cartItem.quantity,
          startDate = ticketType.startDate, //product.startDate
          endDate = ticketType.stopDate, //product.stopDate
          bOCartFk = boCart.id
          //bOCart = boCart
          //bOProducts = [boProduct]
        )
        withSQL {
          // = sale.save()
          val b = BOCartItem.column
          val newid = newId()
          saleId = newid
          insert.into(BOCartItem).namedValues(
            b.id -> saleId,
            b.code -> ("SALE_" + boCart.id + "_" + boProductId),
            b.price -> cartItem.price,
            b.tax -> cartItem.tax.get,
            b.endPrice -> cartItem.endPrice.get,
            b.totalPrice -> cartItem.totalPrice,
            b.totalEndPrice -> cartItem.totalEndPrice.get,
            b.hidden -> false,
            b.quantity -> cartItem.quantity,
            b.startDate -> ticketType.startDate, //product.startDate
            b.endDate -> ticketType.stopDate, //product.stopDate
            b.bOCartFk -> boCart.id,
            b.dateCreated -> DateTime.now, b.lastUpdated -> DateTime.now
          )
        }.update.apply()

        sql"insert into b_o_cart_item_b_o_product(b_o_products_fk,boproduct_id) values(${saleId},${boProductId})".update.apply()
      }
      }
    }

    uuidService.setCart(updatedCart)

    val renderedCart = CartRenderService.renderTransactionCart(updatedCart,rate)
    /*
        implicit def json4sFormats: Formats = DefaultFormats
        import org.json4s.native.JsonMethods._
        import org.json4s.JsonDSL._


        println("-----------------------------------------------------------------------------------------------")
        val prettyJsonCart = pretty(render(write(renderedCart)))
        println(prettyJsonCart)
        println("-----------------------------------------------------------------------------------------------")
        */
    val data = Map(
      "amount" ->  renderedCart("finalPrice"), //RateBoService.calculateAmount(updatedCart.finalPrice, rate),
      "currencyCode" -> currencyCode,
      "currencyRate" -> rate.rate.doubleValue(),
      "transactionExtra" ->  renderedCart
    )
    data
  }

  def commit(cartVO:CartVO, transactionUuid:String ):List[Map[String,Any]]=
  {

    assert(!transactionUuid.isEmpty,"transactionUuid should not be empty")

    BOCart.findByTransactionUuid(cartVO.uuid) match {
      case Some(boCart) => {

        val emailingData = BOCartItem.findByBOCart(boCart).map { boCartItem =>
          BOCartItem.bOProducts(boCartItem).map { boProduct =>
            val product = boProduct.product
            BOTicketType.findByBOProduct(boProduct.id).map {  boTicketType =>
              var map :Map[String,Any]= Map()
              map+=("email" -> boTicketType.email)
              map+=("eventName" -> product.name)
              map+=("startDate" -> (boTicketType.startDate))
              map+=("stopDate" -> (boTicketType.endDate))
              map+=("location" -> toEventLocationVO(product.poi))
              map+=("type" -> boTicketType.ticketType)
              map+=("price" ->boTicketType.price)
              map+=("qrcode" -> boTicketType.qrcodeContent)
              map+=("shortCode" -> boTicketType.shortCode)

              map
            }
          }.flatten
        }.flatten

        DB localTx { implicit session =>
          // Mise à jour du statut et du transactionUUID
          /* code iper
        boCart.transactionUuid = transactionUuid;
        boCart.status = TransactionStatus.COMPLETE;
        boCart.save()
        */
          withSQL {
            update(BOCart).set(
              BOCart.column.transactionUuid -> transactionUuid,
              BOCart.column.status -> TransactionStatus.COMPLETE.toString,
              BOCart.column.lastUpdated -> DateTime.now
            ).where.eq(BOCart.column.id, boCart.id)
          }.update.apply()

          /* code iper
        cartVO.uuid = null;
        cartVO.price = 0
        cartVO.endPrice = 0
        cartVO.count = 0;
        cartVO.cartItemVOs = null
        //TODO uuidDataService.removeCart(); PAS IMPLEMENTE DANS IPER
        */
        }
        //code traduit en : val updatedCart = cartVO.copy(inTransaction = false, price = 0, endPrice = Some(0),count = 0, cartItemVOs = Array())
        //TODO a confirmer, mais je pense que c'est un reset complet du panier qu'on veut, comme suit :
        val updatedCart = CartVO(uuid = cartVO.uuid)
        uuidService.setCart(updatedCart)
        //TODO sendEmails(emailingData) faire un Actor
        emailingData
      }
      case None => throw new IllegalArgumentException("Unabled to retrieve Cart " + cartVO.uuid + " into BO. It has not been initialized or has already been validated")
    }
  }

  /**
   * @param poiOpt
   * @return formated location
   */
  private def toEventLocationVO(poiOpt:Option[Poi]) = poiOpt match{
    case Some(poi) => {
      poi.road1.getOrElse("")+" "+
      poi.road2.getOrElse("")+" "+
      poi.city.getOrElse("")+" "+
      poi.postalCode.getOrElse("")+" "+
      poi.state.getOrElse("")+" "+
      poi.city.getOrElse("")+" "+
      poi.countryCode.getOrElse("")+" "
    }
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

  def cancel(cartVO:CartVO ):CartVO = {
    BOCart.findByTransactionUuid(cartVO.uuid) match {
      case Some(boCart) => {
        DB localTx { implicit session =>
          // Mise à jour du statut et du transactionUUID
          /* code iper
        boCart.status = TransactionStatus.FAILED;
        boCart.save()
        */
          withSQL {
            update(BOCart).set(
              BOCart.column.status -> TransactionStatus.FAILED.toString,
              BOCart.column.lastUpdated -> DateTime.now
            ).where.eq(BOCart.column.id, boCart.id)
          }.update.apply()
        }

        val updatedCart = cartVO.copy(inTransaction = false) //cartVO.uuid = null;
        uuidService.setCart(updatedCart);
        updatedCart
      }
      case None => throw new IllegalArgumentException("Unabled to retrieve Cart " + cartVO.uuid + " into BO. It has not been initialized or has already been validated")
      }
  }
}

class CartException(val errors:Map[String,String]) extends Exception

case class AddCartItemException (override val errors:Map[String,String]) extends CartException(errors) {
  override def toString = errors.toString()
}

case class UpdateCartItemException (override val errors:Map[String,String]) extends CartException(errors)

case class RemoveCartItemException (override val errors:Map[String,String]) extends CartException(errors)

case class ClearCartException (override val errors:Map[String,String]) extends CartException(errors)

case class AddCouponToCartException (override val errors:Map[String,String]) extends CartException(errors)

case class RemoveCouponFromCartException (override val errors:Map[String,String]) extends CartException(errors)

case class CartVO(price: Long = 0, endPrice: Option[Long] = Some(0), reduction: Long = 0, finalPrice: Long = 0, count: Int = 0, uuid: String,
                  cartItemVOs: Array[CartItemVO]=Array(), coupons: Array[CouponVO]=Array(), inTransaction:Boolean = false)

object ProductType extends Enumeration {
  class ProductTypeType(s: String) extends Val(s)
  type ProductType = ProductTypeType
  val SERVICE = new ProductTypeType("SERVICE")
  val PRODUCT = new ProductTypeType("PRODUCT")
  val DOWNLOADABLE = new ProductTypeType("DOWNLOADABLE")
  val PACKAGE = new ProductTypeType("PACKAGE")
  val OTHER = new ProductTypeType("OTHER")

  def valueOf(str:String):ProductType = str match {
    case "SERVICE"=> SERVICE
    case "PRODUCT"=> PRODUCT
    case "DOWNLOADABLE"=> DOWNLOADABLE
    case "PACKAGE"=> PACKAGE
    case _=> OTHER
  }

}

object ProductCalendar extends Enumeration {
  class ProductCalendarType(s: String) extends Val(s)
  type ProductCalendar = ProductCalendarType
  val NO_DATE = new ProductCalendarType("NO_DATE")
  val DATE_ONLY = new ProductCalendarType("DATE_ONLY")
  val DATE_TIME = new ProductCalendarType("DATE_TIME")

  def valueOf(str:String):ProductCalendar = str match {
    case "DATE_ONLY"=> DATE_ONLY
    case "DATE_TIME"=> DATE_TIME
    case _=> NO_DATE
  }
}

case class RegisteredCartItemVO
(cartItemId: String, id: String, email: Option[String], firstname: Option[String]=None, lastname: Option[String]=None, phone: Option[String]=None, birthdate: Option[DateTime]=None)

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

  def apply(str:String) = str match{
    case "KG" => KG
    case "LB" => LB
    case "G"  => G
    case _ => throw new RuntimeException("unexpected WeightUnit value")
  }
}

object LinearUnit extends Enumeration {
  class LinearUnitType(s: String) extends Val(s)
  type LinearUnit = LinearUnitType
  val CM = new LinearUnitType("CM")
  val IN = new LinearUnitType("IN")

  def apply(str:String) = str match{
    case "CM" => CM
    case "IN" => IN
    case _ => throw new RuntimeException("unexpected LinearUnit value")
  }
}

case class ShippingVO
(id:Long, weight: Long, weightUnit: WeightUnit, width: Long, height: Long, depth: Long, linearUnit: LinearUnit, amount: Long, free: Boolean)

case class ReductionSold(id:Long,sold:Long=0,dateCreated:DateTime = DateTime.now, lastUpdated:DateTime = DateTime.now) extends DateAware

object ReductionSold extends SQLSyntaxSupport[ReductionSold] {

  def apply(rn: ResultName[ReductionSold])(rs:WrappedResultSet): ReductionSold = ReductionSold(
    id=rs.get(rn.id),sold=rs.get(rn.sold), dateCreated = rs.get(rn.dateCreated),lastUpdated = rs.get(rn.lastUpdated))

  def get(id:Long)(implicit session: DBSession):Option[ReductionSold]={
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

case class CouponVO(id:Long, name:String,code:String, startDate:Option[DateTime] = None, endDate:Option[DateTime] = None, active:Boolean = false, price:Long = 0)
object CouponVO {
  def apply(coupon:Coupon):CouponVO = {
    CouponVO( id = coupon.id, name = coupon.name, code = coupon.code, startDate = coupon.startDate, endDate = coupon.endDate, active = coupon.active)
  }
}

case class Coupon
(id: Long, name: String, code: String, companyFk: Long,startDate: Option[DateTime]=None, endDate: Option[DateTime]=None, //price: Long,
  numberOfUses:Option[Long]=None, reductionSoldFk:Option[Long]=None,active: Boolean = true,catalogWise:Boolean = false,
  dateCreated:DateTime = DateTime.now,lastUpdated:DateTime = DateTime.now) extends DateAware {

  def rules = Coupon.getRules(this.id)
}

case class Company(id: Long, name: String, code: String,aesPassword:String,dateCreated:DateTime = DateTime.now,lastUpdated:DateTime = DateTime.now) extends DateAware {
}

object Company extends SQLSyntaxSupport[Company]{
  def apply(rn: ResultName[Company])(rs:WrappedResultSet): Company = Company(
    id=rs.get(rn.id),name = rs.get(rn.name), code=rs.get(rn.code),aesPassword = rs.get(rn.aesPassword),
    dateCreated = rs.get(rn.dateCreated),lastUpdated = rs.get(rn.lastUpdated))

  def get(id:Long):Option[Company] = {

    val c = Company.syntax("c")

    val res = DB readOnly { implicit session =>
      withSQL {
        select.from(Company as c).where.eq(c.id, id)
      }.map(Company(c.resultName)).single().apply()
    }
    res
  }

  def findByCode(code:String):Option[Company]={
    val c = Company.syntax("c")
    DB readOnly {
      implicit session =>
        withSQL {
          select.from(Company as c).where.eq(c.code, code)
        }.map(Company(c.resultName)).single().apply()
    }
  }

}

object Coupon extends SQLSyntaxSupport[Coupon]{
  def apply(rn: ResultName[Coupon])(rs:WrappedResultSet): Coupon = Coupon(
    id=rs.get(rn.id),name = rs.get(rn.name), code=rs.get(rn.code), startDate=rs.get(rn.startDate),endDate=rs.get(rn.endDate),
    numberOfUses = rs.get(rn.numberOfUses),companyFk = rs.get(rn.companyFk),reductionSoldFk=rs.get(rn.reductionSoldFk),
    dateCreated = rs.get(rn.dateCreated),lastUpdated = rs.get(rn.lastUpdated))

  def findByCode(companyCode:String, couponCode:String):Option[Coupon]={
    val compagny = Company.findByCode(companyCode)
    if (compagny.isEmpty) None
    else {
      val c = Coupon.syntax("c")
      DB readOnly {
        implicit session =>
          withSQL {
            select.from(Coupon as c).where.eq(c.code, couponCode).and.eq(c.companyFk,compagny.get.id)
          }.map(Coupon(c.resultName)).single().apply()
      }
    }
  }

  def get(id: Long)/*(implicit session: DBSession)*/:Option[Coupon]={
    val c = Coupon.syntax("c")
    DB readOnly { implicit session =>
      withSQL {
        select.from(Coupon as c).where.eq(c.id, id)
      }.map(Coupon(c.resultName)).single().apply()
    }
  }

  def getRules(couponId:Long):List[ReductionRule]={
    val c = ReductionRule.syntax("c")
    DB readOnly { implicit session =>
      withSQL {
        select.from(ReductionRule as c).where.eq(c.id, couponId)
      }.map(ReductionRule(c.resultName)).list().apply()
    }
  }
}

object ReductionRuleType extends Enumeration {
  class ReductionRuleTypeType(s: String) extends Val(s)
  type ReductionRuleType = ReductionRuleTypeType
  val DISCOUNT = new ReductionRuleTypeType("DISCOUNT")
  val X_PURCHASED_Y_OFFERED = new ReductionRuleTypeType("X_PURCHASED_Y_OFFERED")

  def apply(name:String) = name match{
    case "DISCOUNT" => DISCOUNT
    case "X_PURCHASED_Y_OFFERED" => X_PURCHASED_Y_OFFERED
    case _ => throw new Exception("Not expected ReductionRuleType")
  }
}

case class ReductionRule(
                          id:Long,
                          xtype: ReductionRuleType,
                          quantityMin:Option[Long],
                          quantityMax:Option[Long],
                          discount:Option[String], //discount (or percent) if type is DISCOUNT (example : -1000 or * 10%)
                          xPurchased:Option[Long], yOffered:Option[Long],
  dateCreated:DateTime = DateTime.now,lastUpdated:DateTime = DateTime.now) extends DateAware

object ReductionRule extends SQLSyntaxSupport[ReductionRule] {
  def apply(rn: ResultName[ReductionRule])(rs: WrappedResultSet): ReductionRule = ReductionRule(
    id = rs.get(rn.id), xtype = ReductionRuleType(rs.string("reduction_rule_type")), quantityMin = rs.get(rn.quantityMin), quantityMax = rs.get(rn.quantityMax),
    discount = rs.get(rn.discount), xPurchased = rs.get(rn.xPurchased), yOffered = rs.get(rn.yOffered),
    dateCreated = rs.get(rn.dateCreated), lastUpdated = rs.get(rn.lastUpdated))
}
object CouponService extends BoService {

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
          insert.into(ReductionSold).namedValues(col.id -> d.id, col.sold -> d.sold, col.dateCreated -> d.dateCreated, col.lastUpdated -> d.lastUpdated)
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
        case Some(nb) => {
          nb > reduc.sold
        }
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

  def getTicketTypesIdsWhereCouponApply(couponId: Long) : List[Long] = DB readOnly {
    implicit session => {
      sql"""
         | select id from ticket_type
         | where tt.product_fk is in(select product_id from coupon_product where products_fk=${couponId})
         | or tt.product_fk is in (select id from product where category_fk in (select category_id from coupon_category where categories_fk=${couponId}))
       """.map(rs => rs.long("id")).list.apply()
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
    val coupon = Coupon.get(c.id).get

    val couponVO = CouponVO(coupon)

    //the coupon must be active and is valid for a date between startDate and endDate
    (coupon.active, coupon.startDate, coupon.endDate) match {
      case (true, Some(startDate), Some(endDate)) => {
        if ((startDate.isBeforeNow || startDate.isEqualNow) && (endDate.isAfterNow || endDate.isEqualNow)) {
          /*
          List<TicketType> listTicketType = TicketType.createCriteria().list {
            or {
              if (coupon.products) {
                "in"("product", coupon.products)
              }
              if (coupon.categories) {
                product {
                  'in' ('category', coupon.categories)
                }
              }
              if (coupon.ticketTypes) {
                "in"("id", coupon.ticketTypes.collect {it.id})
              }
            }
          }*/

          // liste des ticketTypes pour lesquels le coupon s'applique
          //val listTicketType = List[TicketType]()
          //pas besoin de ramener les ticketTypes, les id suffisent

          val listTicketTypeIds = getTicketTypesIdsWhereCouponApply(c.id)

          if (listTicketTypeIds.size > 0) {
            var quantity = 0l
            var xPurchasedPrice = java.lang.Long.MAX_VALUE
            cart.cartItemVOs.foreach { cartItem =>
              if (listTicketTypeIds.exists {
                _ == cartItem.skuId
              }) {
                quantity += cartItem.quantity
                if (cartItem.endPrice.getOrElse(0l) > 0) {
                  xPurchasedPrice = Math.min(xPurchasedPrice, cartItem.endPrice.get)
                }
              }
            }
            if (xPurchasedPrice == java.lang.Long.MAX_VALUE) {
              xPurchasedPrice = 0
            }

            if (quantity > 0) {
              //couponVO.active = true
              var couponVOprice = couponVO.price
              coupon.rules.foreach {
                //TODO foldLeft later
                rule => {
                  couponVOprice = rule.xtype match {
                    case ReductionRuleType.DISCOUNT => {
                      cart.endPrice match {
                        case Some(endprice) => couponVOprice + computeDiscount(rule.discount, endprice)
                        case None => couponVOprice + computeDiscount(rule.discount, cart.price)
                      }
                    }
                    case ReductionRuleType.X_PURCHASED_Y_OFFERED => {
                      val multiple = quantity / rule.xPurchased.get //FIXME None.get possible
                      (couponVO.price + (xPurchasedPrice * rule.yOffered.get * multiple)) //FIXME None.get possible
                    }
                    case _ => couponVOprice
                  }
                }
              }
              couponVO.copy(active = true, price = couponVOprice)

              /* code iper
                if (ReductionRuleType.DISCOUNT == rule.xtype) {
                  if (cart.endPrice != null) {
                    couponVO.price += IperUtil.computeDiscount(rule.discount, cart.endPrice)
                  }
                  else {
                    couponVO.price += IperUtil.computeDiscount(rule.discount, cart.price)
                  }
                }
                else if (ReductionRuleType.X_PURCHASED_Y_OFFERED.equals(rule.xtype)) {
                  long multiple = quantity / rule.xPurchased
                  couponVO.price += xPurchasedPrice * rule.yOffered * multiple
                }*/

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
      case _ => couponVO
    }
  }

  /**
   *
   * @param discountRule
   * @param prixDeBase
   * @return
   */
  //TODO translate variable label
  def computeDiscount(discountRule: Option[String], prixDeBase: Long) = {

    discountRule match{
      case Some(regle) => {
        if (regle.endsWith("%"))
        {
          val pourcentage = java.lang.Float.parseFloat(regle.substring(0, regle.length()-1))
          (prixDeBase * pourcentage / 100).toLong //TODO recheck the rounded value computed
        }
        else if (regle.startsWith ("+"))
        {

          val increment = java.lang.Long.parseLong(regle.substring(1))
          prixDeBase + increment;
        }
        else if (regle.startsWith ("-"))
        {
          val decrement = java.lang.Long.parseLong (regle.substring(1))
          prixDeBase - decrement;
        }
        else
        {
          java.lang.Long.parseLong(regle)
        }
      }
      case None => prixDeBase
    }
  }
}


object TransactionStatus extends Enumeration {
  class TransactionStatusType(s: String) extends Val(s)
  type TransactionStatus = TransactionStatusType
  val PENDING = new TransactionStatusType("PENDING")
  val PAYMENT_NOT_INITIATED = new TransactionStatusType("PAYMENT_NOT_INITIATED")
  val FAILED = new TransactionStatusType("FAILED")
  val COMPLETE = new TransactionStatusType("COMPLETE")

  def valueOf(str:String):TransactionStatus = str match {
    case "PENDING"=> PENDING
    case "PAYMENT_NOT_INITIATED"=> PAYMENT_NOT_INITIATED
    case "FAILED"=> FAILED
    case "COMPLETE"=> COMPLETE
  }

  override def toString = this match {
    case PENDING => "PENDING"
    case PAYMENT_NOT_INITIATED => "PAYMENT_NOT_INITIATED"
    case FAILED => "FAILED"
    case COMPLETE => "COMPLETE"
    case _ => "Invalid value"
  }
}

case class BOProduct(id:Long,acquittement:Boolean=false,price:Long=0,principal:Boolean=false,productFk:Long,
                     dateCreated:DateTime = DateTime.now,lastUpdated:DateTime = DateTime.now) extends DateAware {

  def product : Product = {
    Product.get(this.productFk).get
  }
  def delete()(implicit session: DBSession) {
    BOProduct.delete(this.id)
  }
}
object BOProduct extends SQLSyntaxSupport[BOProduct]{

  override val tableName = "b_o_product"

  def apply(rn: ResultName[BOProduct])(rs:WrappedResultSet): BOProduct = new BOProduct(id=rs.get(rn.id),acquittement=rs.get(rn.acquittement),price=rs.get(rn.price),principal=rs.get(rn.principal),productFk=rs.get(rn.productFk),dateCreated = rs.get(rn.dateCreated),lastUpdated = rs.get(rn.lastUpdated))


  def delete(id:Long)(implicit session: DBSession) {
    withSQL {
      deleteFrom(BOProduct).where.eq(BOProduct.column.id,  id)
    }.update.apply()
  }
}
case class BOTicketType(id:Long,quantity : Int = 1, price:Long,shortCode:Option[String],
                        ticketType : Option[String],firstname : Option[String], lastname : Option[String],
                        email : Option[String],phone : Option[String],age:Int,
                        birthdate : Option[DateTime],startDate : Option[DateTime],endDate : Option[DateTime],
                        qrcode:Option[String]=None, qrcodeContent:Option[String]=None,
                        //bOProduct : BOProduct,
                        bOProductFk : Long,
                        dateCreated:DateTime,lastUpdated:DateTime) extends DateAware {

  /*
  def delete()(implicit session: DBSession) {
    withSQL {
      deleteFrom(BOTicketType).where.eq(BOTicketType.column.id,  this.id)
    }.update.apply()
  }
  */
  def delete()(implicit session: DBSession){
    BOTicketType.delete(this.id)
  }
}

object BOTicketType extends SQLSyntaxSupport[BOTicketType]{

  override val tableName = "b_o_ticket_type"

  def apply(rn: ResultName[BOTicketType])(rs:WrappedResultSet): BOTicketType = new BOTicketType(id=rs.get(rn.id),quantity=rs.get(rn.quantity),price=rs.get(rn.price),
    shortCode = rs.get(rn.shortCode),ticketType=rs.get(rn.ticketType),firstname=rs.get(rn.firstname),lastname=rs.get(rn.lastname),email=rs.get(rn.email),phone=rs.get(rn.phone),
    age = rs.get(rn.age), birthdate=rs.get(rn.birthdate),startDate=rs.get(rn.endDate),endDate=rs.get(rn.endDate),bOProductFk=rs.get(rn.bOProductFk),
    qrcode = rs.get(rn.qrcode), qrcodeContent = rs.get(rn.qrcodeContent),
    dateCreated = rs.get(rn.dateCreated),lastUpdated = rs.get(rn.lastUpdated))

  def findByBOProduct(boProductId:Long):List[BOTicketType]={
    val t = BOTicketType.syntax("t")
    DB readOnly {
      implicit session =>
        withSQL {
          select.from(BOTicketType as t).where.eq(t.bOProductFk, boProductId)
        }.map(BOTicketType(t.resultName)).list().apply()
    }
  }


  def delete(id:Long)(implicit session: DBSession) {
    //sql"delete from b_o_ticket_type where b_o_product_fk=${boProductId}"
    withSQL {
      deleteFrom(BOTicketType).where.eq(BOTicketType.column.id,  id)
    }.update.apply()
  }
}

case class BOCart(id:Long, transactionUuid:String,date:DateTime, price:Long,status : TransactionStatus, currencyCode:String,currencyRate:Double,companyFk:Long,buyer:String = "christophe.galant@ebiznext.com",
                  dateCreated:DateTime = DateTime.now,lastUpdated:DateTime = DateTime.now) extends DateAware {

  def delete()(implicit session: DBSession){
    BOCart.delete(this.id)
  }
}

object BOCart extends SQLSyntaxSupport[BOCart]{

  override val tableName = "b_o_cart"

  def apply(rn: ResultName[BOCart])(rs:WrappedResultSet): BOCart = BOCart(
    id=rs.get(rn.id),transactionUuid=rs.get(rn.transactionUuid),price=rs.get(rn.price),
    date=rs.get(rn.date),status=TransactionStatus.valueOf(rs.string("s_on_t")), //FIXME
    currencyCode = rs.get(rn.currencyCode),currencyRate = rs.get(rn.currencyRate),
    companyFk=rs.get(rn.companyFk),dateCreated=rs.get(rn.dateCreated),lastUpdated=rs.get(rn.lastUpdated))

  def findByTransactionUuidAndStatus(uuid:String, status:TransactionStatus):Option[BOCart] = {

    val t = BOCart.syntax("t")
    val res: Option[BOCart] = DB readOnly {
      implicit session =>
        withSQL {
          select.from(BOCart as t).where.eq(t.transactionUuid, uuid).and.eq(t.status, status.toString) //"PENDING"
        }.map(BOCart(t.resultName)).single().apply()
    }
    res
  }

  def findByTransactionUuid(uuid:String):Option[BOCart] = {
    val t = BOCart.syntax("t")
    DB readOnly {
      implicit session =>
        withSQL {
          select.from(BOCart as t).where.eq(t.transactionUuid, uuid)
        }.map(BOCart(t.resultName)).single().apply()
    }
  }

  //def insert():BOCart = {}

  def delete(id:Long)(implicit session: DBSession) {
    withSQL {
      deleteFrom(BOCart).where.eq(BOCart.column.id,  id)
    }.update.apply()
  }
}


//"SALE_" + boCart.id + "_" + boProduct.id
case class BOCartItem(id:Long,code : String,
                      price: Long,
                      tax: Double,
                      endPrice: Long,
                      totalPrice: Long,
                      totalEndPrice: Long,
                      hidden : Boolean = false,
                      quantity : Int = 1,
                      startDate : Option[DateTime],
                      endDate : Option[DateTime],
                      //bOCart : BOCart,
                      bOCartFk : Long,
                      dateCreated:DateTime = DateTime.now,lastUpdated:DateTime = DateTime.now) extends DateAware {

  def delete()(implicit session: DBSession){
    BOCartItem.delete(this.id)
  }
}

object BOCartItem extends SQLSyntaxSupport[BOCartItem]{

  override val tableName = "b_o_cart_item"

  def apply(rn: ResultName[BOCartItem])(rs:WrappedResultSet): BOCartItem = new BOCartItem(id=rs.get(rn.id),code=rs.get(rn.code),price=rs.get(rn.price),tax=rs.get(rn.tax),
    endPrice=rs.get(rn.endPrice),totalPrice=rs.get(rn.totalPrice),totalEndPrice=rs.get(rn.totalEndPrice),quantity=rs.get(rn.quantity),hidden=rs.get(rn.hidden),
    startDate = rs.get(rn.startDate),endDate = rs.get(rn.endDate),dateCreated = rs.get(rn.dateCreated),lastUpdated = rs.get(rn.lastUpdated),bOCartFk=rs.get(rn.bOCartFk))

  def findByBOCart(boCart:BOCart):List[BOCartItem] = {

    val t = BOCartItem.syntax("t")
    val res: List[BOCartItem] = DB readOnly {
      implicit session =>
        withSQL {
          select.from(BOCartItem as t).where.eq(t.bOCartFk, boCart.id)
        }.map(BOCartItem(t.resultName)).list().apply()
    }
    res
  }

  def bOProducts(boCartItem: BOCartItem) : List[BOProduct] = {

    DB readOnly {
      implicit session =>
        sql"select p.* from b_o_cart_item_b_o_product ass inner join b_o_product p on ass.boproduct_id=p.id where b_o_products_fk=${boCartItem.id}"
          .map(rs => new BOProduct(id=rs.long("id"),acquittement=rs.boolean("acquittement"),price=rs.long("price"),principal=rs.boolean("principal"),productFk=rs.long("product_fk"),dateCreated = rs.dateTime("date_created"),lastUpdated = rs.dateTime("last_updated"))).list().apply()
    }
  }

  def delete(id:Long)(implicit session: DBSession) {
    withSQL {
      deleteFrom(BOCartItem).where.eq(BOCartItem.column.id,  id)
    }.update.apply()
  }
}