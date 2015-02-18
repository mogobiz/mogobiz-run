package com.mogobiz.run.boot

import com.mogobiz.es.EsClient
import com.mogobiz.run.config.Settings
import com.mogobiz.run.es.Mapping
import com.sksamuel.elastic4s.ElasticDsl._
import org.elasticsearch.indices.IndexAlreadyExistsException
import org.elasticsearch.transport.RemoteTransportException

/**
 * Created by yoannbaudy on 12/11/2014.
 */
object DBInitializer {
  def apply(): Unit = {
    def createIndex(indexName: String) : Boolean = {
      try {
        EsClient.client.sync.execute(create index indexName)
        println(s"Index $indexName has been created.")
        return true
      } catch {
        case e: RemoteTransportException if e.getCause().isInstanceOf[IndexAlreadyExistsException] =>
          println(s"Index $indexName was not created because it already exists.")
        case e: Throwable => e.printStackTrace()
      }
      return false
    }

    val backofficeIndexCreated = createIndex(Settings.backoffice.EsIndex)
    val cartIndexCreated = createIndex(Settings.cart.EsIndex)
    try {
      //if (backofficeIndexCreated) Mapping.set(List("BOCart"));
      if (cartIndexCreated) Mapping.set(List("StoreCart"));
    }
    catch {
      case e: Throwable => e.printStackTrace()
    }
  }
}
