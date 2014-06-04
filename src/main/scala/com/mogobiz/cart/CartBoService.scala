package com.mogobiz.cart

import java.util.{Date, Locale, Calendar}
import com.mogobiz.cart.ProductType.ProductType
import com.mogobiz.cart.ProductCalendar.ProductCalendar
import com.mogobiz.cart.WeightUnit.WeightUnit
import com.mogobiz.cart.LinearUnit.LinearUnit
import org.json4s.{DefaultFormats, Formats}
import com.mogobiz.{Currency, Cart, Utils}
import org.joda.time.DateTime
import scalikejdbc.config.DBs
import akka.actor.FSM.->
import com.mogobiz.cart.TransactionStatus.TransactionStatus
import scalikejdbc.SQLInterpolation._
import scala.Some
import scalikejdbc.{DBSession, DB, WrappedResultSet}

/**
 * Created by Christophe on 05/05/2014.
 * The Cart Back office Service in charge of retrieving/storing data from database
 */
object CartBoService extends BoService {

  type CouponVO = Coupon

  DBs.setupAll()

  val uuidService = UuidBoService
  val productService = ProductBoService
  val taxRateService = TaxRateBoService

  def initCart(uuid:String): CartVO = {

    val cartVO = getCart(uuid)
    cartVO match {
      case Some(c) => c
      case None => {
        val c = CartVO(uuid = uuid)
        uuidService.set(c)
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
    // init local vars
    val ticketType:TicketType = TicketType.get(ticketTypeId) //TODO error management

    val product = ticketType.product.get;
    val startEndDate = Utils.verifyAndExtractStartEndDate(Some(ticketType), dateTime); //TODO finish implement the method

    var errors:Map[String,String] = Map()

    //TODO check parameters
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
    val tax = taxRateService.findTaxRateByProduct(product, locale.getCountry)
    val endPrice = taxRateService.calculateEndPrix(itemPrice, tax)
    val totalPrice = quantity * itemPrice;
    val totalEndPrice = endPrice match {
      case Some(p) => Some(quantity * itemPrice)
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

    val items = cartVO.cartItemVOs:+item //WARNING not optimal

    val newEndPrice = (oldCartEndPrice,item.totalEndPrice) match {
      case (Some(o),Some(t))=> Some(o+t)
      case _ => None
    }
    val newcart = cartVO.copy(
        price = oldCartPrice + item.totalPrice,//(cartVO.price + item.totalPrice),
        endPrice = newEndPrice,
        count = items.size,
        cartItemVOs = items)

    uuidService.set(newcart)
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

        uuidService.set(updatedCart)
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
    val items = parts._1
    val removed = parts._2.headOption


    val cartItem = removed.get

    val sku = TicketType.get(cartItem.skuId)

    productService.increment(sku, cartItem.quantity, cartItem.startDate)
    /* TODO
    cartVO.cartItemVOs -= cartItem
    cartVO.price -= cartItem.totalPrice;
    if (cartVO.endPrice != null && cartItem.totalEndPrice != null) {
      cartVO.endPrice -= cartItem.totalEndPrice;
    }
    cartVO.count -= 1;
    */
    val updatedCart = cartVO //TODO return cartVO.copy
    uuidService.set(updatedCart);

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
    uuidService.set(updatedCart);
    updatedCart
  }

  @throws[AddCouponToCartException]
  def addCoupon(companyId:Long, couponCode:String, cartVO:CartVO, locale:Locale, currencyCode:String) : CartVO = {
    val errors:Map[String,String] = Map()

    val optCoupon = Coupon.findByCode(companyId, couponCode)

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
          uuidService.set(cart);
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
   * @param companyId
   * @param locale
   * @param currencyCode
   * @param cartVO
   * @param couponCode
   * @return
   */
  @throws[RemoveCouponFromCartException]
  def removeCoupon(companyId:Long, couponCode:String, cartVO:CartVO,locale:Locale, currencyCode: String ):CartVO = {
    val errors:Map[String,String] = Map()

    val optCoupon = Coupon.findByCode(companyId, couponCode)
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
          val coupons = cartVO.coupons.filter{ c => couponCode == c.code }
          val cart = cartVO.copy( coupons = coupons.toArray)
          uuidService.set(cart);
          cart
        }
      }
    }

    updatedCart

  }

  def prepareBeforePayment(companyId:Long, countryCode:String, stateCode:String, currencyCode:String, cartVO:CartVO,rate:Currency) : CartVO = {
    //val rate:MogopayRate = rateService.getMogopayRate(currencyCode)
    //val rate:Currency= rateService.getMogopayRate(currencyCode)

    val errors:Map[String,String] = Map()

    // Calcul des montants TTC
    /* TODO a remplacé par une méthode calculAmountAllTaxIncluded sur CartVO (renvoie un new CartVO avec une new colloection of items)
    cartVO.endPrice = 0;
    cartVO.cartItemVOs?.each {CartItemVO cartItem ->
      Product product = Product.get(cartItem.getProductId());
      cartItem.tax = taxRateService.findTaxRateByProduct(product, countryCode, stateCode)
      if (cartItem.tax == null) {
        // Le pays/state de livraison n'ont pas de taxRate associé,
        // la taxe est donc de 0
        cartItem.tax = 0;
      }
      cartItem.endPrice = taxRateService.calculateEndPrix(cartItem.price, cartItem.tax)
      cartItem.totalEndPrice = cartItem.quantity * cartItem.endPrice;
      cartVO.endPrice += cartItem.totalEndPrice
    }*/

    //TRANSACTION 1 : SUPPRESSIONS
    DB localTx { implicit session =>

      if (cartVO.inTransaction) {
        //if (cartVO.uuid) {
        // On supprime tout ce qui concerne l'ancien BOCart (s'il est en attente)
        val boCart = BOCart.findByTransactionUuidAndStatus(cartVO.uuid, TransactionStatus.PENDING)
        if (boCart.isDefined) {

        BOCartItem.findByBOCart(boCart.get).foreach { boCartItem =>
          BOCartItem.bOProducts(boCart.get).foreach { boProduct =>
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
      //cartVO.uuid = UUID.randomUUID();

    val updatedCart = cartVO.copy(inTransaction = true) //cartVO.inTransaction = true

    //TRANSACTION 2 : INSERTIONS
    DB localTx { implicit session =>

      var boCartId = 0
      withSQL { // =boCart.save()
        val newid = newId()
        boCartId = newid
        insert.into(BOCart).values(newid, cartVO.uuid, DateTime.now, cartVO.price, TransactionStatus.PENDING, currencyCode, rate.rate, companyId)
      }.update.apply()

      val boCart = new BOCart(
        id = boCartId,
        transactionUuid = cartVO.uuid,
        date = DateTime.now,
        price = cartVO.price,
        status = TransactionStatus.PENDING,
        currencyCode = currencyCode,
        currencyRate = rate.rate,
        companyFk = companyId
      )


      cartVO.cartItemVOs.foreach { cartItem => {
        //val product = Product.get(cartItem.productId)
        val ticketType = TicketType.get(cartItem.skuId)

        // Création du BOProduct correspondant au produit principal
        var boProductId = 0
        val boProduct = new BOProduct(id = boProductId, principal = true, productFk = cartItem.productId, price = cartItem.totalEndPrice.getOrElse(-1))
        withSQL {
          //=boProduct.save()
          val newid = newId()
          boProductId = newid
          //acquittement:Boolean=false,price:Long=0,principal:Boolean=false,productFk:Long,dateCreated:DateTime = DateTime.now,lastUpdate:DateTime = DateTime.now
          insert.into(BOProduct).values(newid, boProduct.acquittement, boProduct.price, boProduct.principal, boProduct.productFk, boProduct.dateCreated, boProduct.lastUpdated)
        }.update.apply()


        cartItem.registeredCartItemVOs.foreach {
          registeredCartItem => {
            // Création des BOTicketType (SKU)
            var boTicketId = 0
            val boTicket = new BOTicketType(id = boTicketId,
              quantity = 1, price = cartItem.totalEndPrice.getOrElse(-1),shortCode = None,
              ticketType = Some(ticketType.name), firstname = registeredCartItem.firstname,
              lastname = registeredCartItem.lastname, email = registeredCartItem.email,
              phone = registeredCartItem.phone,
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
              insert.into(BOTicketType).namedValues(
                b.id -> boTicketId,
                b.quantity -> 1, b.price -> cartItem.totalEndPrice.getOrElse(-1), b.ticketType -> Some(ticketType.name), b.firstname -> registeredCartItem.firstname,
                b.lastname -> registeredCartItem.lastname, b.email -> registeredCartItem.email,
                b.phone -> registeredCartItem.phone,
                b.birthdate -> registeredCartItem.birthdate, b.startDate -> cartItem.startDate, b.endDate -> cartItem.endDate,
                b.dateCreated -> DateTime.now, b.lastUpdated -> DateTime.now,
                b.bOProductFk -> boProductId
              )
            }.update.apply()


            //génération du qr code uniquement pour les services
            /* TODO
          if (product.xtype == ProductType.SERVICE)
          {
            boTicket.shortCode = "P" + boProduct.id + "T" + boTicket.id
            String qrCodeContent = "EventId:"+product.id+";BoProductId:"+boProduct.id+";BoTicketId:"+boTicket.id
            qrCodeContent += ";EventName:" + product.name + ";EventDate:" + DateUtilitaire.format(cartItem.startDate, "dd/MM/yyyy HH:mm") + ";FirstName:"
            qrCodeContent += boTicket.firstname + ";LastName:" + boTicket.lastname + ";Phone:" + boTicket.phone
            qrCodeContent += ";TicketType:" +boTicket.ticketType + ";shortCode:" + boTicket.shortCode
            qrCodeContent = SecureCodec.encrypt(qrCodeContent, product.company.aesPassword);
            ByteArrayOutputStream output = new ByteArrayOutputStream()
            QRCodeUtils.createQrCode(output, qrCodeContent, 256,"png")
            String qrCodeBase64 = Base64.encode(output.toByteArray())
            boTicket.qrcode = qrCodeBase64
            boTicket.qrcodeContent = qrCodeContent
          }
          boTicket.save()
          */
          }
        }

        //create Sale
        var saleId = 0
        val sale = new BOCartItem(id = saleId,
          code = "SALE_" + boCart.id + "_" + boProduct.id,
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
            b.code -> ("SALE_" + boCart.id + "_" + boProduct.id),
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


    /*
    // Construit la map correspondant au panier pour le jsoniser
    Map map = [:]
    map["count"] = cartVO.count

    List<Map> listMapItem = []
    cartVO.cartItemVOs?.each { CartItemVO cartItem ->
      String [] included = [
      "id", "productId", "productName", "xtype", "skuId", "skuName", "quantity", "tax", "startDate", "endDate",
      "shipping",
      "shipping.weight",
      "shipping.weightUnit",
      "shipping.width",
      "shipping.height",
      "shipping.depth",
      "shipping.linearUnit",
      "shipping.amount",
      "shipping.free",
      "registeredCartItemVOs",
      "registeredCartItemVOs.cartItemId",
      "registeredCartItemVOs.id",
      "registeredCartItemVOs.email",
      "registeredCartItemVOs.firstname",
      "registeredCartItemVOs.lastname",
      "registeredCartItemVOs.phone",
      "registeredCartItemVOs.birthdate"
      ]
      Map mapItem = RenderUtil.asMapForJSON(null, included, null, cartItem)
      mapItem["price"] = rateService.calculateAmount(cartItem.price, rate);
      mapItem["endPrice"] = rateService.calculateAmount(cartItem.endPrice, rate);
      mapItem["totalPrice"] = rateService.calculateAmount(cartItem.totalPrice, rate);
      mapItem["totalEndPrice"] = rateService.calculateAmount(cartItem.totalEndPrice, rate);
      listMapItem << mapItem
    }
    map["cartItemVOs"] = listMapItem

    // Calcul du prix des coupons
    updateCoupons(cartVO);
    List<Map> listCoupon = []
    cartVO.coupons?.each { CouponVO c ->
      String [] included = [
      "id",
      "name",
      "code",
      "active",
      "startDate",
      "endDate"
      ]
      Map mapCoupon = RenderUtil.asMapForJSON(null, included, null, c)
      mapCoupon["price"] = rateService.calculateAmount(c.price, rate);
      listCoupon << mapCoupon
    }
    map["coupons"] = listCoupon

    map["price"] = rateService.calculateAmount(cartVO.price, rate)
    map["endPrice"] = rateService.calculateAmount(cartVO.endPrice, rate)
    map["reduction"] = rateService.calculateAmount(cartVO.reduction, rate)
    map["finalPrice"] = rateService.calculateAmount(cartVO.finalPrice, rate)

    Map data = [:]
    data["amount"] = rateService.calculateAmount(cartVO.finalPrice, rate)
    data["currencyCode"] = currencyCode
    data["currencyRate"] = rate.rate.doubleValue();
    data["transactionExtra"] = new JsonBuilder(map).toPrettyString();

    result.data = data
    uuidDataService.setCart(cartVO);
    return result;

    */
    updatedCart
  }

  def commit(cartVO:CartVO, transactionUuid:String ):List[Map[String,Any]]=
  {
    //def emailingData = []


    BOCart.findByTransactionUuid(cartVO.uuid) match {
      case Some(boCart) => {

        val emailingData = BOCartItem.findByBOCart(boCart).map { boCartItem =>
          BOCartItem.bOProducts(boCart).map { boProduct =>
          //boCartItem.bOProducts.each { BOProduct boProduct ->
            //Product product = boProduct.product;
            val product = boProduct.product
            BOTicketType.findByBOProduct(boProduct.id).map {  boTicketType =>
              /*
              def emailContent = [:]
              emailContent.put("email", boTicketType.email)
              emailContent.put("eventName", product.name)
              emailContent.put("startDate", RenderUtil.asMapForJSON(boTicketType.startDate))
              emailContent.put("stopDate", RenderUtil.asMapForJSON(boTicketType.endDate))
              emailContent.put("location", toEventLocationVO(product.poi))
              emailContent.put("type", boTicketType.ticketType)
              emailContent.put("price",boTicketType.price)
              emailContent.put("qrcode", boTicketType.qrcodeContent)
              emailContent.put("shortCode", boTicketType.shortCode)
              emailingData << emailContent
              */
              var map :Map[String,Any]= Map()
              map+=("email" -> boTicketType.email)
              map+=("eventName" -> product.name)
              map+=("startDate" -> (boTicketType.startDate))
              map+=("stopDate" -> (boTicketType.endDate))
              //TODO map+=("location" -> toEventLocationVO(product.poi))
              map+=("type" -> boTicketType.ticketType)
              map+=("price" ->boTicketType.price)
              //TODO map+=("qrcode" -> boTicketType.qrcodeContent)
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
              BOCart.column.status -> TransactionStatus.COMPLETE,
              BOCart.column.lastUpdated -> DateTime.now
            ).where.eq(BOCart.column.id, boCart.id)
          }.update.apply()

          /* code iper
        cartVO.uuid = null;
        cartVO.price = 0
        cartVO.endPrice = 0
        cartVO.count = 0;
        cartVO.cartItemVOs = null
        //uuidDataService.removeCart(); PAS IMPLEMENTE DANS IPER
        */
        }
        val updatedCart = cartVO.copy(inTransaction = false, price = 0, endPrice = Some(0),count = 0, cartItemVOs = Array())
        uuidService.set(updatedCart)
        //TODO sendEmails(emailingData)
        emailingData
      }
      case None => throw new IllegalArgumentException("Unabled to retrieve Cart " + cartVO.uuid + " into BO. It has not been initialized or has already been validated")
    }
  }

  def cancel(locale:Locale, currencyCode:String , cartVO:CartVO ):CartVO = {
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
              BOCart.column.status -> TransactionStatus.FAILED,
              BOCart.column.lastUpdated -> DateTime.now
            ).where.eq(BOCart.column.id, boCart.id)
          }.update.apply()
        }

        val updatedCart = cartVO.copy(inTransaction = false) //cartVO.uuid = null;
        uuidService.set(cartVO);
        updatedCart
      }
      case None => throw new IllegalArgumentException("Unabled to retrieve Cart " + cartVO.uuid + " into BO. It has not been initialized or has already been validated")
      }
  }

  private def getCart(uuid:String): Option[CartVO] = {
    import org.json4s.native.JsonMethods._
    implicit def json4sFormats: Formats = DefaultFormats

    uuidService.get(uuid) match {
      case Some(data) => {
        val parsed = parse(data.payload)
        val cart = parsed.extract[CartVO]
        Some(cart)
      }
      case _ => None
    }
  }
}

case class AddCartItemException(val errors:Map[String,String]) extends Exception

case class UpdateCartItemException (val errors:Map[String,String]) extends Exception

case class RemoveCartItemException (val errors:Map[String,String]) extends Exception

case class ClearCartException (val errors:Map[String,String]) extends Exception

case class AddCouponToCartException (val errors:Map[String,String]) extends Exception

case class RemoveCouponFromCartException (val errors:Map[String,String]) extends Exception

case class CartVO(price: Long = 0, endPrice: Option[Long] = Some(0), reduction: Long = 0, finalPrice: Long = 0, count: Int = 0, uuid: String,
                  cartItemVOs: Array[CartItemVO]=Array(), coupons: Array[CouponVO]=Array(), inTransaction:Boolean = false)

object ProductType extends Enumeration {
  type ProductType = Value
  val SERVICE = Value("SERVICE")
  val PRODUCT = Value("PRODUCT")
  val DOWNLOADABLE = Value("DOWNLOADABLE")
  val PACKAGE = Value("PACKAGE")
  val OTHER = Value("OTHER")

  def valueOf(str:String):ProductType = str match {
    case "SERVICE"=> SERVICE
    case "PRODUCT"=> PRODUCT
    case "DOWNLOADABLE"=> DOWNLOADABLE
    case "PACKAGE"=> PACKAGE
    case _=> OTHER
  }

}

object ProductCalendar extends Enumeration {
  type ProductCalendar = Value
  val NO_DATE = Value("NO_DATE")
  val DATE_ONLY = Value("DATE_ONLY")
  val DATE_TIME = Value("DATE_TIME")

  def valueOf(str:String):ProductCalendar = str match {
    case "DATE_ONLY"=> DATE_ONLY
    case "DATE_TIME"=> DATE_TIME
    case _=> NO_DATE
  }
}

case class RegisteredCartItemVO
(cartItemId: String, id: String, email: Option[String], firstname: Option[String], lastname: Option[String], phone: Option[String], birthdate: Option[DateTime])

case class CartItemVO
(id: String, productId: Long, productName: String, xtype: ProductType, calendarType: ProductCalendar, skuId: Long, skuName: String,
 quantity: Int, price: Long, endPrice: Option[Long], tax: Option[Float], totalPrice: Long, totalEndPrice: Option[Long],
 startDate: Option[DateTime], endDate: Option[DateTime], registeredCartItemVOs: Array[RegisteredCartItemVO], shipping: Option[ShippingVO])


object WeightUnit extends Enumeration {
  type WeightUnit = Value
  val KG = Value("kg")
  val LB = Value("lb")
  val G = Value("g")
}

object LinearUnit extends Enumeration {
  type LinearUnit = Value
  val CM = Value("cm")
  val IN = Value("in")
}

case class ShippingVO
(weight: Long, weightUnit: WeightUnit, width: Long, height: Long, depth: Long, linearUnit: LinearUnit, amount: Long, free: Boolean)


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
  dateCreated:DateTime = DateTime.now,lastUpdated:DateTime = DateTime.now) extends DateAware

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
  def consumeCoupon(coupon:Coupon) : Boolean = {
    DB localTx { implicit s =>

      val reducId = if (coupon.reductionSoldFk.isEmpty) {
        var id = 0
        withSQL {
          id = newId()
          val d = ReductionSold(id)
          val col = ReductionSold.column
          insert.into(ReductionSold).namedValues(col.id->d.id, col.sold -> d.sold, col.dateCreated -> d.dateCreated, col.lastUpdated -> d.lastUpdated)
        }.update.apply()

        val c = Coupon.column
        withSQL {
          update(Coupon).set(c.reductionSoldFk -> id,c.lastUpdated -> DateTime.now).where.eq(c.id, coupon.id)
        }.update.apply()
        id
      }else{
        coupon.reductionSoldFk.get
      }
      val reduc = ReductionSold.get(reducId).get
      val canConsume = coupon.numberOfUses match {
        case Some(nb) => {
          nb>reduc.sold
        }
        case _ => true
      }

      if(canConsume){
        val c = ReductionSold.column
        withSQL {
          update(ReductionSold).set(c.sold -> (reduc.sold+1),c.lastUpdated -> DateTime.now).where.eq(c.id, reducId)
        }.update.apply()
      }
      canConsume
    }
  }

  def releaseCoupon(coupon:Coupon):Unit= {
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

  /**
   * update the active attribute and calculate the reduction price
   * using the content of the cartVO and the current date (to define if coupon
   * is active or not)
   * @param couponVO
   * @param cart
   */
  def updateCoupon(couponVO:CouponVO, cart:CartVO):CouponVO = {
    val coupon = Coupon.get(couponVO.id).get

    val updatedCouponVO = CouponVO(coupon)

    (coupon.active,coupon.startDate,coupon.endDate) match {
      case (true,Some(startDate),Some(endDate)) => {
        if((startDate.isBeforeNow || startDate.isEqualNow) && (endDate.isAfterNow || endDate.isEqualNow)){
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
          }

          if (listTicketType.size() > 0) {
            long quantity = 0
            long xPurchasedPrice = Long.MAX_VALUE;
            cart.cartItemVOs.each { CartItemVO cartItem ->
              if (listTicketType.find {it.id == cartItem.skuId} != null) {
                quantity += cartItem.quantity
                if (cartItem.endPrice > 0) {
                  xPurchasedPrice = Math.min(xPurchasedPrice, cartItem.endPrice)
                }
              }
            }
            if (xPurchasedPrice == Long.MAX_VALUE) {
              xPurchasedPrice = 0
            }

            if (quantity > 0) {
              couponVO.active = true
              coupon.rules.each {ReductionRule rule ->
                if (ReductionRuleType.DISCOUNT.equals(rule.xtype)) {
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
                }
              }
            }
          }*/
          couponVO.copy()
        }else couponVO
      }
      case _ => couponVO
    }
  }
}
object Coupon extends SQLSyntaxSupport[Coupon]{
  def apply(rn: ResultName[Coupon])(rs:WrappedResultSet): Coupon = Coupon(
    id=rs.get(rn.id),name = rs.get(rn.name), code=rs.get(rn.code), startDate=rs.get(rn.startDate),endDate=rs.get(rn.endDate),
    numberOfUses = rs.get(rn.numberOfUses),companyFk = rs.get(rn.companyFk),reductionSoldFk=rs.get(rn.reductionSoldFk),
    dateCreated = rs.get(rn.dateCreated),lastUpdated = rs.get(rn.lastUpdated))

  def findByCode(companyId:Long, couponCode:String):Option[Coupon]={
    val c = Coupon.syntax("c")
    DB readOnly {
      implicit session =>
        withSQL {
          select.from(Coupon as c).where.eq(c.code, couponCode).and.eq(c.companyFk,companyId)
        }.map(Coupon(c.resultName)).single().apply()
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
}

object TransactionStatus extends Enumeration {
  type TransactionStatus = Value
  val PENDING = Value("PENDING")
  val PAYMENT_NOT_INITIATED = Value("PAYMENT_NOT_INITIATED")
  val FAILED = Value("FAILED")
  val COMPLETE = Value("COMPLETE")

  def valueOf(str:String):TransactionStatus = str match {
    case "PENDING"=> PENDING
    case "PAYMENT_NOT_INITIATED"=> PAYMENT_NOT_INITIATED
    case "FAILED"=> FAILED
    case "COMPLETE"=> COMPLETE
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
  def apply(rn: ResultName[BOProduct])(rs:WrappedResultSet): BOProduct = new BOProduct(id=rs.get(rn.id),acquittement=rs.get(rn.acquittement),price=rs.get(rn.price),principal=rs.get(rn.principal),productFk=rs.get(rn.productFk),dateCreated = rs.get(rn.dateCreated),lastUpdated = rs.get(rn.lastUpdated))


  def delete(id:Long)(implicit session: DBSession) {
    withSQL {
      deleteFrom(BOProduct).where.eq(BOProduct.column.id,  id)
    }.update.apply()
  }
}
case class BOTicketType(id:Long,quantity : Int = 1, price:Long,shortCode:Option[String],
                        ticketType : Option[String],firstname : Option[String], lastname : Option[String],
                        email : Option[String],phone : Option[String],
                        birthdate : Option[DateTime],startDate : Option[DateTime],endDate : Option[DateTime],
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
  def apply(rn: ResultName[BOTicketType])(rs:WrappedResultSet): BOTicketType = new BOTicketType(id=rs.get(rn.id),quantity=rs.get(rn.quantity),price=rs.get(rn.price),
    shortCode = rs.get(rn.shortCode),ticketType=rs.get(rn.ticketType),firstname=rs.get(rn.firstname),lastname=rs.get(rn.lastname),email=rs.get(rn.email),phone=rs.get(rn.phone),
    birthdate=rs.get(rn.birthdate),startDate=rs.get(rn.endDate),endDate=rs.get(rn.endDate),bOProductFk=rs.get(rn.bOProductFk),
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

case class BOCart(id:Long, transactionUuid:String,date:DateTime, price:Long,status : TransactionStatus, currencyCode:String,currencyRate:Double,companyFk:Long,
                  dateCreated:DateTime = DateTime.now,lastUpdated:DateTime = DateTime.now) extends DateAware {

  def delete()(implicit session: DBSession){
    BOCart.delete(this.id)
  }
}

object BOCart extends SQLSyntaxSupport[BOCart]{

  def apply(rn: ResultName[BOCart])(rs:WrappedResultSet): BOCart = new BOCart(id=rs.get(rn.id),transactionUuid=rs.get(rn.transactionUuid),price=rs.get(rn.price),
    date=rs.get(rn.date),status=TransactionStatus.valueOf(rs.string("status")),currencyCode = rs.get(rn.currencyCode),currencyRate = rs.get(rn.currencyRate),
    companyFk=rs.get(rn.companyFk),dateCreated=rs.get(rn.dateCreated),lastUpdated=rs.get(rn.lastUpdated))

  def findByTransactionUuidAndStatus(uuid:String, status:TransactionStatus):Option[BOCart] = {

    val t = BOCart.syntax("t")
    val res: Option[BOCart] = DB readOnly {
      implicit session =>
        withSQL {
          select.from(BOCart as t).where.eq(t.transactionUuid, uuid).and.eq(t.status, status)
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

  def bOProducts(boCart:BOCart) : List[BOProduct] = {

    DB readOnly {
      implicit session =>
        sql"select p.* from b_o_cart_item_b_o_product ass inner join b_o_product p on ass.boproduct_id=p.id where b_o_products_fk=${boCart.id}"
          .map(rs => new BOProduct(id=rs.long("id"),acquittement=rs.boolean("acquittement"),price=rs.long("price"),principal=rs.boolean("principal"),productFk=rs.long("productFk"),dateCreated = rs.dateTime("dateCreated"),lastUpdated = rs.dateTime("lastUpdated"))).list().apply()
    }
  }

  def delete(id:Long)(implicit session: DBSession) {
    withSQL {
      deleteFrom(BOCartItem).where.eq(BOCartItem.column.id,  id)
    }.update.apply()
  }
}