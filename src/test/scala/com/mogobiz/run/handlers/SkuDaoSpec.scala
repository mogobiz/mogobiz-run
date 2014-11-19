package com.mogobiz.run.handlers

import com.mogobiz.MogobizRouteTest
import org.joda.time.DateTime

/**
 * Created by yoannbaudy on 18/11/2014.
 */
class SkuDaoSpec extends MogobizRouteTest {

  "SkuDao" should {

    "retrieve sku by id" in {
      val skuOpt = SkuDao.get("mogobiz", 137)
      skuOpt must beSome
      val sku = skuOpt.get
      sku.id must be_==(137)
      sku.uuid must be_==("6e258455-1b6c-4c99-9dfb-324c7f9230ed")
      sku.sku must be_==("72b7f03f-9ddb-4890-9a91-1879ee3b6b91")
      sku.name must be_==("Child")
      sku.price must be_==(750)
      sku.minOrder must be_==(1)
      sku.maxOrder must be_==(10)
      sku.availabilityDate must beNone
      sku.startDate must beSome(DateTime.parse("2014-01-01T00:00:00"))
      sku.stopDate must beSome(DateTime.parse("2014-12-31T23:59:00"))
    }

    "not retrieve unknown sku" in {
      val sku = SkuDao.get("mogobiz", 0)
      sku must beNone
    }

  }
}
