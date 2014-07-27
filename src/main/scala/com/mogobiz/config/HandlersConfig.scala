package com.mogobiz.config

import com.mogobiz.handlers.{BrandHandler, TagHandler}

object HandlersConfig {
  val tagHandler = new TagHandler
  val brandHandler = new BrandHandler
}
