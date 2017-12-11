/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import com.mogobiz.MogobizRouteTest

/**
 */
class TaxRateHandlerSpec extends MogobizRouteTest {

  val service = new TaxRateHandler()

  "TaxRateHandler" should "not retrieve TaxRate for a product without country" in {
      val product = ProductDao.get(STORE, 135)
      val tax = service.findTaxRateByProduct(product.get, None, None)
      tax should be(None)
    }

    it should "retrieve TaxRate for a product and a country and a state" in {
      val product = ProductDao.get(STORE, 135)
      val tax = service.findTaxRateByProduct(product.get, Some("USA"), Some("USA.AL"))
      tax should be(Some(9f))
    }

  it should "not retrieve TaxRate for a product and a country without required state" in {
      val product = ProductDao.get(STORE, 135)
      val tax = new TaxRateHandler().findTaxRateByProduct(product.get, Some("USA"), None)
      tax should be(None)
    }

  it should "retrieve TaxRate for a product and a country and a non-required state" in {
      val product = ProductDao.get(STORE, 135)
      val tax = service.findTaxRateByProduct(product.get, Some("FR"), Some("FR.53"))
      tax should be(Some(19.6f))
    }

  it should "retrieve TaxRate for a product and a country without state" in {
      val product = ProductDao.get(STORE, 135)
      val tax = service.findTaxRateByProduct(product.get, Some("FR"), None)
      tax should be(Some(19.6f))
    }

  it should "calculate endPrice with a taxRate" in {
      val endPrice = service.calculateEndPrice(20000, Some(19.6f))
      endPrice should be(Some(23920))
    }

  it should "calculate endPrice without taxRate" in {
      val endPrice = service.calculateEndPrice(20000, None)
      endPrice should be(None)
    }
  }
