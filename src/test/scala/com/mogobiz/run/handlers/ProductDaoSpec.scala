/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import com.mogobiz.MogobizRouteTest
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat

/**
  */
class ProductDaoSpec extends MogobizRouteTest {

  "ProductDao" should "Retrieve a product from a id" in {
    val product = ProductDao.get(STORE, 135)
    product should not be(None)
  }

  it should "Retrieve a product from a unknown id" in {
    val product = ProductDao.get(STORE, 0)
    product should be(None)
  }

  it should "Retrieve a product and sku from a sku's id" in {
    val productAndSku = ProductDao.getProductAndSku(STORE, 137)

    productAndSku should not be(None)

    val product = productAndSku.get._1
    product.id should equal(135)

    val sku = productAndSku.get._2
    sku.id should equal(137)
    sku.uuid should equal("4f48ff92-2bb3-4ad1-af29-112662b1a309")
    sku.sku should equal ("16458153-e907-43f1-bd26-2b90b97dcde3")
    sku.name should equal("Child")
    sku.price should equal(750)
    sku.salePrice should equal(0)
    sku.minOrder should equal(1)
    sku.maxOrder should equal(10)
    sku.availabilityDate should be(None)
    sku.startDate should be(Some(DateTime.parse("2014-01-01T00:00:00Z", ISODateTimeFormat.dateTimeParser())))
    sku.stopDate should be(Some(DateTime.parse("2014-12-31T23:59:00Z", ISODateTimeFormat.dateTimeParser())))
  }

  it should "Retrieve a product and sku from a unknown sku's id" in {
    val productAndSku = ProductDao.getProductAndSku(STORE, 0)
    productAndSku should be(None)
  }

}
