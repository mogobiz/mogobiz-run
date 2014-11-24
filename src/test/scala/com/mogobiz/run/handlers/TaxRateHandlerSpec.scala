package com.mogobiz.run.handlers

import com.mogobiz.MogobizRouteTest

/**
 * Created by yoannbaudy on 24/11/2014.
 */
class TaxRateHandlerSpec extends MogobizRouteTest {

  val service = new TaxRateHandler()

  "TaxRateHandler" should {

    "not retrieve TaxRate for a product without country" in {
      val product = ProductDao.get(STORE, 135)
      val tax = service.findTaxRateByProduct(product.get, None, None)
      tax must beNone
    }

    "retrieve TaxRate for a product and a country and a state" in {
      val product = ProductDao.get(STORE, 135)
      val tax = service.findTaxRateByProduct(product.get, Some("USA"), Some("USA.AL"))
      tax must beSome(9f)
    }

    "not retrieve TaxRate for a product and a country without required state" in {
      val product = ProductDao.get(STORE, 135)
      val tax = new TaxRateHandler().findTaxRateByProduct(product.get, Some("USA"), None)
      tax must beNone
    }

    "retrieve TaxRate for a product and a country and a non-required state" in {
      val product = ProductDao.get(STORE, 135)
      val tax = service.findTaxRateByProduct(product.get, Some("FR"), Some("FR.53"))
      tax must beSome(19.6f)
    }

    "retrieve TaxRate for a product and a country without state" in {
      val product = ProductDao.get(STORE, 135)
      val tax = service.findTaxRateByProduct(product.get, Some("FR"), None)
      tax must beSome(19.6f)
    }

    "calculate endPrice with a taxRate" in {
      val endPrice = service.calculateEndPrice(20000, Some(19.6f))
      endPrice must beSome(23920)
    }

    "calculate endPrice without taxRate" in {
      val endPrice = service.calculateEndPrice(20000, None)
      endPrice must beNone
    }
  }

}
