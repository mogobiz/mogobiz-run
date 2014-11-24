package com.mogobiz.run.handlers

import com.mogobiz.MogobizRouteTest
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat

/**
 * Created by yoannbaudy on 21/11/2014.
 */
class ProductDaoSpec extends MogobizRouteTest {

  "ProductDao" should {

    "Retrieve a product from a id" in {
      val product = ProductDao.get(STORE, 135)
      product must beSome
    }

    "Retrieve a product from a unknown id" in {
      val product = ProductDao.get(STORE, 0)
      product must beNone
    }

    "Retrieve a product and sku from a sku's id" in {
      val productAndSku = ProductDao.getProductAndSku(STORE, 137)

      productAndSku must beSome

      val product = productAndSku.get._1
      product.id mustEqual 135

      val sku = productAndSku.get._2
      sku.id mustEqual 137
      sku.uuid mustEqual "6e258455-1b6c-4c99-9dfb-324c7f9230ed"
      sku.sku mustEqual "72b7f03f-9ddb-4890-9a91-1879ee3b6b91"
      sku.name mustEqual "Child"
      sku.price mustEqual 750
      sku.salePrice mustEqual 0
      sku.minOrder mustEqual 1
      sku.maxOrder mustEqual 10
      sku.availabilityDate must beNone
      sku.startDate must beSome(DateTime.parse("2014-01-01T00:00:00Z", ISODateTimeFormat.dateTimeParser()))
      sku.stopDate must beSome(DateTime.parse("2014-12-31T23:59:00Z", ISODateTimeFormat.dateTimeParser()))
    }

    "Retrieve a product and sku from a unknown sku's id" in {
      val productAndSku = ProductDao.getProductAndSku(STORE, 0)
      productAndSku must beNone
    }
  }
}
