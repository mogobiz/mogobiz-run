package com.mogobiz.cart

import org.joda.time.DateTime

/**
 * Created by Christophe on 09/05/2014.
 */
object CartCommands {

}

case class AddToCartCommand(
                             skuId:Long,
                             quantity:Int,
                             dateTime:Option[DateTime],
                             registeredCartItems:List[RegisteredCartItemVO])

case class UpdateCartItemCommand (cartItemId: String,quantity: Int)

case class RemoveCartItemCommand (cartItemId: String)