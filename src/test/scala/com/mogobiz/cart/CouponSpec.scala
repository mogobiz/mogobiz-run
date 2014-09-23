package com.mogobiz.cart

import com.mogobiz.cart.domain.Coupon
import org.specs2.mutable.Specification
import scalikejdbc.DB
import scalikejdbc.config.DBs

class CouponSpec extends Specification {

  DBs.setupAll()
  val companyId = 9
  val companyCode = "mogobiz"


  "The Coupon DAO" should {
    "retrieve a coupon by its code" in {
    val code = "TEST1"
    val res = Coupon.findByCode(companyCode,code)

    res must beSome[Coupon]
    val coupon = res.get
    coupon.code must be_==(code)

    }

    "find the promotion available on the store" in {

      val promotions = Coupon.findPromotionsThatOnlyApplyOnCart(companyCode)

      promotions.size must be_==(1)
      promotions(0).active must beTrue
      promotions(0).anonymous must beTrue
      promotions(0).code must be_==("Promo1pour3")

    }

  }

  "the Coupon Service" should {

    "be able to consumeCoupon" in {
      val code = "TEST1"
      val res = Coupon.findByCode(companyCode,code)

      //res must beSome(coupon)
      res must beSome[Coupon]
      val coupon = res.get
      coupon.code must be_==(code)

      val consumption = CouponService.consumeCoupon(coupon)
      consumption must beTrue

    }

    "be able to releaseCoupon" in {
      val code = "TEST1"
      val res = Coupon.findByCode(companyCode,code)

      //res must beSome(coupon)
      res must beSome[Coupon]
      val coupon = res.get
      coupon.code must be_==(code)

      CouponService.consumeCoupon(coupon) must beTrue
      val reduc = DB readOnly{ implicit session => ReductionSold.get(coupon.reductionSoldFk.get).get }
      val soldBefore = reduc.sold
      CouponService.consumeCoupon(coupon) must beTrue
      CouponService.consumeCoupon(coupon) must beTrue
      CouponService.consumeCoupon(coupon) must beTrue
      CouponService.consumeCoupon(coupon) must beTrue

      val reduc2 = DB readOnly{ implicit session => ReductionSold.get(coupon.reductionSoldFk.get).get }
      reduc2.sold must be_==(soldBefore+4)
      CouponService.releaseCoupon(coupon)
      val reduc3 = DB readOnly{ implicit session => ReductionSold.get(coupon.reductionSoldFk.get).get }
      reduc3.sold must be_==(soldBefore+3)

    }

  }

}
