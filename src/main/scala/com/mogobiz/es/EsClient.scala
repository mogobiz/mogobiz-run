package com.mogobiz.es

import java.util.{Calendar, Date}

import com.mogobiz.config.Settings._
import com.mogobiz.utils.JacksonConverter
import com.sksamuel.elastic4s.ElasticDsl.{delete => esdelete4s, index => esindex4s, update => esupdate4s, _}
import com.sksamuel.elastic4s.source.DocumentSource
import com.sksamuel.elastic4s.{ElasticClient, GetDefinition, MultiGetDefinition}
import com.typesafe.scalalogging.slf4j.Logger
import org.elasticsearch.action.get.{GetResponse, MultiGetItemResponse}
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.common.xcontent.{ToXContent, XContentFactory}
import org.elasticsearch.index.get.GetResult
import org.elasticsearch.search.{SearchHit, SearchHits}
import org.json4s.JsonAST.{JArray, JValue}
import org.json4s.native.JsonMethods._
import org.slf4j.LoggerFactory

import scala.language.{implicitConversions, reflectiveCalls}

object EsClient {
  private val logger = Logger(LoggerFactory.getLogger("esClient"))

  val settings = ImmutableSettings.settingsBuilder().put("cluster.name", EsCluster).build()
  val client = ElasticClient.remote(settings, (EsHost, EsPort))

  def apply() = {
    client.sync
  }

  type Timestamped = {
    val uuid: String
    var lastUpdated: Date
    var dateCreated: Date
  }

  def index[T: Manifest](store: String, t: T): String = {
    val js = JacksonConverter.serialize(t)
    val req = esindex4s into(store, manifest[T].runtimeClass.getSimpleName.toLowerCase) doc new DocumentSource {
      override val json: String = js
    }
    val res = EsClient().execute(req)
    res.getId
  }

  def load[T: Manifest](store: String, uuid: String): Option[T] = {
    val req = get id uuid from store -> manifest[T].runtimeClass.getSimpleName.toLowerCase
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

  def loadWithVersion[T: Manifest](store: String, uuid: String): Option[(T, Long)] = {
    val req = get id uuid from store -> manifest[T].runtimeClass.getSimpleName
    val res = EsClient().execute(req)
    val maybeT = if (res.isExists) Some(JacksonConverter.deserialize[T](res.getSourceAsString)) else None
    maybeT map ((_, res.getVersion))
  }

  def delete[T: Manifest](store: String, uuid: String, refresh: Boolean = false): Boolean = {
    val req = esdelete4s id uuid from store -> manifest[T].runtimeClass.getSimpleName refresh refresh
    val res = EsClient().execute(req)
    res.isFound
  }

  def update[T <: Timestamped : Manifest](store: String, t: T, upsert: Boolean = true, refresh: Boolean = false): Boolean = {
    val now = Calendar.getInstance().getTime
    t.lastUpdated = now
    val js = JacksonConverter.serialize(t)
    val req = esupdate4s id t.uuid in store -> manifest[T].runtimeClass.getSimpleName refresh refresh doc new DocumentSource {
      override val json: String = js
    }
    req.docAsUpsert(upsert)
    val res = EsClient().execute(req)
    res.isCreated
  }

  def update[T <: Timestamped : Manifest](store: String, t: T, version: Long): Boolean = {
    val now = Calendar.getInstance().getTime
    t.lastUpdated = now
    val js = JacksonConverter.serialize(t)
    val req = esupdate4s id t.uuid in store -> manifest[T].runtimeClass.getSimpleName version version doc new DocumentSource {
      override def json: String = js
    }
    val res = EsClient().execute(req)
    true
  }

  def updateRaw(req:UpdateDefinition) : GetResult = {
    EsClient().execute(req).getGetResult
  }

  def searchAll[T: Manifest](req: SearchDefinition): Seq[T] = {
    debug(req)
    val res = EsClient().execute(req)
    res.getHits.getHits.map { hit => JacksonConverter.deserialize[T](hit.getSourceAsString)}
  }

  def search[T: Manifest](req: SearchDefinition): Option[T] = {
    debug(req)
    val res = EsClient().execute(req)
    if (res.getHits.getTotalHits == 0)
      None
    else
      Some(JacksonConverter.deserialize[T](res.getHits.getHits()(0).getSourceAsString))
  }

  def searchAllRaw(req: SearchDefinition): SearchHits = {
    debug(req)
    val res = EsClient().execute(req)
    res.getHits
  }

  def searchRaw(req: SearchDefinition): Option[SearchHit] = {
    debug(req)
    val res = EsClient().execute(req)
    if (res.getHits.getTotalHits == 0)
      None
    else
      Some(res.getHits.getHits()(0))
  }

  implicit def searchHits2JValue(searchHits:SearchHits) : JValue = {
    parse(searchHits.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS).string().substring("hits".length + 2))
  }

  implicit def hits2JArray(hits:Array[SearchHit]) : JArray = JArray(hits.map(hit => parse(hit.getSourceAsString)).toList)

  implicit def hit2JValue(hit:SearchHit) : JValue = parse(hit.getSourceAsString)

  implicit def response2JValue(response:GetResponse) : JValue = parse(response.getSourceAsString)

  implicit def responses2JArray(hits:Array[MultiGetItemResponse]) : JArray = JArray(hits.map(hit => parse(hit.getResponse.getSourceAsString)).toList)

  private def debug(req: SearchDefinition) {
    if (EsDebug) {
      logger.info(req._builder.toString)
    }
  }

}
