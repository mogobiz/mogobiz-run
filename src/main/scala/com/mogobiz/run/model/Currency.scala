/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
case class Currency(currencyFractionDigits: Int, rate: Double, name: String, code: String) {
  val numericCode = java.util.Currency.getInstance(code).getNumericCode
}
