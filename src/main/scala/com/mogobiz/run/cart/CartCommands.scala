package com.mogobiz.run.cart

import com.mogobiz.run.model.Render.RegisteredCartItem
import org.joda.time.DateTime

/**
 *
 * Created by Christophe on 09/05/2014.
 */
object CartCommands {

}

case class AddToCartCommand(
                             skuId:Long,
                             quantity:Int,
                             dateTime:Option[DateTime],
                             registeredCartItems:List[RegisteredCartItem])

case class UpdateCartItemCommand (quantity: Int)
