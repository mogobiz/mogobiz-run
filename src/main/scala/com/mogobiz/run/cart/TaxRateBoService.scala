package com.mogobiz.run.cart

import com.mogobiz.run.cart.domain.{TaxRate,Product}
import scalikejdbc._

/**
 *
 * Created by Christophe on 06/05/2014.
 */
object TaxRateBoService {

  def findTaxRateByProduct(product: Product, country: Option[String], state: Option[String] = None): Option[Float] = {
    val taxRate = product.taxRate
    findTaxRate(taxRate, country, state)
  }

  def findTaxRate(taxRate: Option[TaxRate], country: Option[String], state: Option[String]): Option[Float] = {
    if (taxRate.isDefined && country.isDefined) {
      assert(!country.get.isEmpty, "country should not be empty")
      val taxRateId = taxRate.get.id
      val countryCode = country.get
      val rate = DB readOnly { implicit session =>
        val str = state match {
          case Some(s) => sql"select l.rate from tax_rate_local_tax_rate ass inner join local_tax_rate l on ass.local_tax_rate_id = l.id where ass.local_tax_rates_fk = $taxRateId and l.country_code = $countryCode and l.active is true and l.state_code=$s"
          case None => sql"select l.rate from tax_rate_local_tax_rate ass inner join local_tax_rate l on ass.local_tax_rate_id = l.id where ass.local_tax_rates_fk = $taxRateId and l.country_code = $countryCode and l.state_code is null"
        }
        str.map(rs => rs.float("rate")).single().apply()
      }
      if (rate.isDefined) rate else if (state.isDefined) findTaxRate(taxRate, country, None) else None
    }
    else None
  }

  def calculateEndPrix(price:Long, taxRate:Option[Float]):Option[Long] = taxRate match {
    case Some(s)=>
      Some(price + (price * s / 100f).toLong)
    case None => None
  }
}
