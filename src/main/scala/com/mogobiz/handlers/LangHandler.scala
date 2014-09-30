package com.mogobiz.handlers

import com.mogobiz.es._
import org.json4s.JsonAST.JValue


class LangHandler {

  def queryLang(storeCode: String): JValue = {
    queryStoreLanguages(storeCode)
  }
}
