package com.mogobiz

import java.util.Locale
import java.text.NumberFormat

/**
 * Created by Christophe on 12/05/2014.
 */
object RateBoService {

  def format(amount: Double, currencyCode: String, locale: Locale = Locale.getDefault, rate: Double = 0): String = {
    val numberFormat = NumberFormat.getCurrencyInstance(locale);
    numberFormat.setCurrency(java.util.Currency.getInstance(currencyCode));
    numberFormat.format(amount * rate);
  }
}
