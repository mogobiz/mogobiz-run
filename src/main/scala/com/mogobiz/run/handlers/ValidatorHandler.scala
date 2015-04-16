package com.mogobiz.run.handlers

import java.io.{FileOutputStream, File}
import java.nio.file.{Files, Paths}
import java.util.UUID

import com.mogobiz.run.config.Settings
import com.mogobiz.run.exceptions.NotFoundException
import com.mogobiz.run.model.Mogobiz._
import com.mogobiz.utils.SymmetricCrypt
import org.joda.time.DateTime
import scalikejdbc._
import sqls.count

/**
 * Created by yoannbaudy on 16/03/2015.
 */
class ValidatorHandler {

  @throws[NotFoundException]
  def download(storeCode: String, key: String) : (String, File) = {
    val company = CompanyDao.findByCode(storeCode).getOrElse(throw new NotFoundException(""))

    val params = SymmetricCrypt.decrypt(key, company.aesPassword, "AES")
    val paramsMap = params.split(";").map{ s => {
      val kv = s.split(":")
      (kv(0), kv(1))
    }}
    val paramBOCartItemUuid=paramsMap.find{kv => kv._1 == "boCartItemUuid"}.getOrElse(throw new NotFoundException(""))._2
    val paramSkuId=paramsMap.find{kv => kv._1 == "skuId"}.getOrElse(throw new NotFoundException(""))._2
    val paramStoreCode=paramsMap.find{kv => kv._1 == "storeCode"}.getOrElse(throw new NotFoundException(""))._2
    val paramMaxDelay=paramsMap.find{kv => kv._1 == "maxDelay"}.getOrElse(throw new NotFoundException(""))._2.toInt
    val paramMaxTimes=paramsMap.find{kv => kv._1 == "maxTimes"}.getOrElse(throw new NotFoundException(""))._2.toInt

    DB localTx { implicit session =>
      val boCartItem = BOCartItemDao.load(paramBOCartItemUuid).getOrElse(throw new NotFoundException(""))
      val boProducts = BOCartItemDao.getBOProducts(boCartItem)

      val expiredDate = boCartItem.dateCreated.plusDays(paramMaxDelay).toLocalDate

      if (storeCode == paramStoreCode &&
          (paramMaxDelay == 0 || !expiredDate.isBefore(DateTime.now().toLocalDate)) &&
          (paramMaxTimes == 0 || ConsumptionDao.countByBOProducts(boProducts) < paramMaxTimes)) {

        if (ConsumptionDao.createConsumption(boProducts)) {
          val file = new File(s"${Settings.ResourcesRootPath}/download/$paramSkuId")
          if (!file.exists()) {
            val parentFile = file.getParentFile()
            if (!parentFile.exists()) {
              parentFile.mkdirs()
            }
            DownloadableDao.load(storeCode, paramSkuId).map { content =>
              content.writeTo(new FileOutputStream(file))
            }
          }

          if (file.exists()) {
            val productName = ProductDao.get(storeCode, boProducts.find{p => p.principal}.get.productFk).map {p => p.name }.getOrElse(file.getName)
            (productName, file)
          }
          else throw new NotFoundException("")
        }
        else throw new NotFoundException("")
      }
      else throw new NotFoundException("")
    }
  }
}

object ConsumptionDao extends BoService {

  def countByBOProducts(boProducts: List[BOProduct])(implicit session: DBSession = AutoSession) : Int = {
    if (boProducts.isEmpty) 0
    else math.max(BOProductConsumptionSQL.countByBOProduct(boProducts.head), countByBOProducts(boProducts.tail))
  }

  def createConsumption(boProducts: List[BOProduct])(implicit session: DBSession) : Boolean = {
    if (boProducts.isEmpty) true
    else {
      val consumption = new Consumption(id = newId(),
        bOTicketTypeFk = None,
        xdate = DateTime.now(),
        dateCreated = DateTime.now(),
        lastUpdated = DateTime.now(),
        uuid = UUID.randomUUID().toString)

      ConsumptionSQL.create(consumption) &&
      BOProductConsumptionSQL.add(consumption, boProducts.head) &&
      createConsumption(boProducts.tail)
    }
  }

  private object BOProductConsumptionSQL extends SQLSyntaxSupport[BOProductConsumption] with BoService {

    override val tableName = "b_o_product_consumption"

    def countByBOProduct(boProduct: BOProduct)(implicit session: DBSession = AutoSession) : Int = {
      val o = BOProductConsumptionSQL.syntax("o")
      withSQL {
        select(count(o.consumptionId)).from(BOProductConsumptionSQL as o).where.eq(o.consumptionsFk, boProduct.id)
      }.map(_.int(1)).single.apply().get
    }

    def add(consumption: Consumption, boProduct: BOProduct)(implicit session: DBSession) : Boolean = {
      applyUpdate {
        insert.into(BOProductConsumptionSQL).namedValues(
          BOProductConsumptionSQL.column.consumptionsFk -> boProduct.id,
          BOProductConsumptionSQL.column.consumptionId -> consumption.id
        )
      } == 1
    }
  }

  private object ConsumptionSQL extends SQLSyntaxSupport[Consumption] with BoService {

    override val tableName = "consumption"

    def create(consumption: Consumption)(implicit session: DBSession) : Boolean = {
      applyUpdate {
        insert.into(ConsumptionSQL).namedValues(
          ConsumptionSQL.column.id -> consumption.id,
          ConsumptionSQL.column.bOTicketTypeFk -> consumption.bOTicketTypeFk,
          ConsumptionSQL.column.xdate -> consumption.xdate,
          ConsumptionSQL.column.dateCreated -> consumption.dateCreated,
          ConsumptionSQL.column.lastUpdated -> consumption.lastUpdated,
          ConsumptionSQL.column.uuid -> consumption.uuid
        )
      } == 1
    }

/*
  def apply(rn: ResultName[BOCart])(rs: WrappedResultSet): BOCart = BOCart(
    rs.get(rn.id),
    rs.get(rn.buyer),
    rs.get(rn.companyFk),
    rs.get(rn.currencyCode),
    rs.get(rn.currencyRate),
    rs.get(rn.xdate),
    rs.get(rn.dateCreated),
    rs.get(rn.lastUpdated),
    rs.get(rn.price),
    TransactionStatus.valueOf(rs.get(rn.status)),
    rs.get(rn.transactionUuid),
    rs.get(rn.uuid))

  def load(uuid: String): Option[BOCart] = {
    val t = BOCartDao.syntax("t")
    DB readOnly { implicit session =>
      withSQL {select.from(BOCartDao as t).where.eq(t.uuid, uuid)}.map(BOCartDao(t.resultName)).single().apply()
    }
  }

  def findByTransactionUuid(transactionUuid: String)(implicit session: DBSession = AutoSession) : Option[BOCart] = {
    val t = BOCartDao.syntax("t")
    withSQL {select.from(BOCartDao as t).where.eq(t.transactionUuid, transactionUuid)}.map(BOCartDao(t.resultName)).single().apply()
  }

  def updateStatus(boCart: BOCart) : Unit = {
    DB localTx { implicit session =>
      withSQL {
        update(BOCartDao).set(
          BOCartDao.column.status -> boCart.status.toString(),
          BOCartDao.column.transactionUuid -> boCart.transactionUuid,
          BOCartDao.column.lastUpdated -> DateTime.now
        ).where.eq(BOCartDao.column.id, boCart.id)
      }.update.apply()
    }
  }

  def create(buyer: String, companyId: Long, rate: Currency, price: Long)(implicit session: DBSession):BOCart = {

    val newBoCart = new BOCart(
      newId(),
      buyer,
      companyId,
      rate.code,
      rate.rate,
      DateTime.now,
      DateTime.now,
      DateTime.now,
      price,
      TransactionStatus.PENDING,
      None,
      UUID.randomUUID().toString
    )

    applyUpdate {
      insert.into(BOCartDao).namedValues(
        BOCartDao.column.id -> newBoCart.id,
        BOCartDao.column.buyer -> newBoCart.buyer,
        BOCartDao.column.companyFk -> newBoCart.companyFk,
        BOCartDao.column.currencyCode -> newBoCart.currencyCode,
        BOCartDao.column.currencyRate -> newBoCart.currencyRate,
        BOCartDao.column.xdate -> newBoCart.xdate,
        BOCartDao.column.dateCreated -> newBoCart.dateCreated,
        BOCartDao.column.lastUpdated -> newBoCart.lastUpdated,
        BOCartDao.column.price -> newBoCart.price,
        BOCartDao.column.status -> newBoCart.status.toString(),
        BOCartDao.column.transactionUuid -> newBoCart.transactionUuid,
        BOCartDao.column.uuid -> newBoCart.uuid
      )
    }

    newBoCart
  }

  def delete(boCart: BOCart)(implicit session: DBSession) = {
    withSQL {
      deleteFrom(BOCartDao).where.eq(BOCartDao.column.id,  boCart.id)
    }.update.apply()
  }
*/
  }
}
