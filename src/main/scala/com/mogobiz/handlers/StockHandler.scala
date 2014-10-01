package com.mogobiz.handlers

import com.mogobiz.es.EsClient
import com.sksamuel.elastic4s.ElasticDsl.{update => esupdate4s,search => esearch4s, _}
import com.sksamuel.elastic4s.ElasticDsl._

/**
 * Handle the stock update in es
 */
class StockHandler {

  def update(storeCode: String, uuid:String, stockValue: Long) = {
    println("update ES stock")

    val script = "ctx._source.stock = stock"
    val req = esupdate4s id uuid in s"${storeCode}/stock" script script params {"stock" -> s"$stockValue"} retryOnConflict 4
    EsClient.updateRaw(req)

  }
}
