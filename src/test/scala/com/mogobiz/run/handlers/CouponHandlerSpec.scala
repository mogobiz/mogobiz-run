package com.mogobiz.run.handlers

import com.mogobiz.MogobizRouteTest
import com.mogobiz.run.config.MogobizDBsWithEnv
import scalikejdbc._

class CouponHandlerSpec extends MogobizRouteTest {

  MogobizDBsWithEnv("test").setupAll()

  val service = new CouponHandler

  "CouponDao" should {

    "retrieve a coupon by code" in {
      val coupon = CouponDao.findByCode(STORE,"TEST1")

      coupon must beSome
      coupon.get.code mustEqual "TEST1"
    }

    "find the promotion available on the store" in {

      val promotions = CouponDao.findPromotionsThatOnlyApplyOnCart(STORE)

      promotions.size must be_==(1)
      promotions(0).active must beTrue
      promotions(0).anonymous must beTrue
      promotions(0).code mustEqual "Promo1pour3"
    }
  }

  "CouponHandler" should {

    "be able to consumeCoupon" in {
      val coupon = CouponDao.findByCode(STORE,"TEST1")
      coupon must beSome

      val initConsumed = extractConsumed(STORE, "TEST1")
      initConsumed must beSome

      val consumption = service.consumeCoupon(STORE, coupon.get)
      consumption must beTrue
      val newConsumed = extractConsumed(STORE, "TEST1")
      newConsumed must beSome(initConsumed.get + 1)
    }

    "not be able to consumeCoupon if insufficient stock" in {
      val coupon = CouponDao.findByCode(STORE,"Promotion")
      coupon must beSome

      val consumption = service.consumeCoupon(STORE, coupon.get)
      consumption must beFalse
    }

    "be able to releaseCoupon" in {
      val coupon = CouponDao.findByCode(STORE,"TEST1")
      coupon must beSome
      service.consumeCoupon(STORE, coupon.get) must beTrue

      val initConsumed = extractConsumed(STORE, "TEST1")

      service.releaseCoupon(STORE, coupon.get)
      val newConsumed = extractConsumed(STORE, "TEST1")
      newConsumed must beSome(initConsumed.get - 1)
    }

  }

  private def extractConsumed(storeCode: String, couponCode: String) : Option[Long] = DB readOnly { implicit session =>
    sql""" select c.consumed from Coupon c where c.code=${couponCode} and company_fk in (select c.id from Company c where c.code = ${storeCode}) """.map(rs => rs.long(1)).first().apply()
  }

}
