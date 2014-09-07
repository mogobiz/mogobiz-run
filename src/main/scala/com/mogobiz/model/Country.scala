package com.mogobiz.model

/**
 *
 * Created by dach on 17/02/2014.
 */
case class Country(id: Int, code: String, label: String, translations: Option[List[String]])

case class CountryRequest(lang: String)
