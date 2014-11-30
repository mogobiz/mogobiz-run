package com.mogobiz.run.handlers

import akka.actor.Props
import com.mogobiz.es.EsClient
import com.mogobiz.json.JacksonConverter
import com.sksamuel.elastic4s.ElasticDsl.{update => esupdate, _}
import com.mogobiz.run.actors.EsUpdateActor.SalesUpdateRequest
import com.mogobiz.run.actors.{EsUpdateActor, ActorSystemLocator}
import com.mogobiz.run.model.Mogobiz.{Sku, Product}
import com.sksamuel.elastic4s.source.DocumentSource
import org.joda.time.DateTime
import scalikejdbc._

class SalesHandler {

  def incrementSales(storeCode: String, product: Product, sku: Sku, quantity: Long):Unit = {
    DB localTx { implicit session =>
      val nbProductSales = sql"select nb_sales from product where id=${product.id}".map(rs => rs.long("nb_sales")).single().apply().get
      val newNbProductSales = nbProductSales + quantity
      sql"update product set nb_sales = ${newNbProductSales},last_updated = ${DateTime.now} where id=${product.id}".update().apply()

      val nbSkuSales = sql"select nb_sales from ticket_type where id=${sku.id}".map(rs => rs.long("nb_sales")).single().apply().get
      val newNbSkuSales = nbSkuSales + quantity
      sql"update ticket_type set nb_sales = ${newNbSkuSales},last_updated = ${DateTime.now} where id=${sku.id}".update().apply()

      fireUpdateEsSales(storeCode, product, sku, newNbProductSales, newNbSkuSales)
    }
  }

  private def fireUpdateEsSales(storeCode: String, product: Product, sku: Sku, newNbProductSales : Long, newNbSkuSales: Long) = {
    val system = ActorSystemLocator.get
    val stockActor = system.actorOf(Props[EsUpdateActor])
    stockActor ! SalesUpdateRequest(storeCode, product, sku, newNbProductSales, newNbSkuSales)
  }

  /**
   * update the product and sku nbSales
   */
  def update(storeCode: String, product: Product, sku: Sku, newNbProductSales : Long, newNbSkuSales: Long) = {
    val newSkus = product.skus.map(s => if (s.id == sku.id) sku.copy(nbSales = newNbSkuSales) else s)
    val newProduct = product.copy(nbSales = newNbProductSales, skus = newSkus)
    val js = JacksonConverter.serialize(newProduct)
    val req = esupdate id product.id in storeCode -> "product" doc new DocumentSource{
      override def json: String = js
    }
    EsClient().execute(req)
  }

}
