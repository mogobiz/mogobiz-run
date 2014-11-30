package com.mogobiz.run.actors

import akka.actor.Actor
import com.mogobiz.run.actors.EsUpdateActor._
import com.mogobiz.run.config.HandlersConfig
import HandlersConfig._
import com.mogobiz.run.model.Mogobiz.{Product, Sku}
import com.mogobiz.run.model.{Stock, StockCalendar}

/**
 * Actor in charge of every updates operations on ES data
 */
object EsUpdateActor {

  case class StockUpdateRequest(storeCode: String, product: Product, sku: Sku, stock: Stock, stockCalendar:StockCalendar)

  case class SalesUpdateRequest(storeCode: String, product: Product, sku: Sku, newNbProductSales : Long, newNbSkuSales: Long)

  case class ProductNotationsUpdateRequest(storeCode: String, productId: Long)
}

class EsUpdateActor extends Actor {

  def receive = {
    case q: StockUpdateRequest =>
      stockHandler.updateEsStock(q.storeCode,  q.product, q.sku, q.stock, q.stockCalendar)
      context.stop(self)

    case q: SalesUpdateRequest =>
      salesHandler.update(q.storeCode, q.product, q.sku, q.newNbProductSales, q.newNbSkuSales)
      context.stop(self)

    case q: ProductNotationsUpdateRequest =>
      val notations = facetHandler.getCommentNotations(q.storeCode, Some(q.productId))
      productHandler.updateProductNotations(q.storeCode, q.productId, notations)
      context.stop(self)

  }
}
