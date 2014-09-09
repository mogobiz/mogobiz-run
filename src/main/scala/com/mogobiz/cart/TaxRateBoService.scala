package com.mogobiz.cart

import com.mogobiz.cart.domain.{TaxRate,Product}
import scalikejdbc._

/**
 *
 * Created by Christophe on 06/05/2014.
 */
object TaxRateBoService {

  def findTaxRateByProduct(product: Product, country: String, state: Option[String] = None): Option[Float] = {
    assert(!country.isEmpty,"country should not be empty")
    val taxRate = product.taxRate
    findTaxRate(taxRate, country, state)
  }

  def findTaxRate(taxRate: Option[TaxRate], country: String, state: Option[String]): Option[Float] = {
    assert(!country.isEmpty,"country should not be empty")

    taxRate match {


      case Some(_taxRate) =>

        val taxRateId = _taxRate.id
        /*
        val whereStateCond:String = state match {
          case Some(s) => s"and l.state_code=${s}"
          case None => s"and l.state_code is null"
        }
        */
        val rate = DB readOnly { implicit session =>
          val str = state match {
            case Some(s) => sql"select l.rate from tax_rate_local_tax_rate ass inner join local_tax_rate l on ass.local_tax_rate_id = l.id where ass.local_tax_rates_fk = $taxRateId and l.country_code = $country and l.active is true and l.state_code=$s"
            case None => sql"select l.rate from tax_rate_local_tax_rate ass inner join local_tax_rate l on ass.local_tax_rate_id = l.id where ass.local_tax_rates_fk = $taxRateId and l.country_code = $country and l.state_code is null"
          }
          str.map(rs => rs.float("rate")).single().apply()
        }
        if (rate.isDefined) rate else if (state.isDefined) findTaxRate(Some(_taxRate), country, None) else None

        /*val query = new StringBuffer("SELECT DISTINCT localTaxRate FROM TaxRate taxRate RIGHT JOIN taxRate.localTaxRates AS localTaxRate WHERE taxRate.id = :taxRateId AND localTaxRate.active = true AND localTaxRate.countryCode = :country")
        def params = [taxRateId: taxRate.id, country: country]
        if (!StringUtils.isEmpty(state)) {
          query.append(" AND (localTaxRate.stateCode = :state)")
          params << [state: state]
        }
        else {
          query.append(" AND localTaxRate.stateCode is null")
        }
        List<LocalTaxRate> listLocalTaxRate = LocalTaxRate.executeQuery(query.toString(), params)
        if (listLocalTaxRate != null && listLocalTaxRate.size() > 0) {
          return listLocalTaxRate.get(0).rate
        }
        else if (!StringUtils.isEmpty(state)) {
          return findTaxRate(taxRate, country, null);
        }*/
      case None => None
    }
  }


  def calculateEndPrix(price:Long, taxRate:Option[Float]):Option[Long] = taxRate match {
    case Some(s)=>
      Some(price + (price * s / 100f).toLong)
    case None => None
  }
}
