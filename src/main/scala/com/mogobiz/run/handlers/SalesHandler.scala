package com.mogobiz.run.handlers

import com.mogobiz.run.es.EsClientOld
import EsClientOld._
import com.mogobiz.run.es.EsClientOld
import com.sksamuel.elastic4s.ElasticDsl.{update => esupdate4s}
import com.sksamuel.elastic4s.ElasticDsl._
import org.json4s.JsonAST.{JObject, JArray, JInt}
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization._
import com.mogobiz.run.implicits.JsonSupport._

class SalesHandler {

  /**
   * update the product nbSales only with a script
   * @param storeCode
   * @param uuid
   * @param nbSales
   * @return
   */
  def updateProductSales(storeCode: String, uuid: String , nbSales : Long) = {
    val script = "ctx._source.nbSales = nbSales"
    val req = esupdate4s id uuid in s"${storeCode}/product" script script params {"nbSales" -> nbSales} retryOnConflict 4
    EsClientOld.updateRaw(req)
  }

  /**
   * update the product and sku nbSales
   * @param storeCode
   * @param productId
   * @param nbSalesProduct
   * @param idSku
   * @param nbSalesSku
   */
  def update(storeCode: String, productId: Long, nbSalesProduct : Long, idSku: Long, nbSalesSku: Long) = {

    val res = EsClientOld.loadRaw(get(productId) from storeCode -> "product").get
    val v1 = res.getVersion
    val product = response2JValue(res)

    val skus = (product \ "skus").children
    val sku = skus.find(sku => (sku\"id")==JInt(idSku)).get
    val updatedSku = sku merge parse(write(Map("nbSales" -> nbSalesSku)))

    val updatedSkus = skus.map{ sku =>
      if((sku\"id")==JInt(idSku))
          updatedSku
      else sku
    }

    val updatedProduct = (product removeField { f => f._1 =="skus"} ) merge parse(write(Map("nbSales" -> nbSalesProduct))) merge JObject(("skus",JArray(updatedSkus)))

    val res2 = EsClientOld.updateRaw(esupdate4s id productId in storeCode -> "product" doc updatedProduct)
    //res2.getVersion > v1
  }

}
