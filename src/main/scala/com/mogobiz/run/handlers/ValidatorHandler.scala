/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import java.io.{File, FileOutputStream}
import java.util.UUID

import com.mogobiz.run.config.Settings
import com.mogobiz.run.exceptions.NotFoundException
import com.mogobiz.run.model.Mogobiz._
import com.mogobiz.run.config.MogobizHandlers.handlers.boCartHandler
import com.mogobiz.utils.SymmetricCrypt
import org.joda.time.DateTime
import scalikejdbc._
import sqls.count

/**
  */
class ValidatorHandler {

  def buildDownloadLink(storeCode: String,
                        boCartUuid: String,
                        boShopCartUuid: String,
                        boCartItemUuid: String,
                        boProductUuid: String,
                        product: Product,
                        sku: Sku,
                        company: Company) = {
    val params =
      s"boCartUuid:$boCartUuid;boShopCartUuid:$boShopCartUuid;boCartItemUuid:$boCartItemUuid;boProductUuid:$boProductUuid;skuId:${sku.id};storeCode:$storeCode;maxDelay:${product.downloadMaxDelay};maxTimes:${product.downloadMaxTimes}"
    val encryptedParams = SymmetricCrypt.encrypt(params, company.aesPassword, "AES", hexSecret = true)
    s"${Settings.AccessUrl}/$storeCode/download/$encryptedParams"
  }

  @throws[NotFoundException]
  def download(storeCode: String, key: String): (String, File) = {
    val company = CompanyDao.findByCode(storeCode).getOrElse(throw NotFoundException(""))

    val params = SymmetricCrypt.decrypt(key, company.aesPassword, "AES", hexSecret = true)
    val paramsMap = params.split(";").map { s =>
      {
        val kv = s.split(":")
        (kv(0), kv(1))
      }
    }
    val paramBOCartUuid = paramsMap.find { kv =>
      kv._1 == "boCartUuid"
    }.getOrElse(throw NotFoundException(""))._2
    val paramBOShopCartUuid = paramsMap.find { kv =>
      kv._1 == "boShopCartUuid"
    }.getOrElse(throw NotFoundException(""))._2
    val paramBOCartItemUuid = paramsMap.find { kv =>
      kv._1 == "boCartItemUuid"
    }.getOrElse(throw NotFoundException(""))._2
    val paramBOProductUuid = paramsMap.find { kv =>
      kv._1 == "boProductUuid"
    }.getOrElse(throw NotFoundException(""))._2
    val paramSkuId = paramsMap.find { kv =>
      kv._1 == "skuId"
    }.getOrElse(throw NotFoundException(""))._2
    val paramStoreCode = paramsMap.find { kv =>
      kv._1 == "storeCode"
    }.getOrElse(throw NotFoundException(""))._2
    val paramMaxDelay = paramsMap.find { kv =>
      kv._1 == "maxDelay"
    }.getOrElse(throw NotFoundException(""))._2.toInt
    val paramMaxTimes = paramsMap.find { kv =>
      kv._1 == "maxTimes"
    }.getOrElse(throw NotFoundException(""))._2.toInt

    if (storeCode == paramStoreCode) {
      DB localTx { implicit session =>
        boCartHandler
          .find(paramStoreCode, paramBOCartUuid)
          .map { boCart =>
            boCart.shopCarts
              .find(_.uuid == paramBOShopCartUuid)
              .map { boShopCart =>
                boShopCart.cartItems
                  .find(_.uuid == paramBOCartItemUuid)
                  .map { boCartItem =>
                    val boProduct =
                      if (boCartItem.principal.uuid == paramBOProductUuid) Some(boCartItem.principal)
                      else boCartItem.secondary.find(_.uuid == paramBOProductUuid)

                    boProduct.map { boProduct =>
                      val expiredDate = boCartItem.dateCreated.plusDays(paramMaxDelay).toLocalDate

                      if ((paramMaxDelay == 0 || !expiredDate.isBefore(DateTime.now().toLocalDate)) &&
                          (paramMaxTimes == 0 || ConsumptionDao.countByBOProducts(boProduct) < paramMaxTimes)) {

                        if (ConsumptionDao.createConsumption(boProduct)) {
                          val file = new File(s"${Settings.ResourcesRootPath}/download/$paramSkuId")
                          if (file.exists())
                            (boProduct.product.name, file)
                          else throw NotFoundException("")
                        } else throw NotFoundException("")
                      } else throw NotFoundException("")
                    }.getOrElse(throw NotFoundException(""))
                  }
                  .getOrElse(throw NotFoundException(""))
              }
              .getOrElse(throw NotFoundException(""))
          }
          .getOrElse(throw NotFoundException(""))
      }
    } else throw NotFoundException("")
  }
}

object ConsumptionDao extends SQLSyntaxSupport[Consumption] with BoService {

  override val tableName = "consumption"

  def countByBOProducts(boProduct: BOProduct)(implicit session: DBSession = AutoSession): Int = {
    val o = ConsumptionDao.syntax("o")
    withSQL {
      select(count(o.uuid)).from(ConsumptionDao as o).where.eq(o.boProductUuid, boProduct.uuid)
    }.map(_.int(1)).single.apply().get
  }

  def createConsumption(boProduct: BOProduct)(implicit session: DBSession): Boolean = {
    val consumption = Consumption(id = newId(),
                                  None,
                                  boProduct.uuid,
                                  xdate = DateTime.now(),
                                  dateCreated = DateTime.now(),
                                  lastUpdated = DateTime.now(),
                                  uuid = UUID.randomUUID().toString)

    applyUpdate {
      insert
        .into(ConsumptionDao)
        .namedValues(
            ConsumptionDao.column.id             -> consumption.id,
            ConsumptionDao.column.boCartItemUuid -> consumption.boCartItemUuid,
            ConsumptionDao.column.boProductUuid  -> consumption.boProductUuid,
            ConsumptionDao.column.xdate          -> consumption.xdate,
            ConsumptionDao.column.dateCreated    -> consumption.dateCreated,
            ConsumptionDao.column.lastUpdated    -> consumption.lastUpdated,
            ConsumptionDao.column.uuid           -> consumption.uuid
        )
    } == 1
  }
}
