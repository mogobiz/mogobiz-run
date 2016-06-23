/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import com.mogobiz.run.model.Mogobiz
import com.mogobiz.run.model.Mogobiz.LocalTaxRate
import org.apache.commons.lang.StringUtils

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
    country.map { c =>
      state.map { s =>
        localTaxRates.find { taxRate =>
          taxRate.countryCode == c && taxRate.stateCode == s
        }.map { taxRate =>
          Some(taxRate.rate)
        }.getOrElse {
          findTaxRate(localTaxRates, country, None)
        }
      }.getOrElse {
        val existTaxRateWithState = localTaxRates.find { taxRate =>
          taxRate.countryCode == c && StringUtils.isNotEmpty(taxRate.stateCode)
        }
        existTaxRateWithState.map {
          ltx => None
        }.getOrElse {
          localTaxRates.find { taxRate =>
            taxRate.countryCode == c && StringUtils.isEmpty(taxRate.stateCode)
          }.map { taxRate =>
            Some(taxRate.rate)
          }.getOrElse(None)
        }
      }
    }.getOrElse(None)
  }

  /**
   * Applies the tax on the price if the tax is defined else returns None
   * @param price : price use to apply tax
   * @param taxRate : TaxRate to apply on price
   * @return : End price or None if no Tax is defined
   */
  def calculateEndPrice(price: Long, taxRate: Option[Float]): Option[Long] = taxRate match {
    case Some(s) =>
      Some(math.round(price + (price * s / 100f)))
    case None => None
  }

}
