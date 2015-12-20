/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import java.text.NumberFormat
import java.util.Locale

import com.mogobiz.run.model.Currency

/**
 *
 */
object RateBoService {

  type MogopayRate = Currency

  def formatPrice(price: Double, currency: Currency, locale: Locale = Locale.getDefault): String = {
    val numberFormat = NumberFormat.getCurrencyInstance(locale)
    numberFormat.setCurrency(java.util.Currency.getInstance(currency.code))
    numberFormat.format(price * currency.rate)
  }

  def formatLongPrice(price: Long, currency: Currency, locale: Locale = Locale.getDefault): String = {
    val numberFormat = NumberFormat.getCurrencyInstance(locale)
    numberFormat.setCurrency(java.util.Currency.getInstance(currency.code))
    numberFormat.format(price * currency.rate)
  }

  /**
   * Return the amount convert into the given currency. The return is in cents
   * @param amount - amount
   * @return calculated amount
   */
  def calculateAmount(amount: Long, rate: MogopayRate): Long = {
    (amount * rate.rate.doubleValue() * Math.pow(10, rate.currencyFractionDigits)).toLong
  }

}
