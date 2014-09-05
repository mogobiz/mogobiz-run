package com.mogobiz.es

import java.util.{Calendar, Date}

import com.mogobiz.config.Settings
import com.mogobiz.utils.JacksonConverter
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.source.DocumentSource
import org.elasticsearch.common.settings.ImmutableSettings
import com.sksamuel.elastic4s.ElasticDsl.{delete => esdelete4s, update => esupdate4s, _}
import org.elasticsearch.search.SearchHit

object EsClient {
  val settings = ImmutableSettings.settingsBuilder().put("cluster.name", Settings.DB.EsCluster).build()
  val client = ElasticClient.remote(settings, (Settings.DB.EsHost, Settings.DB.EsPort))

  def apply() = {
    client.sync
  }

  type Timestamped = {
    val uuid: String
    var lastUpdated: Date
    var dateCreated: Date
  }

  def index[T <: Timestamped : Manifest](t: T, refresh: Boolean = true): String = {
    val now = Calendar.getInstance().getTime
    t.dateCreated = now
    t.lastUpdated = now
    val json = JacksonConverter.serialize(t)
    val res = client.client.prepareIndex(Settings.DB.Index, manifest[T].runtimeClass.getSimpleName, t.uuid)
      .setSource(json)
      .setRefresh(refresh)
      .execute()
      .actionGet()
    res.getId
  }

  def load[T: Manifest](uuid: String): Option[T] = {
    val req = get id uuid from Settings.DB.Index -> manifest[T].runtimeClass.getSimpleName
    val res = client.sync.execute(req)
    if (res.isExists) Some(JacksonConverter.deserialize[T](res.getSourceAsString)) else None
  }

  def loadWithVersion[T: Manifest](uuid: String): Option[(T, Long)] = {
    val req = get id uuid from Settings.DB.Index -> manifest[T].runtimeClass.getSimpleName
    val res = client.sync.execute(req)
    val maybeT = if (res.isExists) Some(JacksonConverter.deserialize[T](res.getSourceAsString)) else None
    maybeT map ((_, res.getVersion))
  }

  def delete[T: Manifest](uuid: String, refresh: Boolean = true): Boolean = {
    val req = esdelete4s id uuid from Settings.DB.Index -> manifest[T].runtimeClass.getSimpleName refresh refresh
    val res = client.sync.execute(req)
    res.isFound
  }

  def update[T <: Timestamped : Manifest](t: T, upsert: Boolean = true, refresh: Boolean = true): Boolean = {
    val now = Calendar.getInstance().getTime
    t.lastUpdated = now
    val js = JacksonConverter.serialize(t)
    val req = esupdate4s id t.uuid in Settings.DB.Index -> manifest[T].runtimeClass.getSimpleName refresh refresh doc new DocumentSource {
      override def json: String = js
    }
    req.docAsUpsert(upsert)
    val res = client.sync.execute(req)
    res.isCreated
  }

  def update[T <: Timestamped : Manifest](t: T, version: Long): Boolean = {
    val now = Calendar.getInstance().getTime
    t.lastUpdated = now
    val js = JacksonConverter.serialize(t)
    val req = esupdate4s id t.uuid in Settings.DB.Index -> manifest[T].runtimeClass.getSimpleName version version doc new DocumentSource {
      override def json: String = js
    }
    val res = client.sync.execute(req)
    true
  }

  def searchAll[T: Manifest](req: SearchDefinition): Seq[T] = {
    val res = EsClient().execute(req)
    res.getHits.getHits.map { hit => JacksonConverter.deserialize[T](hit.getSourceAsString)}
  }

  def search[T: Manifest](req: SearchDefinition): Option[T] = {
    val res = EsClient().execute(req)
    if (res.getHits.getTotalHits == 0)
      None
    else
      Some(JacksonConverter.deserialize[T](res.getHits.getHits()(0).getSourceAsString))
  }

  def searchAllRaw(req: SearchDefinition): Array[SearchHit] = {
    val res = EsClient().execute(req)
    res.getHits.getHits
  }

  def searchRaw(req: SearchDefinition): Option[SearchHit] = {
    val res = EsClient().execute(req)
    if (res.getHits.getTotalHits == 0)
      None
    else
      Some(res.getHits.getHits()(0))
  }
}
