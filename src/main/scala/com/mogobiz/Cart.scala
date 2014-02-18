package com.mogobiz

/**
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
                     email: String
                     , firstName: Option[String]
                     , lastName: Option[String]
                     , phone: Option[String]
                     , birthdate: Option[String])

case class CartRequest(
                        cartId: Int
                        , storeCode: String
                        , currencyCode: String
                        , countryCode: String
                        , lang: String)

case class AddCartRequest(
                           cartId: Int
                           , skuId: Int
                           , dateTime: String
                           , quantity: Int
                           , registeredCartItems: List[CartItem]
                           , storeCode: String
                           , currencyCode: String
                           , countryCode: String
                           , lang: String)

case class UpdateCartItemRequest(
                                  cartId: Int
                                  , cartItemId: Int
                                  , quantity: Int
                                  , storeCode: String
                                  , currencyCode: String
                                  , countryCode: String
                                  , lang: String)

case class RemoveCartItemRequest(
                                  cartId: Int
                                  , cartItemId: Int
                                  , quantity: Int
                                  , storeCode: String
                                  , currencyCode: String
                                  , countryCode: String
                                  , lang: String)

case class PrepareCartRequest(
                               storeCode: String
                               , currencyCode: String
                               , countryCode: String
                               , lang: String)

case class CommitCartRequest(
                              cartId: String
                              , transactionUUID: String
                              , storeCode: String
                              , countryCode: String
                              , lang: String)

case class CancelCartRequest(
                              storeCode: String
                              , countryCode: String
                              , lang: String)
