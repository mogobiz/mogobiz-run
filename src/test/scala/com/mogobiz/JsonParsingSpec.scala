/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz

import org.json4s.JsonDSL._

/**
 *
 */
class JsonParsingSpec /*extends Specification*/ {

  val product1 = ("id" -> 1) ~
    ("price" -> 1000000) ~
    ("taxRate" -> (
      ("id" -> 1) ~ ("name" -> "TVA") ~ ("localTaxRates" -> List(
        ("id" -> 10) ~ ("rate" -> 19.6) ~ ("country" -> "FR") ~ ("stateCode" -> null),
        ("id" -> 11) ~ ("rate" -> 17) ~ ("country" -> "EN") ~ ("stateCode" -> null),
        ("id" -> 12) ~ ("rate" -> (None: Option[Double])) ~ ("country" -> "ES") ~ ("stateCode" -> null)
      ))
      ))

}
