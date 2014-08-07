package com.mogobiz.config

import spray.httpx.Json4sJacksonSupport
import org.json4s.{Formats, DefaultFormats}

object JsonSupport extends Json4sJacksonSupport {
  implicit val json4sJacksonFormats = DefaultFormats
}
