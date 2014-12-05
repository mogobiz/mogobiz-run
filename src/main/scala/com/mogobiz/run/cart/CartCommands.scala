package com.mogobiz.run.cart

import com.fasterxml.jackson.databind.annotation.{JsonDeserialize, JsonSerialize}
import com.mogobiz.run.json.{JodaDateTimeOptionDeserializer, JodaDateTimeOptionSerializer}
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
                             registeredCartItems:List[RegisteredCartItemCommand])

case class RegisteredCartItemCommand(email: String,
                                     firstname: Option[String] = None,
                                     lastname: Option[String] = None,
                                     phone: Option[String] = None,
                                     birthdate: Option[DateTime] = None)

case class UpdateCartItemCommand (quantity: Int)
