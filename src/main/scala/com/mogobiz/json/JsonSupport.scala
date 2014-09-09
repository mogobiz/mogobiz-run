package com.mogobiz.json

import org.json4s.DefaultFormats
import spray.httpx.Json4sJacksonSupport

object JsonSupport extends Json4sJacksonSupport {
  implicit val json4sJacksonFormats = DefaultFormats
}
