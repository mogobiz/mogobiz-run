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
      sku.uuid mustEqual "4f48ff92-2bb3-4ad1-af29-112662b1a309"
      sku.sku mustEqual "16458153-e907-43f1-bd26-2b90b97dcde3"
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
