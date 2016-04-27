/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import com.mogobiz.MogobizRouteTest
import com.mogobiz.run.config.MogobizDBsWithEnv
import scalikejdbc._

class CouponHandlerSpec extends MogobizRouteTest {

  MogobizDBsWithEnv("test").setupAll()

  val service = new CouponHandler

  "CouponDao" should {

    "retrieve a coupon by code" in {
      val coupon = CouponDao.findByCode(STORE_ACMESPORT, "VIP50")

      coupon must beSome
      coupon.get.code mustEqual "VIP50"
    }
  }

  "CouponHandler" should {

    "be able to consumeCoupon" in {
      val coupon = CouponDao.findByCode(STORE_ACMESPORT,"VIP50")
      coupon must beSome

      val initConsumed = extractConsumed(STORE_ACMESPORT, "VIP50")
      initConsumed must beSome

      val consumption = service.consumeCoupon(STORE_ACMESPORT, coupon.get)
      consumption must beTrue
      val newConsumed = extractConsumed(STORE_ACMESPORT, "VIP50")
      newConsumed must beSome(initConsumed.get + 1)
    }

    "be able to releaseCoupon" in {
      val coupon = CouponDao.findByCode(STORE_ACMESPORT,"VIP50")
      coupon must beSome
      service.consumeCoupon(STORE_ACMESPORT, coupon.get) must beTrue

      val initConsumed = extractConsumed(STORE_ACMESPORT, "VIP50")

      service.releaseCoupon(STORE_ACMESPORT, coupon.get)
      val newConsumed = extractConsumed(STORE_ACMESPORT, "VIP50")
      newConsumed must beSome(initConsumed.get - 1)
    }

  }

  private def extractConsumed(storeCode: String, couponCode: String) : Option[Long] = DB readOnly { implicit session =>
    sql""" select c.consumed from coupon c where c.code=${couponCode} and company_fk in (select c.id from company c where c.code = ${storeCode}) """.map(rs => rs.long(1)).first().apply()
  }

}
