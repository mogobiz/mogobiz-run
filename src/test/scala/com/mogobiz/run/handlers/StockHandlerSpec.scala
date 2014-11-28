package com.mogobiz.run.handlers

import com.mogobiz.MogobizRouteTest
import com.mogobiz.run.config.MogobizDBsWithEnv
import com.mogobiz.run.model.Mogobiz.{Product, Sku, InsufficientStockException}
import com.mogobiz.run.model.Stock
import org.joda.time.DateTime
import scalikejdbc._

class StockHandlerSpec extends MogobizRouteTest {

  MogobizDBsWithEnv("test").setupAll()

  val stockHandler = new StockHandler

  "StockHandler" should {
    "increment stock without date" in {
      DB localTx { implicit session =>
        val productAndSku = ProductDao.getProductAndSku(STORE, 93) // Pull Nike
        val product = productAndSku.get._1
        val sku = productAndSku.get._2
        val date = None
        val stock = StockDao.findByProductAndSku(STORE, product, sku).get

        // On simule un décrément
        simulateDecrement(product, sku, stock, date, 2)

        val remainedStock = extractRemainedStock(product.id, sku.id, date)
        DB localTx { implicit session =>
          stockHandler.incrementStock(STORE, product, sku, 2, date)
        }

        val newRemainedStock = extractRemainedStock(product.id, sku.id, date)
        newRemainedStock must beSome(remainedStock.getOrElse(stock.initialStock) + 2)
      }
    }

    "increment stock with date" in {
      val productAndSku = ProductDao.getProductAndSku(STORE, 144) // Tonton flingueur
      val product = productAndSku.get._1
      val sku = productAndSku.get._2
      val date = Some(DateTime.now().withHourOfDay(0).withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0))
      val stock = StockDao.findByProductAndSku(STORE, product, sku).get

      // On simule un décrément
      simulateDecrement(product, sku, stock, date, 2)

      val remainedStock = extractRemainedStock(product.id, sku.id, date)
      DB localTx { implicit session =>
        stockHandler.incrementStock(STORE, product, sku, 2, date)
      }

      val newRemainedStock = extractRemainedStock(product.id, sku.id, date)
      newRemainedStock must beSome(remainedStock.getOrElse(stock.initialStock) + 2)
    }

    "decrement stock without date" in {
      DB localTx { implicit session =>
        val productAndSku = ProductDao.getProductAndSku(STORE, 93) // Pull Nike
        val product = productAndSku.get._1
        val sku = productAndSku.get._2
        val date = None
        val stock = StockDao.findByProductAndSku(STORE, product, sku).get

        DB localTx { implicit session =>
          // On incrémente pour êtes sûr d'avoir des stocks en base
          stockHandler.incrementStock(STORE, product, sku, 2, date)
        }

        val remainedStock = extractRemainedStock(product.id, sku.id, date)
        DB localTx { implicit session =>
          stockHandler.decrementStock(STORE, product, sku, 2, date)
        }

        val newRemainedStock = extractRemainedStock(product.id, sku.id, date)
        newRemainedStock must beSome(remainedStock.getOrElse(stock.initialStock) - 2)
      }
    }

    "decrement stock with date" in {
      DB localTx { implicit session =>
        val productAndSku = ProductDao.getProductAndSku(STORE, 144) // Tonton flingueur
        val product = productAndSku.get._1
        val sku = productAndSku.get._2
        val date = Some(DateTime.now().withHourOfDay(0).withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0))
        val stock = StockDao.findByProductAndSku(STORE, product, sku).get

        DB localTx { implicit session =>
          // On incrémente pour êtes sûr d'avoir des stocks en base
          stockHandler.incrementStock(STORE, product, sku, 2, date)
        }

        val remainedStock = extractRemainedStock(product.id, sku.id, date)
        DB localTx { implicit session =>
          stockHandler.decrementStock(STORE, product, sku, 2, date)
        }

        val newRemainedStock = extractRemainedStock(product.id, sku.id, date)
        newRemainedStock must beSome(remainedStock.getOrElse(stock.initialStock) - 2)
      }
    }

    "throw exception during decrement if insufficient stock" in {
      DB localTx { implicit session =>
        val productAndSku = ProductDao.getProductAndSku(STORE, 93) // Pull Nike
        val product = productAndSku.get._1
        val sku = productAndSku.get._2
        val date = None

        val remainedStock = extractRemainedStock(product.id, sku.id, date)
        DB localTx { implicit session =>
          stockHandler.decrementStock(STORE, product, sku, remainedStock.getOrElse(0L) + 1, date) must throwA[InsufficientStockException]
        }
      }
    }
  }

  private def simulateDecrement(product: Product, sku: Sku, stock: Stock, date: Option[DateTime], quantity: Long) = {
    DB localTx { implicit session =>
      val stockCalendarOpt = StockCalendarDao.findBySkuAndDate(sku, date)
      if (stockCalendarOpt.isEmpty) {
        // on crée la ligne et on modifie le sold pour considéré qu'on a fait le décrément
        val stockCalendar = StockCalendarDao.create(product, sku, stock, date)
        StockCalendarDao.update(stockCalendar, quantity)
      }
    }
  }

  private def extractRemainedStock(productId: Long, skuId: Long, dateOpt:Option[DateTime]) : Option[Long] = {
    DB localTx { implicit session =>
      dateOpt match {
        case Some(date) => sql""" select s.stock - s.sold from stock_calendar s where s.product_fk=${productId} and s.ticket_type_fk=${skuId} and s.start_date = ${date} """.map(rs => rs.long(1)).first().apply()
        case _ => sql""" select s.stock - s.sold from stock_calendar s where s.product_fk=${productId} and s.ticket_type_fk=${skuId} and s.start_date is null """.map(rs => rs.long(1)).first().apply()
      }
    }
  }
}
