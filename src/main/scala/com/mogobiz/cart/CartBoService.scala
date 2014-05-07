package com.mogobiz.cart

import java.util.{Date, Locale, Calendar}
import com.mogobiz.cart.ProductType.ProductType
import com.mogobiz.cart.ProductCalendar.ProductCalendar
import com.mogobiz.cart.WeightUnit.WeightUnit
import com.mogobiz.cart.LinearUnit.LinearUnit
import org.json4s.{DefaultFormats, Formats}
import com.mogobiz.Utils
import org.joda.time.DateTime
import scalikejdbc.config.DBs

/**
 * Created by Christophe on 05/05/2014.
 * The Cart Back office Service in charge of retrieving/storing data from database
 */
object CartBoService {

  DBs.setupAll()

  val uuidService = UuidBoService
  val productService = ProductBoService
  val taxRateService = TaxRateBoService

  def initCart(uuid:String): CartVO = {

    val cartVO = getCart(uuid)
    cartVO match {
      case Some(c) => c
      case None => {
        val c = new CartVO(0,0,0,0,0,"",Array(),Array())
        uuidService.set(c)
        c
      }
    }
  }

  def addError(errors:Map[String,String],key:String,msg:String,parameters:List[Any],locale:Locale):Map[String,String]={
    //TODO
    errors
  }

  //def addToCart(AddToCartCommand)
  /**
   *
   * @param locale
   * @param currencyCode
   * @param cartVO
   * @param ticketType
   * @param quantity
   * @param dateTime
   * @param registeredCartItems
   * @return send back the new cart with the added item
   */
  def addItem(locale:Locale , currencyCode:String , cartVO:CartVO , ticketTypeId:Long, quantity:Int, dateTime:Option[DateTime], registeredCartItems:List[RegisteredCartItemVO]):CartVO  = {
    // init local vars
    val ticketType:TicketType = TicketType.get(ticketTypeId) //TODO error management

    val product = ticketType.product.get;
    val startEndDate = Utils.verifyAndExtractStartEndDate(Some(ticketType), dateTime); //TODO finish implement the method

    val errors:Map[String,String] = Map()

    //TODO check parameters
    if(cartVO.uuid.isEmpty){
      addError(errors, "cart", "initiate.payment.error", null, locale)
    }

    if (ticketType.minOrder > quantity || (ticketType.maxOrder < quantity && ticketType.maxOrder > -1))
    {
      addError(errors, "quantity", "min.max.error", List(ticketType.minOrder, ticketType.maxOrder), locale)
    }

    if (!dateTime.isDefined && !ProductCalendar.NO_DATE.equals(product.calendarType))
    {
      addError(errors, "dateTime", "nullable.error", null, locale)
    }
    else if (dateTime.isDefined && startEndDate == (None,None))
    {
      addError(errors, "dateTime", "unsaleable.error", null, locale)
    }
    if (product.xtype == ProductType.SERVICE) {
      if (registeredCartItems.size != quantity) {
        addError(errors, "registeredCartItems", "size.error", null, locale)
      }
      else {
        val emptyMails = for {
          item <- registeredCartItems
          if (item.email.trim().isEmpty)

        }yield item
        if(!emptyMails.isEmpty)
          addError(errors, "registeredCartItems", "email.error", null, locale)
      }
    }

    if(errors.size>0)
      throw new AddCartItemException(errors)

    // decrement stock
    productService.decrement(ticketType,quantity,startEndDate._1)

    //TODO resume existing items

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

    val item = new CartItemVO(newItemId,product.id,product.name,product.xtype,product.calendarType,ticketType.id,ticketType.name,quantity,itemPrice,endPrice,tax,totalPrice,totalEndPrice,startEndDate._1,startEndDate._2,registeredItems.toArray,shipping)

    val items = cartVO.cartItemVOs:+item //WARNING not optimal
    val newcart = new CartVO(price = (cartVO.price + item.totalPrice), endPrice = (cartVO.endPrice + item.totalEndPrice.getOrElse(0l)),reduction = cartVO.reduction,finalPrice=cartVO.finalPrice,count=items.size,uuid=cartVO.uuid,items,cartVO.coupons )
    uuidService.set(newcart)
    newcart
  }

  //TODO def updateItem

  //TODO def removeItem

  //TODO def clear

  //TODO def addCoupon

  //TODO def removeCoupon

  //TODO prepareBeforePayment

  //TODO commit

  //TODO cancel

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

case class AddCartItemException(val errors:Map[String,String]) extends Exception{

}

case class CartVO(price: Long = 0, endPrice: Long = 0, reduction: Long = 0, finalPrice: Long = 0, count: Int = 0, uuid: String, cartItemVOs: Array[CartItemVO]=Array(), coupons: Array[CouponVO]=Array())

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
(cartItemId: String, id: String, email: String, firstname: String, lastname: String, phone: String, birthdate: Calendar)

case class CartItemVO
(id: String, productId: Long, productName: String, xtype: ProductType, calendarType: ProductCalendar, skuId: Long, skuName: String, quantity: Int, price: Long, endPrice: Option[Long], tax: Option[Float], totalPrice: Long, totalEndPrice: Option[Long], startDate: Option[DateTime], endDate: Option[DateTime], registeredCartItemVOs: Array[RegisteredCartItemVO], shipping: Option[ShippingVO])


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



case class CouponVO
(id: Long, name: String, code: String, active: Boolean, startDate: Calendar, endDate: Calendar, price: Long)



