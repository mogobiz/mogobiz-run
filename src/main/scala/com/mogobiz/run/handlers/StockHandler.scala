package com.mogobiz.run.handlers

import java.util.{UUID, Date}

import akka.actor.Props
import com.mogobiz.es.EsClient
import com.mogobiz.run.config.HandlersConfig._
import com.mogobiz.run.actors.EsUpdateActor.StockUpdateRequest
import com.mogobiz.run.actors.{EsUpdateActor, ActorSystemLocator}
import com.mogobiz.run.model.Mogobiz._
import com.mogobiz.run.model.{Stock => EsStock, StockByDateTime, StockCalendar}
import com.mogobiz.utils.GlobalUtil._
import com.sksamuel.elastic4s.ElasticDsl.{update => esupdate, insert => esinsert, _}
import com.sksamuel.elastic4s.IndexesTypesDsl
import org.joda.time.DateTime
import scalikejdbc._

/**
 * Handle the stock update in es
 */
class StockHandler extends IndexesTypesDsl {

  def checkStock(storeCode: String, product: Product, sku: Sku, quantity: Long, date:Option[DateTime]):Boolean = {
    val stockOpt = StockDao.findByProductAndSku(storeCode, product, sku)
    stockOpt match {
      case Some(stock) => DB localTx { implicit session =>
        // Search the corresponding StockCalendar
        val stockCalendarOpt = StockCalendarDao.findBySkuAndDate(sku, date)

        // stock verification
        return stock.stockUnlimited || stock.stockOutSelling || stockCalendarOpt.map {stockCalendar =>
          stockCalendar.stock >= (quantity + stockCalendar.sold)
        }.getOrElse(stock.initialStock >= quantity)
      }
      case None => return true;
    }
  }

  @throws[InsufficientStockException]
  def decrementStock(storeCode: String, product: Product, sku: Sku, quantity: Long, date:Option[DateTime]):Unit = {
    val stockOpt = StockDao.findByProductAndSku(storeCode, product, sku)
    stockOpt match {
      case Some(stock) => DB localTx { implicit session =>

        //In order to avoid locking on concurrents updates, we catch and retry 5 times until the update succeed else throw exception
        def doSelectUpdateStockOperation(retryTime: Int):Unit = {
          try {
            // Search the corresponding StockCalendar or create it if necessary
            val stockCalendarOpt = StockCalendarDao.findBySkuAndDate(sku, date)
            val stockCalendar = if (stockCalendarOpt.isDefined) stockCalendarOpt.get
            else StockCalendarDao.create(product, sku, stock, date)

            // stock verification
            if (!stock.stockUnlimited && !stock.stockOutSelling && stockCalendar.stock < (quantity + stockCalendar.sold)) {
              throw new InsufficientStockException("The available stock is insufficient for the quantity required")
            }

            // sold increment
            val newStockCalendar = StockCalendarDao.update(stockCalendar, quantity)

            // Mise à jour du stock dans ES via un actor pour ne pas bloquer la méthode
            fireUpdateEsStock(storeCode, product, sku, stock, newStockCalendar)

          } catch {
            case e: ConcurrentUpdateStockException =>
              if(retryTime>5) throw new ConcurrentUpdateStockException("update error while decrementing stock")
              else doSelectUpdateStockOperation(retryTime + 1)
          }
        }

        doSelectUpdateStockOperation(0)
      }
      case None => // Pas de stock à gérer
    }
  }

  def incrementStock(storeCode: String, product: Product, sku: Sku, quantity: Long, date:Option[DateTime])(implicit session: DBSession):Unit = {
    val stockOpt = StockDao.findByProductAndSku(storeCode, product, sku)
    stockOpt match {
      case Some(stock) =>

        def doSelectUpdateStockOperation(retryTime: Int): Unit = {
          try {
            // Search the corresponding StockCalendar or create it if necessary
            val stockCalendarOpt = StockCalendarDao.findBySkuAndDate(sku, date)
            val stockCalendar = if (stockCalendarOpt.isDefined) stockCalendarOpt.get
            else StockCalendarDao.create(product, sku, stock, date)

            // sold increment
            val newStockCalendar = StockCalendarDao.update(stockCalendar, -quantity)

            // Mise à jour du stock dans ES via un actor pour ne pas bloquer la méthode
            fireUpdateEsStock(storeCode, product, sku, stock, newStockCalendar)
          } catch {
            case e: ConcurrentUpdateStockException =>
              if (retryTime > 5) throw new ConcurrentUpdateStockException("update error while incrementing stock")
              else doSelectUpdateStockOperation(retryTime + 1)
          }
        }

        doSelectUpdateStockOperation(0)

      case None => // Pas de stock à gérer
    }
  }

  private def fireUpdateEsStock(storeCode: String, product: Product, sku: Sku, stock: EsStock, stockCalendar: StockCalendar) = {
    val system = ActorSystemLocator.get
    val stockActor = system.actorOf(Props[EsUpdateActor])
    stockActor ! StockUpdateRequest(storeCode, product, sku, stock, stockCalendar)
  }

  def updateEsStock(storeCode: String, product: Product, sku: Sku, stock: EsStock, stockCalendar:StockCalendar): Unit = {
    if (stockCalendar.startDate.isEmpty) {
      // Stock produit (sans date)
      val script = s"ctx._source.stock = ctx._source.initialStock - ${stockCalendar.sold}"
      val req = esupdate id stock.uuid in s"$storeCode/stock" script script retryOnConflict 4
      EsClient().execute(req).await.getGetResult
    }
    else {
      val esStockCalendarOpt = stock.stockByDateTime.getOrElse(Seq()).find(_.uuid == stockCalendar.uuid)
      esStockCalendarOpt match {
        case Some(esStockCalendar) =>
          // Mise à jour du StockCalendar existant
          val newESStockCalendar = esStockCalendar.copy(stock = stockCalendar.stock - stockCalendar.sold, lastUpdated = new Date)
          val newStockByDateTime = stock.stockByDateTime.getOrElse(Seq()).map(s =>
            if (s.uuid == stockCalendar.uuid) newESStockCalendar
            else s
          )
          StockDao.update(storeCode, stock.copy(stockByDateTime = Some(newStockByDateTime)))
        case _ =>
          // Ajout d'un nouveau StockCalendar
          val newESStockCalendar = StockByDateTime(
            stockCalendar.id,
            stockCalendar.uuid,
            dateCreated = stockCalendar.dateCreated.toDate,
            lastUpdated = stockCalendar.lastUpdated.toDate,
            startDate = stockCalendar.startDate.get,
            stock = stockCalendar.stock - stockCalendar.sold
          )
          val newStockByDateTime = stock.stockByDateTime.getOrElse(Seq()) :+ newESStockCalendar
          StockDao.update(storeCode, stock.copy(stockByDateTime = Some(newStockByDateTime)))
      }
    }
  }
}

object StockDao {

  def findByProductAndSku(storeCode: String, product: Product, sku: Sku) : Option[EsStock] = {
    // Création de la requête
    val req = search in storeCode -> "stock" postFilter and(
      termFilter("stock.productId", product.id),
      termFilter("stock.id", sku.id)
    )

    // Lancement de la requête
    EsClient.search[EsStock](req);
  }

  def update(storeCode: String, stock: EsStock) = {
    EsClient.update[EsStock](storeCode, stock, "stock", true, false)
  }
}

object StockCalendarDao extends SQLSyntaxSupport[StockCalendar] with BoService {

  override val tableName = "stock_calendar"

  def apply(rs: WrappedResultSet) = StockCalendar(
    rs.get("id"),
    rs.get("date_created"),
    rs.get("last_updated"),
    rs.get("product_fk"),
    rs.get("sold"),
    rs.get("start_date"),
    rs.get("stock"),
    rs.get("ticket_type_fk"),
    rs.get("uuid")
  )

  def findBySkuAndDate(sku: Sku, date:Option[DateTime])(implicit session: DBSession) : Option[StockCalendar] = {
    val req = if (date.isDefined) forUpdateHandler.selectStockCalendarBySkuAndDate(sku, date.get)
    else forUpdateHandler.selectStockCalendarBySkuAndNullDate(sku)

    req.map(rs => StockCalendarDao(rs)).single.apply()
  }

  def create(product:Product, sku: Sku, stock:EsStock, date:Option[DateTime])(implicit session: DBSession) : StockCalendar = {
    val newStockCalendar = new StockCalendar(
      newId(),
      DateTime.now,
      DateTime.now,
      product.id,
      0,
      date,
      stock.initialStock,
      sku.id,
      UUID.randomUUID().toString)

    applyUpdate {
      insert.into(StockCalendarDao).namedValues(
        StockCalendarDao.column.id -> newStockCalendar.id,
        StockCalendarDao.column.dateCreated -> newStockCalendar.dateCreated,
        StockCalendarDao.column.lastUpdated -> newStockCalendar.lastUpdated,
        StockCalendarDao.column.productFk -> newStockCalendar.productFk,
        StockCalendarDao.column.sold -> newStockCalendar.sold,
        StockCalendarDao.column.startDate -> newStockCalendar.startDate,
        StockCalendarDao.column.stock -> newStockCalendar.stock,
        StockCalendarDao.column.ticketTypeFk -> newStockCalendar.ticketTypeFk,
        StockCalendarDao.column.uuid -> newStockCalendar.uuid
      )
    }

    newStockCalendar
  }

  def update(stockCalendar:StockCalendar, addQuantity: Long)(implicit session:DBSession) : StockCalendar = {

    val newStockCalendar = stockCalendar.copy(sold = Math.max(stockCalendar.sold + addQuantity, 0))
    val res = sql"update stock_calendar set sold = ${newStockCalendar.sold},last_updated = $now where id=${stockCalendar.id} and last_updated = ${stockCalendar.lastUpdated}".update().apply()
    if(res==0)
      throw new ConcurrentUpdateStockException()

    newStockCalendar
  }

}