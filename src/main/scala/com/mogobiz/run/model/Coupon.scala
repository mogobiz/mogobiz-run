package com.mogobiz.run.model

import com.mogobiz.run.model.Mogobiz.ReductionRule
import org.joda.time.DateTime

case class Coupon(id: Long,
                  name: String,
                  code: String,
                  startDate: Option[DateTime],
                  endDate: Option[DateTime],
                  numberOfUses: Option[Long],
                  sold: Option[Long],
                  rules: List[ReductionRule],
                  active: Boolean,
                  anonymous: Boolean = false,
                  catalogWise: Boolean = false,
                  description: String,
                  pastille: String)

trait CartCoupon {
  def id: Long
  def code: String
}

trait CartCouponWithData extends CartCoupon {
  def id: Long
  def name: String
  def code: String
  def startDate: Option[DateTime]
  def endDate: Option[DateTime]
  def numberOfUses: Option[Long]
  def sold: Option[Long]
  def rules: List[ReductionRule]
  def active: Boolean
  def anonymous: Boolean
  def catalogWise: Boolean
  def description: String
  def pastille: String
  def promotion: Boolean
}

trait CartCouponWithPrice extends CartCouponWithData {
  def reduction: Long
}

case class StoreCartCoupon(id: Long, code: String) extends CartCoupon

case class CouponWithData(id: Long,
                          name: String,
                          code: String,
                          startDate: Option[DateTime],
                          endDate: Option[DateTime],
                          //@JsonDeserialize(contentAs = classOf[java.lang.Long])
                          numberOfUses: Option[Long],
                          //@JsonDeserialize(contentAs = classOf[java.lang.Long])
                          sold: Option[Long],
                          rules: List[ReductionRule],
                          active: Boolean,
                          anonymous: Boolean,
                          catalogWise: Boolean,
                          description: String,
                          pastille: String,
                          promotion: Boolean)
    extends CartCouponWithData {

  def this(coupon: Coupon, active: Boolean, promotion: Boolean) =
    this(
      coupon.id,
      coupon.name,
      coupon.code,
      coupon.startDate,
      coupon.endDate,
      coupon.numberOfUses,
      coupon.sold,
      coupon.rules,
      active,
      coupon.anonymous,
      coupon.catalogWise,
      coupon.description,
      coupon.pastille,
      promotion
    )

}

case class CouponWithPrices(id: Long,
                            name: String,
                            code: String,
                            startDate: Option[DateTime],
                            endDate: Option[DateTime],
                            numberOfUses: Option[Long],
                            sold: Option[Long],
                            rules: List[ReductionRule],
                            active: Boolean,
                            anonymous: Boolean,
                            catalogWise: Boolean,
                            description: String,
                            pastille: String,
                            promotion: Boolean,
                            reduction: Long)
    extends CartCouponWithPrice {

  def this(coupon: CartCouponWithData, reduction: Long) =
    this(
      coupon.id,
      coupon.name,
      coupon.code,
      coupon.startDate,
      coupon.endDate,
      coupon.numberOfUses,
      coupon.sold,
      coupon.rules,
      coupon.active,
      coupon.anonymous,
      coupon.catalogWise,
      coupon.description,
      coupon.pastille,
      coupon.promotion,
      reduction
    )

}
