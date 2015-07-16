/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.implicits

import org.json4s.DefaultFormats
import spray.httpx.Json4sJacksonSupport

object JsonSupport extends Json4sJacksonSupport {
  implicit val json4sJacksonFormats = DefaultFormats
}
