package com.mogobiz

import com.sksamuel.elastic4s.ElasticDsl.{search => esearch4s, _}
import org.json4s.JsonAST.JNothing
import org.json4s._

/**
 *
 * Created by smanciot on 25/09/14.
 */
package object es {

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

}
