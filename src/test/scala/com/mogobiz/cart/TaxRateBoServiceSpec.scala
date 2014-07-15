package com.mogobiz.cart

import org.specs2.mutable.Specification
import scalikejdbc.config._
import scala.Some

/**
 * Created by Christophe on 07/05/2014.
 */
class TaxRateBoServiceSpec  extends Specification {

  DBs.setupAll()

  val service = TaxRateBoService
  val taxRateId = 12
  val companyId = 8
  val defaultTaxRate = new TaxRate(taxRateId,"",companyId)

  val taxRate = Some(defaultTaxRate)



  "return taxRate from FR" in {

    val country = "FR"
    val state = None

    val rate = service.findTaxRate(taxRate,country,state)
    rate must beSome(19.6f)
  }
  "return taxRate from USA.AL" in {

    val country = "USA"
    val state = Some("USA.AL")

    val rate = service.findTaxRate(taxRate,country,state)
    rate must beSome(9)
  }

  "return product tax rate" in {

    val country = "FR"
    val state = None
    val product = new Product(id=47,name="",xtype=ProductType.PRODUCT, calendarType=ProductCalendar.NO_DATE,taxRateFk = Some(taxRateId),taxRate=Some(defaultTaxRate),shippingFk=None,startDate=None,stopDate=None,poiFk=None,companyFk=8)

    val rate = service.findTaxRateByProduct(product,country,state)
    rate must beSome(19.6f)

  }


  "calculate end price" in {
    val price = 10000l //100€ in cents
    val rate = Some(19.6f)
    val endprice = service.calculateEndPrix(price,rate)
    endprice must beSome(11960)
  }

}
