/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.actors

import akka.actor.Actor
import com.mogobiz.run.actors.EsUpdateActor._
import com.mogobiz.run.config.MogobizHandlers.handlers._
import com.mogobiz.run.model.Mogobiz.{ Product, Sku }
import com.mogobiz.run.model.{ Stock, StockCalendar }

/**
 * Actor in charge of every updates operations on ES data
 */
object EsUpdateActor {

  case class StockUpdateRequest(indexEs: String, product: Product, sku: Sku, stock: Stock, stockCalendar: StockCalendar)

  case class SalesUpdateRequest(indexEs: String, product: Product, sku: Sku, newNbProductSales: Long, newNbSkuSales: Long)

  case class ProductNotationsUpdateRequest(indexEs: String, productId: Long)

  case class SkuStockAvailabilityUpdateRequest(indexEs: String, product: Product, sku: Sku, stock: Stock, stockCalendar: StockCalendar)

  case class ProductStockAvailabilityUpdateRequest(indexEs: String, productId: Long)
}

class EsUpdateActor extends Actor {

  def receive = {
    case q: StockUpdateRequest =>
      stockHandler.updateEsStock(q.indexEs, q.product, q.sku, q.stock, q.stockCalendar)
      context.stop(self)

    case q: SalesUpdateRequest =>
      salesHandler.update(q.indexEs, q.product, q.sku, q.newNbProductSales, q.newNbSkuSales)
      context.stop(self)

    case q: ProductNotationsUpdateRequest =>
      val notations = facetHandler.getCommentNotations(q.indexEs, Some(q.productId))
      productHandler.updateProductNotations(q.indexEs, q.productId, notations)
      context.stop(self)

    case q: SkuStockAvailabilityUpdateRequest =>
      skuHandler.updateStockAvailability(q.indexEs, q.sku, q.stock, q.stockCalendar)
      context.stop(self)

    case q: ProductStockAvailabilityUpdateRequest =>
      //then query skus stock availability filtered on productId
      val skusAvailable = skuHandler.existsAvailableSkus(q.indexEs, q.productId)

      productHandler.updateStockAvailability(q.indexEs, q.productId, skusAvailable)
      /*
    if(!skusAvailable){
      //if no skus returned, then updateStockProductAvailability
      productHandler.updateStockAvailability(q.storeCode, q.product.id,false)
    }*/
      context.stop(self)

  }
}
