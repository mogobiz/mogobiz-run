package com.mogobiz.run.handlers

import com.mogobiz.es.EsClient
import com.mogobiz.json.JsonUtil
import com.mogobiz.run.model.Mogobiz.Sku
import com.sksamuel.elastic4s.ElasticDsl._
import org.elasticsearch.search.SearchHit
import org.json4s.JsonAST.{JArray, JValue}

/**
 * Created by yoannbaudy on 19/11/2014.
 */

object SkuDao extends JsonUtil {

  /**
   * Retrieve a sku with the given id and the given storeCode
   * @param storeCode
   * @param id
   * @return
   */
  def get(storeCode: String, id: Long) : Option[Sku] = {
    import com.mogobiz.run.implicits.Es4Json.searchHit2JValue
    import com.mogobiz.run.implicits.Json4sProtocol.json4sFormats

    // Création de la requête
    val req = search in storeCode -> "product" filter
      termFilter("product.skus.id", id) sourceInclude("skus.*")

    // Lancement de la requête
    val esResult : Option[SearchHit] = EsClient.searchRaw(req);

    // Extract du sku
    esResult match {
      case Some(searchHit: SearchHit) => {
        val product : JValue = searchHit
        product \ "skus" match {
          case JArray(skus) => {
            skus.map(sku => sku.extract[Sku]).find(sku => sku.id == id)
          }
          case _ => None
        }
      }
      case _ => None
    }
  }
}
