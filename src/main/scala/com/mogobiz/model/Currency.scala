package com.mogobiz.model

/**
 *
 * Created by dach on 17/02/2014.
 */
case class Currency(currencyFractionDigits: Int, rate: Double, name:String, code: String)

case class CurrencyRequest(lang: String)