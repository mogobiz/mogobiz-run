/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import java.util.UUID

import akka.actor.ActorSystem
import com.mogobiz.es.{EsClient, _}
import com.mogobiz.json.JsonUtil
import com.mogobiz.pay.codes.MogopayConstant
import com.mogobiz.pay.config.MogopayHandlers.handlers._
import com.mogobiz.pay.config.Settings
import com.mogobiz.pay.handlers.shipping.EasyPostHandler
import com.mogobiz.pay.model.{Account, RoleName}
import com.mogobiz.run.config
import com.mogobiz.run.config.MogobizHandlers.handlers._
import com.mogobiz.run.es._
import com.mogobiz.run.exceptions.{IllegalStatusException, MinMaxQuantityException, NotAuthorizedException, NotFoundException}
import com.mogobiz.run.model.Mogobiz._
import com.mogobiz.run.model.RequestParameters.{BOListCustomersRequest, BOListOrdersRequest, CreateBOReturnedItemRequest, UpdateBOReturnedItemRequest}
import com.mogobiz.run.model.{CartChanges, StockChange, WebHookData}
import com.mogobiz.run.utils.Paging
import com.mogobiz.utils.{EmailHandler, GlobalUtil}
import com.mogobiz.utils.EmailHandler.Mail
import com.sksamuel.elastic4s.ElasticDsl._
import org.elasticsearch.common.joda.time.format.ISODateTimeFormat
import org.elasticsearch.search.SearchHits
import org.elasticsearch.search.sort.SortOrder
import org.joda.time.DateTime
import org.json4s.Extraction
import org.json4s.JsonAST._
import org.json4s.jackson.JsonMethods._
import scalikejdbc.{DB, DBSession}
import spray.client.pipelining._
import spray.http._

import scala.concurrent.Future
import com.mogobiz.run.config.Settings.Mail.Smtp.MailSettings

/**
  */
class BackofficeHandler extends JsonUtil with BoService {
  import org.json4s.{DefaultFormats, Formats}
  implicit def json4sFormats: Formats = DefaultFormats

  private val mogopayIndex = Settings.Mogopay.EsIndex

  @throws[NotAuthorizedException]
  def listOrders(storeCode: String, accountUuid: Option[String], req: BOListOrdersRequest): Paging[JValue] = {
    val account = accountUuid.flatMap { uuid =>
      accountHandler.load(uuid)
    } getOrElse (throw new NotAuthorizedException(""))
    val customer = account.roles.find { role =>
      role == RoleName.CUSTOMER
    }.map { r =>
      account
    }
    val merchant = account.roles.find { role =>
      role == RoleName.MERCHANT
    }.map { r =>
      account
    }

    val _size: Int = req.maxItemPerPage.getOrElse(100)
    val _from: Int = req.pageOffset.getOrElse(0) * _size

    val boCartTransactionUuidList = req.deliveryStatus.map { deliveryStatus =>
      (EsClient.searchAllRaw(
              search in boCartHandler.buildIndex(storeCode) types "BOCart" sourceInclude "transactionUuid"
                query matchQuery("shopCarts.cartItems.bODelivery.status", deliveryStatus)
                size _size
                sort {
              by field "xdate" order SortOrder.DESC
            }) hits () map { hit =>
            hit2JValue(hit) \ "transactionUuid" match {
              case JString(uuid) => {
                Some(uuid)
              }
              case _ => None
            }
          }).toList.flatten.distinct.mkString("|")
    }

    val filters = List(
        req.lastName.map { lastName =>
          or(createTermFilter("customer.lastName", Some(lastName)).get,
             createTermFilter("customer.firstName", Some(lastName)).get)
        },
        createTermFilter("status", req.transactionStatus),
        createTermFilter("email", req.email.map(_.toLowerCase)),
        createTermFilter("customer.uuid", customer.map { c =>
          c.uuid
        }),
        createTermFilter("vendor.uuid", merchant.map { m =>
          m.uuid
        }),
        createRangeFilter("transactionDate", req.startDate, req.endDate),
        createTermFilter("amount", req.price),
        createOrFilterBySplitValues(boCartTransactionUuidList, v => createTermFilter("transactionUUID", Some(v)))
    ).flatten

    val response = try {
      EsClient.searchAllRaw(
          search in mogopayIndex types "BOTransaction" postFilter and(filters: _*)
            from _from
            size _size
            sort {
          by field "transactionDate" order SortOrder.DESC
        })
    } catch {
      case e: Throwable => e.printStackTrace()
    }
    val transformer: PartialFunction[JValue, JValue] = completeBOTransactionWithDeliveryStatus(storeCode)
    Paging.build(_size, _from, response.asInstanceOf[SearchHits], { hit =>
      hit.transform(transformer).transformField(filterBOTransactionField)
    })
  }

  @throws[NotAuthorizedException]
  def listCustomers(storeCode: String, accountUuid: Option[String], req: BOListCustomersRequest): Paging[JValue] = {
    val merchant = accountUuid.flatMap { uuid =>
      accountHandler.load(uuid).flatMap { account =>
        account.roles.find { role =>
          role == RoleName.MERCHANT
        }.map { r =>
          account
        }
      }
    } getOrElse (throw new NotAuthorizedException(""))

    val _size: Int = req.maxItemPerPage.getOrElse(100)
    val _from: Int = req.pageOffset.getOrElse(0) * _size

    val accountFilters = List(
        req.lastName.flatMap { lastName =>
          createTermFilter("lastName", Some(lastName.toLowerCase))
        },
        createTermFilter("email", req.email.map(_.toLowerCase)),
        createTermFilter("owner", Some(merchant.uuid))
    ).flatten

    val response = EsClient.searchAllRaw(
        search in mogopayIndex types "Account" postFilter and(accountFilters: _*)
          from _from
          size _size
          sort {
        by field "lastName" order SortOrder.DESC
      })

    Paging.build(_size, _from, response, { hit =>
      hit
    })
  }

  @throws[NotFoundException]
  def cartDetails(storeCode: String, accountUuid: Option[String], transactionUuid: String): JValue = {
    val account = accountUuid.flatMap { uuid =>
      accountHandler.load(uuid)
    } getOrElse (throw new NotAuthorizedException(""))
    val customer = account.roles.find { role =>
      role == RoleName.CUSTOMER
    }.map { r =>
      account
    }
    val merchant = account.roles.find { role =>
      role == RoleName.MERCHANT
    }.map { r =>
      account
    }

    val transactionFilters = List(
        createTermFilter("customer.uuid", customer.map { c =>
          c.uuid
        }),
        createTermFilter("vendor.uuid", merchant.map { m =>
          m.uuid
        }),
        createTermFilter("transactionUUID", Some(transactionUuid))
    ).flatten

    // Make sure the customer accesses his own transactions
    val transactionRequest = search in mogopayIndex types "BOTransaction" sourceInclude "transactionUUID" postFilter and(
          transactionFilters: _*)
    val esTransactionUuid = EsClient
      .searchRaw(transactionRequest)
      .map { hit =>
        hit2JValue(hit) \ "transactionUUID" match {
          case JString(uuid) => {
            uuid
          }
          case _ => throw new NotFoundException("")
        }
      }
      .getOrElse(throw new NotFoundException(""))

    EsClient
      .searchRaw(
          search in boCartHandler.buildIndex(storeCode) types "BOCart" query matchQuery("transactionUuid",
                                                                                      esTransactionUuid)
      )
      .getOrElse(throw new NotFoundException(s"Transaction UUID $esTransactionUuid"))
  }

  def notifyItemReturned(merchant: Account,
                         customer: Account,
                         boCartItem: BOCartItem,
                         boReturn: BOReturn,
                         locale: Option[String]): Unit = {
    import com.mogobiz.run.implicits.Json4sProtocol
    val json: JValue = JObject(JField("cartItem", Extraction.decompose(boCartItem))) merge
        JObject(JField("merchant", Extraction.decompose(merchant))) merge
        JObject(JField("customer", Extraction.decompose(customer))) merge
        JObject(JField("returnStatus", Extraction.decompose(boReturn)))
    //    val map = Map("cartItem" -> boCartItem, "merchant" -> merchant, "customer" -> customer, "returnStatus" -> boReturn)

    val (subject, body) =
      templateHandler.mustache(Some(merchant), "mail-return-created", locale, compact(render(json)))
    EmailHandler.Send(
        Mail(
            merchant.email -> s"""${merchant.firstName.getOrElse("")} ${merchant.lastName.getOrElse("")}""",
            List(customer.email),
            Nil,
            Nil,
            subject,
            body,
            Some(body),
            None
        ))

  }

  @throws[NotAuthorizedException]
  @throws[NotFoundException]
  @throws[MinMaxQuantityException]
  def createBOReturnedItem(storeCode: String,
                           accountUuid: String,
                           transactionUuid: String,
                           boCartItemUuid: String,
                           req: CreateBOReturnedItemRequest,
                           locale: Option[String]): Unit = {
    val customer: Account = accountHandler.getCustomer(accountUuid).getOrElse(throw NotAuthorizedException(""))
    val merchantUuid = customer.owner.getOrElse(
        throw NotAuthorizedException("Merchant Id not present in Customer Data (Should never happen)"))
    val merchant =
      accountHandler.getMerchant(merchantUuid).getOrElse(throw NotAuthorizedException("Merchant not found"))

    val boCart: BOCart = boCartHandler.findByTransactionUuid(storeCode, transactionUuid).getOrElse(throw NotFoundException(""))
    if (boCart.buyer != customer.email) throw NotAuthorizedException("")

    // On ne traite le retour que pour les produits du shop Mogobiz
    val boShopCart = boCart.shopCarts.find(_.shopId == MogopayConstant.SHOP_MOGOBIZ).getOrElse(throw NotFoundException(""))
    val boCartItem = boShopCart.cartItems.find( _.uuid == boCartItemUuid).getOrElse(throw NotFoundException(""))

    if (req.quantity < 1 || req.quantity > boCartItem.quantity)
      throw new MinMaxQuantityException(1, boCartItem.quantity)

    DB localTx { implicit session =>

      val boReturn = BOReturn(Some(req.motivation),
        status = ReturnStatus.RETURN_SUBMITTED,
        uuid = UUID.randomUUID().toString,
        new DateTime())

      val boReturnedItem = BOReturnedItem(req.quantity,
        refunded = 0,
        totalRefunded = 0,
        ReturnedItemStatus.UNDEFINED,
        List(boReturn),
        UUID.randomUUID().toString)

      val newBOCartItem = boCartItem.copy(bOReturnedItems = boCartItem.bOReturnedItems :+ boReturnedItem)
      val newBOShopCart = replaceBOCartItem(boShopCart, newBOCartItem )
      val newBOCart = replaceBOShopCart(boCart, newBOShopCart)
      boCartHandler.update(newBOCart)

      notifyChangesIntoES(storeCode, newBOCart)
      notifyItemReturned(merchant, customer, newBOCartItem, boReturn, locale)
    }
  }

  def notifyItemReturnedStatusUpdated(merchant: Account,
                                      customer: Account,
                                      boCartItem: BOCartItem,
                                      beforeUpdate: BOReturn,
                                      afterUpdate: BOReturn,
                                      locale: Option[String]): Unit = {
    import com.mogobiz.run.implicits.Json4sProtocol
    val json: JValue = JObject(JField("cartItem", Extraction.decompose(boCartItem))) merge
        JObject(JField("merchant", Extraction.decompose(merchant))) merge
        JObject(JField("customer", Extraction.decompose(customer))) merge
        JObject(JField("beforeUpdate", Extraction.decompose(beforeUpdate))) merge
        JObject(JField("afterUpdate", Extraction.decompose(afterUpdate)))
    //val map = Map("cartItem" -> boCartItem, "merchant" -> merchant, "customer" -> customer, "oldReturnStatus" -> beforeUpdate, "newReturnStatus" -> afterUpdate)
    val (subject, body) =
      templateHandler.mustache(Some(merchant), "mail-return-updated", locale, compact(render(json)))
    EmailHandler.Send(
        Mail(
            merchant.email -> s"""${merchant.firstName.getOrElse("")} ${merchant.lastName.getOrElse("")}""",
            List(customer.email),
            Nil,
            Nil,
            subject,
            body,
            Some(body),
            None
        ))
  }

  @throws[NotAuthorizedException]
  @throws[NotFoundException]
  @throws[IllegalStatusException]
  def updateBOReturnedItem(storeCode: String,
                           accountUuid: String,
                           transactionUuid: String,
                           boCartItemUuid: String,
                           boReturnedItemUuid: String,
                           req: UpdateBOReturnedItemRequest,
                           locale: Option[String]): Unit = {
    val transactionalBloc = { implicit session: DBSession =>
      val merchant =
        accountHandler.load(accountUuid).getOrElse(throw new Exception("Merchant not found for returned item"))

      val boCart: BOCart = boCartHandler.findByTransactionUuid(storeCode, transactionUuid).getOrElse(throw NotFoundException(""))
      val customer = accountHandler.findByEmail(boCart.buyer, Some(accountUuid))
        .getOrElse(throw new Exception("Customer not found for returned item"))

      // On ne traite le retour que pour les produits du shop Mogobiz
      val boShopCart = boCart.shopCarts.find(_.shopId == MogopayConstant.SHOP_MOGOBIZ).getOrElse(throw NotFoundException(""))
      val boCartItem = boShopCart.cartItems.find( _.uuid == boCartItemUuid).getOrElse(throw NotFoundException(""))
      val boReturnedItem = boCartItem.bOReturnedItems.find(_.uuid == boReturnedItemUuid).getOrElse(throw new NotFoundException(""))
      val lastReturn: BOReturn = boReturnedItem.boReturns.sortBy(_.dateCreated.toDate.getTime * -1).head // on inverse pour avoir le plus récent en premier

      // Calcul du nouveau statut en fonction du statut existant
      val newStatus = ReturnedItemStatus.withName(req.status)
      val newReturnStatus = (lastReturn.status, ReturnStatus.withName(req.returnStatus)) match {
        case (ReturnStatus.RETURN_SUBMITTED, ReturnStatus.RETURN_TO_BE_RECEIVED) => ReturnStatus.RETURN_TO_BE_RECEIVED
        case (ReturnStatus.RETURN_SUBMITTED, ReturnStatus.RETURN_REFUSED)        => ReturnStatus.RETURN_REFUSED
        case (ReturnStatus.RETURN_TO_BE_RECEIVED, ReturnStatus.RETURN_RECEIVED)  => ReturnStatus.RETURN_RECEIVED
        case (ReturnStatus.RETURN_RECEIVED, ReturnStatus.RETURN_ACCEPTED)        => ReturnStatus.RETURN_ACCEPTED
        case (ReturnStatus.RETURN_RECEIVED, ReturnStatus.RETURN_REFUSED)         => ReturnStatus.RETURN_REFUSED
        case (_, _)                                                              => throw new IllegalStatusException()
      }

      val newBoReturn = BOReturn(Some(req.motivation),
        status = ReturnStatus.RETURN_SUBMITTED,
        uuid = UUID.randomUUID().toString,
        new DateTime())
      val newBOReturnedItem = boReturnedItem.copy(
        status = newStatus,
        refunded = req.refunded,
        totalRefunded = req.totalRefunded,
        boReturns = boReturnedItem.boReturns :+ newBoReturn
      )
      val newBOCartItem = replaceBOReturnedItem(boCartItem, newBOReturnedItem)
      val newBOShopCart = replaceBOCartItem(boShopCart, newBOCartItem)
      val newBOCart = replaceBOShopCart(boCart, newBOShopCart)
      boCartHandler.update(newBOCart)

      val stockChange =
        if (newReturnStatus == ReturnStatus.RETURN_ACCEPTED && boReturnedItem.status == ReturnedItemStatus.BACK_TO_STOCK) {
          ProductDao
            .getProductAndSku(storeCode, boCartItem.sku.id)
            .flatMap { productAndSku =>
              val stockChange = stockHandler.incrementStock(storeCode,
                                                            productAndSku._1,
                                                            productAndSku._2,
                                                            boReturnedItem.quantity,
                                                            boCartItem.startDate)
              implicit val system = ActorSystem()
              import system.dispatcher
              val pipeline: HttpRequest => Future[HttpResponse] = (addHeader("accept", "application/json")
                      ~> sendReceive)

              pipeline(Get(config.Settings.jahiaClearCacheUrl + "?productId=" + productAndSku._1.id))
              stockChange
            }
        } else None
      (merchant, customer, boCart, boCartItem, lastReturn, newBoReturn, stockChange)
    }

    val successBloc = { result: (Account, Account, BOCart, BOCartItem, BOReturn, BOReturn, Option[StockChange]) =>
      notifyChangesIntoES(storeCode, result._3, result._7)
      notifyItemReturnedStatusUpdated(result._1, result._2, result._4, result._5, result._6, locale)
    }

    GlobalUtil.runInTransaction(transactionalBloc, successBloc)
  }

  private def replaceBOReturn(boReturnedItem: BOReturnedItem, newBOReturn: BOReturn) : BOReturnedItem = {
    val newBOReturns = boReturnedItem.boReturns.map { oldBOReturn =>
      if (oldBOReturn.uuid == newBOReturn.uuid) newBOReturn
      else oldBOReturn
    }
    boReturnedItem.copy(boReturns = newBOReturns)
  }

  private def replaceBOReturnedItem(boCartItem: BOCartItem, newBOReturnedItem: BOReturnedItem) : BOCartItem = {
    val newBOReturnedItems = boCartItem.bOReturnedItems.map { oldBOReturnedItem =>
      if (oldBOReturnedItem.uuid == newBOReturnedItem.uuid) newBOReturnedItem
      else oldBOReturnedItem
    }
    boCartItem.copy(bOReturnedItems = newBOReturnedItems)
  }

  private def replaceBOCartItem(boShopCart: BOShopCart, newBOCartItem: BOCartItem) : BOShopCart = {
    val newBOCartItems = boShopCart.cartItems.map { oldBOCartItem =>
      if (oldBOCartItem.uuid == newBOCartItem.uuid) newBOCartItem
      else oldBOCartItem
    }
    boShopCart.copy(cartItems = newBOCartItems)
  }

  private def replaceBOShopCart(boCart: BOCart, newBOShopCart: BOShopCart) : BOCart = {
    val newBOShopCarts = boCart.shopCarts.map { oldBOShopCart =>
      if (oldBOShopCart.uuid == newBOShopCart.uuid) newBOShopCart
      else oldBOShopCart
    }
    boCart.copy(shopCarts = newBOShopCarts)
  }

  def shippingWebhook(storeCode: String, webhookProvider: String, data: String): Unit = {
    extractWebHookData(webhookProvider, data).map { webHookData =>
      boTransactionHandler.findByShipmentId(EasyPostHandler.EASYPOST_SHIPPING_PREFIX + webHookData.shipmentId).map {
        tx =>
          boCartHandler.findByTransactionUuid(storeCode, tx.transactionUUID).map { boCart =>
            CompanyDao.findByCode(storeCode).foreach { company =>
              if (boCart.companyFk == company.id) {
                DB localTx { implicit session =>
                  // On ne traite le retour que pour les produits du shop Mogobiz
                  val newBOShopCarts = boCart.shopCarts.map { boShopCart =>
                    if (boShopCart.shopId == MogopayConstant.SHOP_MOGOBIZ) {
                      val newCartItems = boShopCart.cartItems.map { boCartItem =>
                        val newBODelivery = boCartItem.bODelivery.map { boDelivery =>
                          boDelivery.copy(status = webHookData.newDeliveryStatus)
                        }
                        boCartItem.copy(bODelivery = newBODelivery)
                      }
                      boShopCart.copy(cartItems = newCartItems)
                    }
                    else boShopCart
                  }

                  val newBOCart = boCart.copy(shopCarts = newBOShopCarts)
                  boCartHandler.update(newBOCart)

                  notifyChangesIntoES(storeCode, boCart)
                }
              }
            }
          }
          val newShippingData = tx.shippingData.map { shippingData =>
            shippingData.copy(trackingHistory = data :: shippingData.trackingHistory)
          }
          val newTx = tx.copy(shippingData = newShippingData)
          boTransactionHandler.update(newTx)
      }
    }
  }

  protected def extractWebHookData(webhookProvider: String, data: String): Option[WebHookData] = {
    webhookProvider match {
      case "esay-post" => extractEasypostWebHookData(data)
      case _           => None
    }
  }

  protected def extractEasypostWebHookData(data: String): Option[WebHookData] = {
    val result = parse(data) \ "result"
    (result \ "object", result \ "shipment_id") match {
      case (JString("Tracker"), JString(shipmentId)) => {
        val newStatus = result \ "status" match {
          case JString("pre_transit")          => Some(DeliveryStatus.IN_PROGRESS)
          case JString("in_transit")           => Some(DeliveryStatus.IN_PROGRESS)
          case JString("delivered")            => Some(DeliveryStatus.DELIVERED)
          case JString("available_for_pickup") => Some(DeliveryStatus.DELIVERED)
          case JString("return_to_sender")     => Some(DeliveryStatus.RETURNED)
          case JString("cancelled")            => Some(DeliveryStatus.CANCELED)
          case JString("error")                => Some(DeliveryStatus.ERROR)
          case JString("failure")              => Some(DeliveryStatus.ERROR)
          case JString("unknown")              => None // On en connait pas le statut, on le laisse donc en l'état
          case _                               => None
        }
        newStatus.map { status =>
          WebHookData(shipmentId, status)
        }
      }
      case _ => None
    }
  }

  private def completeBOTransactionWithDeliveryStatus(storeCode: String): PartialFunction[JValue, JValue] = {
    case obj: JObject => {
      obj \ "transactionUUID" match {
        case (JString(transactionUuid)) => {
          val req = search in boCartHandler.buildIndex(storeCode) types "BOCart" query matchQuery("transactionUuid",
                                                                                                transactionUuid)
          println(req._builder.toString)
          val statusList = (EsClient.searchAllRaw(req) hits () map { hit =>
                hit2JValue(hit) \ "cartItems" match {
                  case JArray(cartItems) => {
                    cartItems.flatMap { cartItem =>
                      cartItem \ "bODelivery" \ "status" match {
                        case JString(status) => Some(JString(status))
                        case _               => None
                      }
                    }
                  }
                  case _ => List()
                }
              }).toList.flatten

          JObject(JField("deliveryStatus", JArray(statusList)) :: obj.obj)
        }
        case _ => obj
      }
    }
  }

  def notifyChangesIntoES(storeCode: String, boCart: BOCart, stockChange: Option[StockChange] = None): Unit = {
    cartHandler
      .notifyChangesIntoES(storeCode, CartChanges(boCartChange = Some(boCart), stockChanges = stockChange.map {
        List(_)
      }.getOrElse(Nil)), true)
  }

  private val filterBOTransactionField: PartialFunction[JField, JField] = {
    case JField("extra", v: JValue)         => JField("extra", JNothing)
    case JField("vendor", v: JValue)        => JField("vendor", JNothing)
    case JField("transactionDate", JInt(v)) => JField("transactionDate", JString(formatDateTime(v)))
    case JField("creationDate", JInt(v))    => JField("creationDate", JString(formatDateTime(v)))
    case JField("dateCreated", JInt(v))     => JField("dateCreated", JString(formatDateTime(v)))
    case JField("lastUpdated", JInt(v))     => JField("lastUpdated", JString(formatDateTime(v)))
    case JField("startDate", JInt(v))       => JField("startDate", JString(formatDateTime(v)))
    case JField("endDate", JInt(v))         => JField("endDate", JString(formatDateTime(v)))
    case JField("orderDate", JInt(v))       => JField("orderDate", JString(formatDateTime(v)))
    case JField("expiryDate", JInt(v))      => JField("expiryDate", JString(formatDateTime(v)))
    case JField("birthDate", JInt(v))       => JField("birthDate", JString(formatDateTime(v)))
  }

  private def formatDateTime(value: BigInt): String = {
    ISODateTimeFormat.dateTimeNoMillis().print(value.longValue())
  }

}
