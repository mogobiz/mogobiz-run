package com.mogobiz.model

/**
 *
 * Created by dach on 18/02/2014.
 */


case class Cart(
                 transactionUUID: String
                 , date: String
                 , price: String
                 , status: String
                 , currencyCode: String
                 , currencyRate: String)

case class CartItem(
                     cartItemId: Int
                     , price: Long
                     , tax: Float
                     , endPrice: Long
                     , totalPrice: Long
                     , totalEndPrice: Long
                     , hidden: Boolean
                     , startDate: String
                     , endDate: String
                     , quantity: Int
                     )

case class RegisteredCartItem(
                               email: String
                               , firstName: String
                               , lastName: String
                               , phone: String
                               , birthday: String)

