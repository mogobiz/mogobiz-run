/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run

import com.mogobiz.es.EsClient
import com.mogobiz.pay.config.Settings
import com.mogobiz.run.model.Currency
import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.searches.SearchDefinition
import com.sksamuel.elastic4s.searches.queries.QueryDefinition
import com.typesafe.scalalogging.Logger
import org.json4s.JsonAST.{JNothing, JValue}
import org.json4s._
import org.slf4j.LoggerFactory

/**
  *
  */
package object es {

  private val log = Logger(LoggerFactory.getLogger("es"))

  private val defaultCurrency = Currency(currencyFractionDigits = 2, rate = 0.01d, name = "Euro", code = "EUR") //FIXME

  def queryCurrency(store: String, currencyCode: Option[String]): Currency = {
    assert(!store.isEmpty)
    currencyCode match {
      case Some(s) =>
        EsClient.search[Currency](
            filterRequest(
                search(Settings.Mogopay.EsIndex -> "Rate"),
                List(
                    createtermQuery("code", Some(s))
                ).flatten
            )
        ) getOrElse defaultCurrency
      case None =>
        log.warn(
            s"No currency code supplied: fallback to programmatic default currency: ${defaultCurrency.code}, ${defaultCurrency.rate}")
        defaultCurrency
    }
  }

  /**
    *
    * @param store - store code
    * @return store languages
    */
  def queryStoreLanguages(store: String): JValue = {
    val languages: JValue = EsClient.searchRaw(search(store -> "i18n") sourceInclude "languages") match {
      case Some(s) => s
      case None    => JNothing
    }
    languages \ "languages"
  }

  def getStoreLanguagesAsList(store: String): List[String] = {
    implicit def json4sFormats: Formats = DefaultFormats

    queryStoreLanguages(store).extract[List[String]]
  }

  /**
    * if lang == "_all" returns an empty list
    * else returns the list of all languages except the given language
    *
    * @param store - store
    * @param lang  - language
    * @return excluded languages
    */
  def createExcludeLang(store: String, lang: String): List[String] = {
    if (lang == "_all") List()
    else
      getStoreLanguagesAsList(store).filter { case l: String => l != lang }.collect {
        case l: String =>
          List(l, l + ".*", "*." + l, "*." + l + ".*")
      }.flatten
  }

  /**
    * Créer le filtre correspondant à la requete fournie. La requête a la format suivant :
    * k1:::v1_1|v1_2|||k2:::v2_1|v2_2 etc...
    * Le filtre généré est le suivant (optimisé si possible):
    * and {
    * or {
    * filter pour tester k1 et v1_1,
    * filter pour tester k1 et v1_2
    *          etc...
    * },
    * or {
    * filter pour tester k2 et v2_1,
    * filter pour tester k2 et v2_2
    *          etc...
    * }
    *     etc...
    * }
    *
    * @param optQuery - query
    * @param fct      - function
    * @return
    */
  def createAndOrFilterBySplitKeyValues(optQuery: Option[String],
                                        fct: (String, String) => Option[QueryDefinition]): Option[QueryDefinition] = {
    optQuery match {
      case Some(query) =>
        val andFilters = (for (keyValues <- query.split("""\|\|\|""")) yield {
          val kv = keyValues.split("""\:\:\:""")
          if (kv.size == 2) {
            val orFilters = (for (v <- kv(1).split("""\|""")) yield {
              fct(kv(0), v)
            }).toList.flatten
            if (orFilters.length > 1) Some(should(orFilters))
            else orFilters.headOption
          } else None
        }).toList.flatten
        if (andFilters.length > 1) Some(must(andFilters))
        else andFilters.headOption
      case _ => None
    }
  }

  /**
    * Créer le filtre correspondant à la requete fournie. La requête a la format suivant :
    * k1:::v1|||k2:::v2 etc...
    * Le filtre généré est le suivant (optimisé si possible):
    * or {
    * filter pour tester k1 et v1,
    * filter pour tester k2 et v2
    *     etc...
    * }
    *
    * @param optQuery - query
    * @param fct      - function
    * @return
    */
  def createOrFilterBySplitKeyValues(optQuery: Option[String],
                                     fct: (String, String) => Option[QueryDefinition]): Option[QueryDefinition] = {
    optQuery match {
      case Some(query) => {
        val orFilters = (for (keyValues <- query.split("""\|\|\|""")) yield {
          val kv = keyValues.split("""\:\:\:""")
          if (kv.size == 2) {
            fct(kv(0), kv(1))
          } else None
        }).toList.flatten
        if (orFilters.length > 1) Some(should(orFilters)) else orFilters.headOption
      }
      case _ => None
    }
  }

  def convertAsLongOption(value: String): Option[Long] = {
    if (value == null || value.trim.length == 0) None
    else
      try {
        Some(value.toInt)
      } catch {
        case e: Exception => None
      }
  }

  /**
    * Créer le filtre correspondant à la requete fournie. La requête a la format suivant :
    * v1|v2 etc...
    * Le filtre généré est le suivant (optimisé si possible):
    * or {
    * filter pour tester v1,
    * filter pour tester v2
    *     etc...
    * }
    *
    * @param optQuery - query
    * @param fct      - function
    * @return
    */
  def createOrFilterBySplitValues(optQuery: Option[String],
                                  fct: (String) => Option[QueryDefinition]): Option[QueryDefinition] = {
    optQuery match {
      case Some(query) => {
        val orFilters = (for (v <- query.split("""\|""")) yield {
          fct(v)
        }).toList.flatten
        if (orFilters.length > 1) Some(should(orFilters)) else orFilters.headOption
      }
      case _ => None
    }
  }

  def getAllExcludedLanguagesExcept(store: String, langRequested: String): List[String] = {
    val storeLanguagesList: List[String] = getStoreLanguagesAsList(store)
    if (langRequested == "_all") List()
    else if (langRequested.isEmpty) storeLanguagesList
    else
      storeLanguagesList.filter { lang =>
        lang != langRequested
      }
  }

  def getAllExcludedLanguagesExceptAsList(store: String, lang: String): List[String] = {
    getAllExcludedLanguagesExcept(store, lang).flatMap { l =>
      l :: "*." + l :: Nil
    }
  }

  def getLangFieldsWithPrefixAsList(store: String, preField: String, field: String): List[String] = {
    getStoreLanguagesAsList(store).flatMap { l =>
      preField + l + "." + field :: Nil
    }
  }

  def getIncludedFieldWithPrefixAsList(store: String, preField: String, field: String, lang: String): List[String] = {
    if ("_all".equals(lang)) {
      getLangFieldsWithPrefixAsList(store, preField, field)
    } else {
      List(preField + lang + "." + field)
    }
  }

  def getHighlightedFieldsAsList(store: String, field: String, lang: String): List[String] = {
    if ("_all".equals(lang))
      getStoreLanguagesAsList(store).flatMap { l =>
        l + "." + field :: Nil
      } else List(s"$lang.$field")
  }

  def createQuery(queries: List[QueryDefinition]): QueryDefinition = {
    if (queries.isEmpty) matchAllQuery()
    else boolQuery().must(queries: _*)
  }

  def filterRequest(req: SearchDefinition,
                    filters: List[QueryDefinition],
                    _query: QueryDefinition = matchAllQuery()): SearchDefinition =
    if (filters.nonEmpty) {
      if (filters.size > 1)
        req query {
          boolQuery().must(filters)
        } else
        req query {
          must(filters.head)
        }
    } else req query _query

  def filterOrRequest(req: SearchDefinition,
                      filters: List[QueryDefinition],
                      _query: QueryDefinition = matchAllQuery()): SearchDefinition =
    if (filters.nonEmpty) {
      if (filters.size > 1)
        req query {
          boolQuery().should(filters)
        } else
        req query {
          boolQuery().must(filters.head)
        }
    } else req query _query

  def createOrRegexAndTypeFilter(typeFields: List[TypeField], value: Option[String]): Option[QueryDefinition] = {
    value match {
      case Some(s) =>
        Some(
            should(
                typeFields.map(typeField =>
                      must(typeQuery(typeField.`type`), regexQuery(typeField.field, s".*${s.toLowerCase}.*"))): _*
            )
        )
      case None => None
    }
  }

  def createNestedtermQuery(path: String, field: String, value: Option[Any]): Option[QueryDefinition] = {
    value match {
      case Some(s) =>
        s match {
          case v: String =>
            val values = v.split("""\|""")
            if (values.size > 1) {
              Some(nestedQuery(path) query termsQuery(field, values))
            } else {
              Some(nestedQuery(path) query termsQuery(field, v))
            }
          case _ => Some(nestedQuery(path) query termsQuery(field, s))
        }
      case None => None
    }
  }

  def createtermQuery(field: String, value: Option[Any]): Option[QueryDefinition] = {
    value match {
      case Some(s) =>
        s match {
          case v: String =>
            val values = v.split("""\|""")
            if (values.size > 1) {
              Some(termsQuery(field, values))
            } else {
              Some(termQuery(field, v))
            }
          case _ => Some(termQuery(field, s))
        }
      case None => None
    }
  }

  def createMatchQuery(field: String, value: Option[Any]): Option[QueryDefinition] = {
    value match {
      case Some(s) =>
        s match {
          case _ => Some(matchQuery(field, s))
        }
      case None => None
    }
  }

  def createRegexFilter(field: String, value: Option[String], addStars: Boolean = true): Option[QueryDefinition] = {
    value match {
      case Some(s) =>
        val star = if (addStars) ".*" else ""
        Some(regexQuery(field, s"$star${s.toLowerCase}$star"))
      case None => None
    }
  }

  def createExistsFilter(field: Option[String]): Option[QueryDefinition] = {
    field match {
      case Some(s) => Some(existsQuery(s))
      case None    => None
    }
  }

  def createRangeFilter(field: String, _gte: Option[String], _lte: Option[String]): Option[QueryDefinition] = {
    val req = _gte match {
      case Some(g) =>
        _lte match {
          case Some(l) => Some(rangeQuery(field) gte g lte l)
          case _       => Some(rangeQuery(field) gte g)
        }
      case _ =>
        _lte match {
          case Some(l) => Some(rangeQuery(field) lte l)
          case _       => None
        }
    }
    req
  }

  def createNumericRangeFilter(field: String, _gte: String, _lte: String): Option[QueryDefinition] = {
    createNumericRangeFilter(field, convertAsLongOption(_gte), convertAsLongOption(_lte))
  }

  def createNumericRangeFilter(field: String, _gte: Option[Long], _lte: Option[Long]): Option[QueryDefinition] = {
    (_gte, _lte) match {
      case (Some(gte_v), Some(lte_v)) => Some(rangeQuery(field) gte gte_v lte lte_v)
      case (Some(gte_v), None)        => Some(rangeQuery(field) gte gte_v)
      case (None, Some(lte_v))        => Some(rangeQuery(field) lte lte_v)
      case _                          => None
    }
  }

  case class TypeField(`type`: String, field: String)

}
