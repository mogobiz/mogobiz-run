package com.mogobiz

import java.util.Locale
import java.text.NumberFormat

/**
 *
 * Created by Christophe on 12/05/2014.
 */
object RateBoService {

  type MogopayRate = Currency

  def format(amount: Double, currencyCode: String, locale: Locale = Locale.getDefault, rate: Double = 0): String = {
    val numberFormat = NumberFormat.getCurrencyInstance(locale)
    numberFormat.setCurrency(java.util.Currency.getInstance(currencyCode))
    numberFormat.format(amount * rate)
  }

  /**
   * Return the amount convert into the given currency. The return is in cents
   * @param amount - amount
   * @return calculated amount
   */
  def calculateAmount(amount: Long, rate:MogopayRate):Long = {
    (amount * rate.rate.doubleValue() * Math.pow(10, rate.currencyFractionDigits)).toLong
  }

}
