package com.mogobiz.run.handlers

import com.mogobiz.run.es._
import org.json4s.JsonAST.JValue


class LangHandler {

  def queryLang(storeCode: String): JValue = {
    queryStoreLanguages(storeCode)
  }
}
