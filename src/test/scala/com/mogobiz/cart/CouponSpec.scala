package com.mogobiz.cart

import org.specs2.mutable.Specification
import java.util.{Locale, UUID}
import scalikejdbc.config.DBs
import scalikejdbc.DB

/**
 * Created by Christophe on 06/05/2014.
 */
class CouponSpec extends Specification {

  DBs.setupAll()


  "findByCode" in {
    val companyId = 8
    val code = "TEST1"
    val res = Coupon.findByCode(companyId,code)

    //res must beSome(coupon)
    res must beSome[Coupon]
    val coupon = res.get
    coupon.code must be_==(code)

  }

  "consumeCoupon" in {
    val companyId = 8
    val code = "TEST1"
    val res = Coupon.findByCode(companyId,code)

    //res must beSome(coupon)
    res must beSome[Coupon]
    val coupon = res.get
    coupon.code must be_==(code)

    val consumption = CouponService.consumeCoupon(coupon)
    consumption must beTrue

  }

  "releaseCoupon" in {
    val companyId = 8
    val code = "TEST1"
    val res = Coupon.findByCode(companyId,code)

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
