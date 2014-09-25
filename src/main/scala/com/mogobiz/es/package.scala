package com.mogobiz

import com.mogobiz.model.Currency
import com.sksamuel.elastic4s.ElasticDsl.{search => esearch4s, _}
import com.sksamuel.elastic4s.{QueryDefinition, FilterDefinition}
import com.typesafe.scalalogging.slf4j.Logger
import org.json4s.JsonAST.JNothing
import org.json4s._
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
      case Some(s) =>     EsClient.search[Currency](
        filterRequest(
          esearch4s in store -> "rate",
          List(
            createTermFilter("code", Some(currencyCode))
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
    import EsClient._
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

  def createRegexFilter(field:String, value:Option[String]) : Option[FilterDefinition] = {
    value match{
      case Some(s) => Some(regexFilter(field, s".*${s.toLowerCase}.*"))
      case None => None
    }
  }

  def createRangeFilter(field:String, _gte:Option[String], _lte: Option[String]) : Option[FilterDefinition] = {
    val req = _gte match{
      case Some(s) => Some(rangeFilter(field) gte s)
      case None => None
    }
    _lte match {
      case Some(s) => if(req.isDefined) Some(req.get lte s) else Some(rangeFilter(field) lte s)
      case None => None
    }
  }

  def createNumericRangeFilter(field:String, _gte:Option[Long], _lte: Option[Long]) : Option[FilterDefinition] = {
    val req = _gte match{
      case Some(s) => Some(numericRangeFilter(field) gte s)
      case None => None
    }
    _lte match {
      case Some(s) => if(req.isDefined) Some(req.get lte s) else Some(numericRangeFilter(field) lte s)
      case None => None
    }
  }

  case class TypeField(`type`:String, field:String)
}
