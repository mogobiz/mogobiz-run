/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.model

import java.util.Date

import com.mogobiz.run.exceptions.CommentException
import com.mogobiz.run.utils.PagingParams
import org.joda.time.DateTime

object RequestParameters {

  case class PromotionRequest(override val maxItemPerPage: Option[Int],
                              override val pageOffset: Option[Int],
                              orderBy: Option[String],
                              orderDirection: Option[String],
                              categoryPath: Option[String],
                              lang: String)
      extends PagingParams

  case class FacetRequest(priceInterval: Long,
                          productId: Option[String],
                          xtype: Option[String],
                          name: Option[String],
                          code: Option[String],
                          rootCategoryPath: Option[String],
                          categoryPath: Option[String],
                          brandId: Option[String],
                          tags: Option[String],
                          notations: Option[String],
                          priceRange: Option[String],
                          creationDateMin: Option[String],
                          featured: Option[Boolean],
                          lang: String,
                          country: Option[String],
                          promotionId: Option[String],
                          hasPromotion: Option[Boolean],
                          inStockOnly: Option[Boolean],
                          property: Option[String],
                          features: Option[String],
                          variations: Option[String],
                          brandName: Option[String],
                          categoryName: Option[String],
                          multiCategory: Option[Boolean],
                          multiBrand: Option[Boolean],
                          multiTag: Option[Boolean],
                          multiFeatures: Option[Boolean],
                          multiVariations: Option[Boolean],
                          multiNotation: Option[Boolean],
                          multiPrices: Option[Boolean]) {
    def this(priceInterval: Long, lang: String) =
      this(priceInterval,
           None,
           None,
           None,
           None,
           None,
           None,
           None,
           None,
           None,
           None,
           None,
           None,
           lang,
           None,
           None,
           None,
           Some(true),
           None,
           None,
           None,
           None,
           None,
           None,
           None,
           None,
           None,
           None,
           None,
           None)
  }

  //--- Products

  case class ProductRequest(override val maxItemPerPage: Option[Int],
                            override val pageOffset: Option[Int],
                            id: Option[String],
                            xtype: Option[String],
                            name: Option[String],
                            fullText: Option[String],
                            code: Option[String],
                            categoryPath: Option[String],
                            brandId: Option[String],
                            tagName: Option[String],
                            notations: Option[String],
                            priceRange: Option[String],
                            creationDateMin: Option[String],
                            featured: Option[Boolean],
                            orderBy: Option[String],
                            orderDirection: Option[String],
                            lang: String,
                            currencyCode: Option[String],
                            countryCode: Option[String],
                            promotionId: Option[String],
                            hasPromotion: Option[Boolean],
                            inStockOnly: Option[Boolean],
                            property: Option[String],
                            feature: Option[String],
                            variations: Option[String])
      extends PagingParams {
    def this(lang: String, currencyCode: String, countryCode: String) =
      this(None,
           None,
           None,
           None,
           None,
           None,
           None,
           None,
           None,
           None,
           None,
           None,
           None,
           Some(false),
           None,
           None,
           lang,
           None,
           None,
           None,
           Some(false),
           Some(true),
           None,
           None,
           None)
  }

  case class ProductDetailsRequest(historize: Boolean,
                                   visitorId: Option[Long],
                                   currency: Option[String],
                                   country: Option[String],
                                   lang: String)

  case class FullTextSearchProductParameters(override val maxItemPerPage: Option[Int],
                                             override val pageOffset: Option[Int],
                                             lang: String,
                                             currency: Option[String],
                                             country: Option[String],
                                             query: String,
                                             highlight: Boolean,
                                             categoryPath: Option[String])
      extends PagingParams

  case class CompareProductParameters(lang: String, currency: Option[String], country: Option[String], ids: String)

  //--- Flattened ProductSkus
  case class SkuRequest(override val maxItemPerPage: Option[Int],
                        override val pageOffset: Option[Int],
                        id: Option[String],
                        productId: Option[String],
                        xtype: Option[String],
                        name: Option[String],
                        code: Option[String],
                        categoryPath: Option[String],
                        brandId: Option[String],
                        tagName: Option[String],
                        notations: Option[String],
                        priceRange: Option[String],
                        creationDateMin: Option[String],
                        featured: Option[Boolean],
                        orderBy: Option[String],
                        orderDirection: Option[String],
                        lang: String,
                        currencyCode: Option[String],
                        countryCode: Option[String],
                        promotionId: Option[String],
                        hasPromotion: Option[Boolean],
                        inStockOnly: Option[Boolean],
                        property: Option[String],
                        feature: Option[String],
                        variations: Option[String])
      extends PagingParams {
    def this(lang: String, currencyCode: String, countryCode: String) =
      this(None,
           None,
           None,
           None,
           None,
           None,
           None,
           None,
           None,
           None,
           None,
           None,
           None,
           Some(false),
           None,
           None,
           lang,
           None,
           None,
           None,
           Some(false),
           Some(true),
           None,
           None,
           None)
  }

  //--- Comment
  case class CommentRequest(notation: Int,
                            subject: String,
                            comment: String,
                            externalCode: Option[String],
                            created: Date = new Date) {
    def validate() {
      if (!(notation >= 0 && notation <= 5)) throw CommentException(CommentException.BAD_NOTATION)
    }
  }

  case class CommentGetRequest(override val maxItemPerPage: Option[Int], override val pageOffset: Option[Int])
      extends PagingParams

  case class CommentPutRequest(subject: Option[String], comment: Option[String], notation: Option[Int])

  case class NoteCommentRequest(note: Int)

  //--- Cart

  case class CartParameters(currency: Option[String],
                            country: Option[String],
                            state: Option[String],
                            lang: String = "_all")

  case class CouponParameters(currency: Option[String],
                              country: Option[String],
                              state: Option[String],
                              lang: String = "_all")

  case class PrepareTransactionParameters(currency: Option[String],
                                          country: Option[String],
                                          state: Option[String],
                                          buyer: String,
                                          shippingAddress: String,
                                          lang: String = "_all")

  case class CommitTransactionParameters(transactionUuid: String, lang: String = "_all")

  case class CancelTransactionParameters(currency: Option[String],
                                         country: Option[String],
                                         state: Option[String],
                                         lang: String = "_all")

  case class AddCartItemRequest(uuid: Option[String],
                                skuId: Long,
                                productUrl: String,
                                quantity: Int,
                                dateTime: Option[DateTime],
                                registeredCartItems: List[RegisteredCartItemRequest])

  case class RegisteredCartItemRequest(uuid: Option[String],
                                       email: String,
                                       firstname: Option[String] = None,
                                       lastname: Option[String] = None,
                                       phone: Option[String] = None,
                                       birthdate: Option[DateTime] = None)

  case class UpdateCartItemRequest(quantity: Int)

  case class BOListOrdersRequest(override val maxItemPerPage: Option[Int] = None,
                                 override val pageOffset: Option[Int] = None,
                                 lastName: Option[String] = None,
                                 email: Option[String] = None,
                                 startDate: Option[String] = None,
                                 endDate: Option[String] = None,
                                 price: Option[String] = None,
                                 transactionStatus: Option[String] = None,
                                 deliveryStatus: Option[String] = None)
      extends PagingParams

  case class BOListCustomersRequest(override val maxItemPerPage: Option[Int] = None,
                                    override val pageOffset: Option[Int] = None,
                                    lastName: Option[String] = None,
                                    email: Option[String] = None)
      extends PagingParams

  case class CreateBOReturnedItemRequest(quantity: Int, motivation: String)

  case class UpdateBOReturnedItemRequest(status: String,
                                         refunded: Long,
                                         totalRefunded: Long,
                                         returnStatus: String,
                                         motivation: String)

}
