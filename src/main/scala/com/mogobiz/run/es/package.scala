/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run

import com.mogobiz.es._
import com.mogobiz.es.EsClient
import com.mogobiz.pay.config.Settings
import com.mogobiz.run.model.Currency
import com.sksamuel.elastic4s.ElasticDsl.{search => esearch4s, _}
import com.sksamuel.elastic4s.source.DocumentSource
import com.sksamuel.elastic4s.{SearchDefinition, FilterDefinition, QueryDefinition}
import com.typesafe.scalalogging.slf4j.Logger
import org.elasticsearch.action.get.{MultiGetItemResponse, GetResponse}
import org.elasticsearch.common.xcontent.{ToXContent, XContentFactory}
import org.elasticsearch.search.{SearchHit, SearchHits}
import org.json4s.JsonAST.{JArray, JValue, JNothing}
import org.json4s._
import org.json4s.native.JsonMethods._
import org.slf4j.LoggerFactory

/**
 *
 * Created by smanciot on 25/09/14.
 */
package object es {

  private val log = Logger(LoggerFactory.getLogger("es"))

  private val defaultCurrency = Currency(currencyFractionDigits = 2, rate = 0.01d, name="Euro", code = "EUR") //FIXME

  def queryCurrency(store: String, currencyCode:Option[String]) : Currency = {
    assert(!store.isEmpty)
    currencyCode match {
      case Some(s) =>
        EsClient.search[Currency](
          filterRequest(
            esearch4s in Settings.Mogopay.EsIndex -> "Rate",
            List(
              createTermFilter("code", Some(s))
            ).flatten
          )
        ) getOrElse defaultCurrency
      case None =>
        log.warn(s"No currency code supplied: fallback to programmatic default currency: ${defaultCurrency.code}, ${defaultCurrency.rate}")
        defaultCurrency
    }
  }

  /**
   *
   * @param store - store code
   * @return store languages
   */
  def queryStoreLanguages(store: String): JValue = {
    val languages:JValue = EsClient.searchRaw(esearch4s in store -> "i18n" sourceInclude "languages" ) match {
      case Some(s) => s
      case None => JNothing
    }
    languages  \ "languages"
  }

  def getStoreLanguagesAsList(store: String): List[String] = {
    implicit def json4sFormats: Formats = DefaultFormats
    queryStoreLanguages(store).extract[List[String]]
  }

  /**
   * if lang == "_all" returns an empty list
   * else returns the list of all languages except the given language
   * @param store - store
   * @param lang - language
   * @return excluded languages
   */
  def createExcludeLang(store: String, lang: String): List[String] = {
    if (lang == "_all") List()
    else getStoreLanguagesAsList(store).filter{case l: String => l != lang}.collect { case l:String =>
      List(l, l+".*", "*." + l, "*." + l + ".*")
    }.flatten
  }

  /**
   * Créer le filtre correspondant à la requete fournie. La requête a la format suivant :
   * k1:::v1_1|v1_2|||k2:::v2_1|v2_2 etc...
   * Le filtre généré est le suivant (optimisé si possible):
   * and {
   *     or {
   *          filter pour tester k1 et v1_1,
   *          filter pour tester k1 et v1_2
   *          etc...
   *     },
   *     or {
   *          filter pour tester k2 et v2_1,
   *          filter pour tester k2 et v2_2
   *          etc...
   *     }
   *     etc...
   * }
   * @param optQuery
   * @param fct
   * @return
   */
  def createAndOrFilterBySplitKeyValues(optQuery: Option[String], fct: (String, String) => Option[FilterDefinition]) : Option[FilterDefinition] = {
    optQuery match {
      case Some(query) => {
        val andFilters = (for (keyValues <- query.split("""\|\|\|""")) yield {
          val kv = keyValues.split( """\:\:\:""")
          if (kv.size == 2) {
            val orFilters = (for (v <- kv(1).split("""\|""")) yield {
              fct(kv(0), v)
            }).toList.flatten
            if (orFilters.length > 1) Some(or(orFilters: _*)) else if (orFilters.length == 1) Some(orFilters(0)) else None
          }
          else None
        }).toList.flatten
        if (andFilters.length > 1) Some(and(andFilters: _*)) else if (andFilters.length == 1) Some(andFilters(0)) else None
      }
      case _ => None
    }
  }

  /**
   * Créer le filtre correspondant à la requete fournie. La requête a la format suivant :
   * k1:::v1|||k2:::v2 etc...
   * Le filtre généré est le suivant (optimisé si possible):
   * or {
   *     filter pour tester k1 et v1,
   *     filter pour tester k2 et v2
   *     etc...
   * }
   * @param optQuery
   * @param fct
   * @return
   */
  def createOrFilterBySplitKeyValues(optQuery: Option[String], fct: (String, String) => Option[FilterDefinition]) : Option[FilterDefinition] = {
    optQuery match {
      case Some(query) => {
        val orFilters = (for (keyValues <- query.split("""\|\|\|""")) yield {
          val kv = keyValues.split( """\:\:\:""")
          if (kv.size == 2) {
            fct(kv(0), kv(1))
          }
          else None
        }).toList.flatten
        if (orFilters.length > 1) Some(or(orFilters: _*)) else if (orFilters.length == 1) Some(orFilters(0)) else None
      }
      case _ => None
    }
  }

  def convertAsLongOption(value: String) : Option[Long] = {
    if (value == null || value.trim.length == 0) None
    else try {
      Some(value.toInt)
    }
    catch {
      case e:Exception => None
    }
  }

  /**
   * Créer le filtre correspondant à la requete fournie. La requête a la format suivant :
   * v1|v2 etc...
   * Le filtre généré est le suivant (optimisé si possible):
   * or {
   *     filter pour tester v1,
   *     filter pour tester v2
   *     etc...
   * }
   * @param optQuery
   * @param fct
   * @return
   */
  def createOrFilterBySplitValues(optQuery: Option[String], fct: (String) => Option[FilterDefinition]) : Option[FilterDefinition] = {
    optQuery match {
      case Some(query) => {
        val orFilters = (for (v <- query.split("""\|""")) yield {
          fct(v)
        }).toList.flatten
        if (orFilters.length > 1) Some(or(orFilters: _*)) else if (orFilters.length == 1) Some(orFilters(0)) else None
      }
      case _ => None
    }
  }

  def getAllExcludedLanguagesExcept(store: String, langRequested: String): List[String] = {
    val storeLanguagesList: List[String] = getStoreLanguagesAsList(store)
    if (langRequested == "_all") List()
    else if(langRequested.isEmpty) storeLanguagesList
    else storeLanguagesList.filter {
      lang => lang != langRequested
    }
  }

  def getAllExcludedLanguagesExceptAsList(store: String, lang: String): List[String] = {
    getAllExcludedLanguagesExcept(store,lang).flatMap {
      l => l :: "*." + l :: Nil
    }
  }

  def getLangFieldsWithPrefixAsList(store: String, preField: String, field: String): List[String] = {
    getStoreLanguagesAsList(store).flatMap {
      l => preField + l + "." + field :: Nil
    }
  }

  def getIncludedFieldWithPrefixAsList(store: String, preField:  String, field: String, lang: String) : List[String] = {
    if ("_all".equals(lang)) {
      getLangFieldsWithPrefixAsList(store, preField, field)
    } else {
      List(preField + lang + "." + field)
    }
  }

  def getHighlightedFieldsAsList(store: String, field: String, lang: String): List[String] = {
    if ("_all".equals(lang))
      getStoreLanguagesAsList(store).flatMap {
        l => l + "." + field :: Nil
      }
    else List(s"$lang.$field")
  }

  def filterRequest(req:SearchDefinition, filters:List[FilterDefinition], _query:QueryDefinition = matchall) : SearchDefinition =
    if(filters.nonEmpty){
      if(filters.size > 1)
        req query {filteredQuery query _query filter {and(filters: _*)}}
      else
        req query {filteredQuery query _query filter filters(0)}
    }
    else req query _query

  def filterOrRequest(req:SearchDefinition, filters:List[FilterDefinition], _query:QueryDefinition = matchall) : SearchDefinition =
    if(filters.nonEmpty){
      if(filters.size > 1)
        req query {filteredQuery query _query filter {or(filters: _*)}}
      else
        req query {filteredQuery query _query filter filters(0)}
    }
    else req

  def createOrRegexAndTypeFilter(typeFields:List[TypeField], value:Option[String]) : Option[FilterDefinition] = {
    value match{
      case Some(s) => Some(
        or(
          typeFields.map(typeField => and(typeFilter(typeField.`type`), regexFilter(typeField.field, s".*${s.toLowerCase}.*")) ):_*
        )
      )
      case None => None
    }
  }

  def createNestedTermFilter(path:String, field:String, value:Option[Any]) : Option[FilterDefinition] = {
    value match{
      case Some(s) =>
        s match {
          case v:String =>
            val values = v.split("""\|""")
            if(values.size > 1){
              Some(nestedFilter(path) filter termsFilter(field, values:_*))
            }
            else{
              Some(nestedFilter(path) filter termFilter(field, v))
            }
          case _ => Some(nestedFilter(path) filter termFilter(field, s))
        }
      case None => None
    }
  }

  def createTermFilter(field:String, value:Option[Any]) : Option[FilterDefinition] = {
    value match{
      case Some(s) =>
        s match {
          case v:String =>
            val values = v.split("""\|""")
            if(values.size > 1){
              Some(termsFilter(field, values:_*))
            }
            else{
              Some(termFilter(field, v))
            }
          case _ => Some(termFilter(field, s))
        }
      case None => None
    }
  }

  def createRegexFilter(field:String, value:Option[String], addStars: Boolean = true) : Option[FilterDefinition] = {
    value match{
      case Some(s) => {
        val star = if (addStars) ".*" else ""
        Some(regexFilter(field, s"${star}${s.toLowerCase}${star}"))
      }
      case None => None
    }
  }

  def createExistsFilter(field:Option[String]) : Option[FilterDefinition] = {
    field match{
      case Some(s) => Some(existsFilter(s))
      case None => None
    }
  }

  def createRangeFilter(field:String, _gte:Option[String], _lte: Option[String]) : Option[FilterDefinition] = {
    val req = _gte match{
      case Some(g) => _lte match {
        case Some(l) => Some(rangeFilter(field) gte g lte l)
        case _ => Some(rangeFilter(field) gte g)
      }
      case _ => _lte match {
        case Some(l) => Some(rangeFilter(field) lte l)
        case _ => None
      }
    }
    req
  }

  def createNumericRangeFilter(field:String, _gte:String, _lte: String) : Option[FilterDefinition] = {
    createNumericRangeFilter(field, convertAsLongOption(_gte), convertAsLongOption(_lte))
  }

  def createNumericRangeFilter(field:String, _gte:Option[Long], _lte: Option[Long]) : Option[FilterDefinition] = {
    (_gte,_lte) match {
      case (Some(gte_v), Some(lte_v)) => Some(numericRangeFilter(field) gte gte_v lte lte_v)
      case (Some(gte_v), None) => Some(numericRangeFilter(field) gte gte_v)
      case (None, Some(lte_v)) => Some(numericRangeFilter(field) lte lte_v)
      case _ => None
    }
  }

  case class TypeField(`type`:String, field:String)

}
