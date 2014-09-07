package com.mogobiz.es

import java.util.{Calendar, Date}

import com.mogobiz.config.Settings
import com.mogobiz.utils.JacksonConverter
import com.sksamuel.elastic4s.{MultiGetDefinition, ElasticClient, GetDefinition}
import com.sksamuel.elastic4s.source.DocumentSource
import org.elasticsearch.action.get.{MultiGetItemResponse, GetResponse}
import org.elasticsearch.common.settings.ImmutableSettings
import com.sksamuel.elastic4s.ElasticDsl.{index => esindex4s, delete => esdelete4s, update => esupdate4s, _}
import org.elasticsearch.search.{SearchHits, SearchHit}
import org.json4s.JsonAST.{JValue, JArray}
import org.json4s.native.JsonMethods._

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

  def index[T: Manifest](_store:String=Settings.DB.Index, t: T): String = {
    val js = JacksonConverter.serialize(t)
    val req = esindex4s into(_store, manifest[T].runtimeClass.getSimpleName.toLowerCase) doc new DocumentSource {
      override val json: String = js
    }
    val res = EsClient().execute(req)
    res.getId
  }

  def load[T: Manifest](_store:String=Settings.DB.Index, _uuid: String): Option[T] = {
    val req = get id _uuid from _store -> manifest[T].runtimeClass.getSimpleName.toLowerCase
    val res = EsClient().execute(req)
    if (res.isExists) Some(JacksonConverter.deserialize[T](res.getSourceAsString)) else None
  }

  def loadRaw(req:GetDefinition): Option[GetResponse] = {
    val res = EsClient().execute(req)
    if (res.isExists) Some(res) else None
  }

  def loadRaw(req:MultiGetDefinition): Array[MultiGetItemResponse] = {
    EsClient().execute(req).getResponses
  }

  def loadWithVersion[T: Manifest](uuid: String): Option[(T, Long)] = {
    val req = get id uuid from Settings.DB.Index -> manifest[T].runtimeClass.getSimpleName
    val res = EsClient().execute(req)
    val maybeT = if (res.isExists) Some(JacksonConverter.deserialize[T](res.getSourceAsString)) else None
    maybeT map ((_, res.getVersion))
  }

  def delete[T: Manifest](uuid: String, refresh: Boolean = false): Boolean = {
    val req = esdelete4s id uuid from Settings.DB.Index -> manifest[T].runtimeClass.getSimpleName refresh refresh
    val res = EsClient().execute(req)
    res.isFound
  }

  def update[T <: Timestamped : Manifest](t: T, upsert: Boolean = true, refresh: Boolean = false): Boolean = {
    val now = Calendar.getInstance().getTime
    t.lastUpdated = now
    val js = JacksonConverter.serialize(t)
    val req = esupdate4s id t.uuid in Settings.DB.Index -> manifest[T].runtimeClass.getSimpleName refresh refresh doc new DocumentSource {
      override val json: String = js
    }
    req.docAsUpsert(upsert)
    val res = EsClient().execute(req)
    res.isCreated
  }

  def update[T <: Timestamped : Manifest](t: T, version: Long): Boolean = {
    val now = Calendar.getInstance().getTime
    t.lastUpdated = now
    val js = JacksonConverter.serialize(t)
    val req = esupdate4s id t.uuid in Settings.DB.Index -> manifest[T].runtimeClass.getSimpleName version version doc new DocumentSource {
      override def json: String = js
    }
    val res = EsClient().execute(req)
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

  def searchAllRaw(req: SearchDefinition): SearchHits = {
    val res = EsClient().execute(req)
    res.getHits
  }

  def searchRaw(req: SearchDefinition): Option[SearchHit] = {
    val res = EsClient().execute(req)
    if (res.getHits.getTotalHits == 0)
      None
    else
      Some(res.getHits.getHits()(0))
  }

  implicit def hits2JArray(hits:Array[SearchHit]) : JArray = JArray(hits.map(hit => parse(hit.getSourceAsString)).toList)

  implicit def hit2JValue(hit:SearchHit) : JValue = parse(hit.getSourceAsString)

  implicit def response2JValue(response:GetResponse) : JValue = parse(response.getSourceAsString)
}
