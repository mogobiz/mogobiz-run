/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import akka.actor.Props
import com.mogobiz.es.EsClient
import com.mogobiz.json.JacksonConverter
import com.mogobiz.run.actors.EsUpdateActor
import com.mogobiz.run.actors.EsUpdateActor.SalesUpdateRequest
import com.mogobiz.run.model.Mogobiz.{ Product, Sku }
import com.mogobiz.run.model.SaleChange
import com.mogobiz.system.ActorSystemLocator
import com.sksamuel.elastic4s.ElasticDsl.{ update => esupdate, _ }
import com.sksamuel.elastic4s.source.DocumentSource
import org.joda.time.DateTime
import scalikejdbc._

class SalesHandler {

  def incrementSales(indexEs: String, product: Product, sku: Sku, quantity: Long)(implicit session: DBSession = AutoSession): SaleChange = {
    val nbProductSales = sql"select nb_sales from product where id=${product.id}".map(rs => rs.long("nb_sales")).single().apply().get
    val newNbProductSales = nbProductSales + quantity
    sql"update product set nb_sales = ${newNbProductSales},last_updated = ${DateTime.now} where id=${product.id}".update().apply()

    val nbSkuSales = sql"select nb_sales from ticket_type where id=${sku.id}".map(rs => rs.long("nb_sales")).single().apply().get
    val newNbSkuSales = nbSkuSales + quantity
    sql"update ticket_type set nb_sales = ${newNbSkuSales},last_updated = ${DateTime.now} where id=${sku.id}".update().apply()

    SaleChange(indexEs, product, sku, newNbProductSales, newNbSkuSales)
  }

  def fireUpdateEsSales(storeCode: String, product: Product, sku: Sku, newNbProductSales: Long, newNbSkuSales: Long) = {
    val system = ActorSystemLocator()
    val stockActor = system.actorOf(Props[EsUpdateActor])
    stockActor ! SalesUpdateRequest(storeCode, product, sku, newNbProductSales, newNbSkuSales)
  }

  /**
   * update the product and sku nbSales
   */
  def update(indexEs: String, product: Product, sku: Sku, newNbProductSales: Long, newNbSkuSales: Long) = {
    val newSkus = product.skus.map(s => if (s.id == sku.id) sku.copy(nbSales = newNbSkuSales) else s)
    val newProduct = product.copy(nbSales = newNbProductSales, skus = newSkus)
    val js = JacksonConverter.serialize(newProduct)
    val req = esupdate id product.id in indexEs -> "product" doc new DocumentSource {
      override def json: String = js
    }
    EsClient().execute(req)
  }

}
