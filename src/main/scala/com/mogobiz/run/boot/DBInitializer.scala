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
  def apply(): Unit = try {
    EsClient.client.sync.execute(create index Settings.cart.EsIndex)
    Mapping.set()
    fillDB()
    println(s"Index ${Settings.cart.EsIndex} has been created.")
  } catch {
    case e: RemoteTransportException if e.getCause().isInstanceOf[IndexAlreadyExistsException] =>
      println(s"Index ${Settings.cart.EsIndex} was not created because it already exists.")
    case e: Throwable => e.printStackTrace()
  }

  private def fillDB(): Unit = {
    // Nothing to add into DB
  }
}
