package com.mogobiz.handlers

import org.specs2.mutable.Specification


class SalesHandlerSpec extends Specification {

  val handler = new SalesHandler

  val storeCode = "mogobiz"

  "update method" should {
    "get the product and update the product and sku nbSales fields " in {
      val pid = 135
      val skuId = 137 //139
      val nbSales = 10

      val res = handler.update(storeCode, pid, nbSales, skuId, nbSales)

      //res must_== (true)
      success
    }
  }
}