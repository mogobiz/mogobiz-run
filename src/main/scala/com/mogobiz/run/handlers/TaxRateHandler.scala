/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import com.mogobiz.run.model.Mogobiz
import com.mogobiz.run.model.Mogobiz.LocalTaxRate

/**
 */
class TaxRateHandler {

  /**
   * Returns the tax to apply for the given product, country and state
   * @param product : product use to retrieve tax
   * @param country : country use to retrieve tax
   * @param state : optional state use to retrieve tax
   * @return : the found tax or None if tax isn't find
   */
  def findTaxRateByProduct(product: Mogobiz.Product, country: Option[String], state: Option[String]): Option[Float] = {
    if (product.taxRate.isDefined) findTaxRate(product.taxRate.get.localTaxRates, country, state)
    else None
  }

  /**
   * Returns the tax for the country and the state. If a state is defined and no tax is found,
   * the method call herself without state
   * @param localTaxRates : liste of LocalTaxRate use to retrive tax
   * @param country : country use to retrieve tax
   * @param state : optional state use to retrieve tax
   * @return : the found tax or None if tax isn't find
   */
  private def findTaxRate(localTaxRates: List[LocalTaxRate], country: Option[String], state: Option[String]): Option[Float] = {
    if (country.isDefined) {
      val taxRate = localTaxRates.find{ taxRate => {
        val isSameState : Boolean = if (state.isDefined) taxRate.stateCode == state.get else taxRate.stateCode == ""
        isSameState && taxRate.countryCode == country.get
      }}
      if (taxRate.isDefined) Some(taxRate.get.rate)
      else if (state.isDefined) findTaxRate(localTaxRates, country, None) // On recherche le taxRate sans State
      else None
    }
    else None
  }

  /**
   * Applies the tax on the price if the tax is defined else returns None
   * @param price : price use to apply tax
   * @param taxRate : TaxRate to apply on price
   * @return : End price or None if no Tax is defined
   */
  def calculateEndPrice(price:Long, taxRate:Option[Float]):Option[Long] = taxRate match {
    case Some(s)=>
      Some(price + (price * s / 100f).toLong)
    case None => None
  }

}
