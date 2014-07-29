package com.mogobiz.config

import com.mogobiz.handlers.{CountryHandler,LangHandler, BrandHandler, TagHandler}

object HandlersConfig {
  val tagHandler = new TagHandler
  val brandHandler = new BrandHandler
  val langHandler = new LangHandler
  val countryHandler = new CountryHandler
}
