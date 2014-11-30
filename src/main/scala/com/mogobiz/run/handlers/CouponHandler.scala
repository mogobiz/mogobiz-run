package com.mogobiz.run.handlers

import com.mogobiz.es.EsClient
import com.mogobiz.run.model.Mogobiz.{ReductionRuleType, ReductionRule}
import com.mogobiz.run.model.{Mogobiz, Render, StoreCoupon}
import com.sksamuel.elastic4s.ElasticDsl._
import scalikejdbc._

/**
 * Created by yoannbaudy on 24/11/2014.
 */
class CouponHandler {

  def releaseCoupon(storeCode: String, coupon: Mogobiz.Coupon): Unit = {
    DB localTx { implicit s =>
      sql""" update Coupon set consumed=consumed-1 where code=${coupon.code} and consumed > 0 and company_fk in (select c.id from Company c where c.code = ${storeCode}) """.update().apply()
    }
  }

  def consumeCoupon(storeCode: String, coupon: Mogobiz.Coupon): Boolean = {
    val numberModifyLine = DB localTx { implicit s =>
      sql""" update Coupon set consumed=consumed+1 where code=${coupon.code} and (number_of_uses is null or number_of_uses > consumed) and company_fk in (select c.id from Company c where c.code = ${storeCode}) """.update().apply()
    }
    numberModifyLine > 0
  }

  def computePromotions(storeCode: String, coupons: List[Mogobiz.Coupon], cartItems: List[Render.CartItem]): ReductionAndCouponsList = {
    if (coupons.isEmpty) new ReductionAndCouponsList(0, List())
    else {
      val coupon = coupons.head
      val couponVO = _computeCoupon(storeCode, coupon, cartItems)
      val reductionAndCouponsList = computePromotions(storeCode, coupons.tail, cartItems)
      new ReductionAndCouponsList(reductionAndCouponsList.reduction + couponVO.price, couponVO :: reductionAndCouponsList.coupons)
    }
  }

  def computeCoupons(storeCode: String, coupons: List[StoreCoupon], cartItems: List[Render.CartItem]): ReductionAndCouponsList = {
    if (coupons.isEmpty) new ReductionAndCouponsList(0, List())
    else {
      val coupon = CouponDao.findByCode(storeCode, coupons.head.code).get
      val couponVO = _computeCoupon(storeCode, coupon, cartItems)
      val reductionAndCouponsList = computeCoupons(storeCode, coupons.tail, cartItems)
      new ReductionAndCouponsList(reductionAndCouponsList.reduction + couponVO.price, couponVO :: reductionAndCouponsList.coupons)
    }
  }

  private def _computeCoupon(storeCode: String, coupon: Mogobiz.Coupon, cartItems: List[Render.CartItem]): Render.Coupon = {
    if (_isCouponActive(coupon)) {
      val skusIdList = ProductDao.getSkusIdByCoupon(storeCode, coupon.id)

      if (skusIdList.size > 0) {
        case class QuantityAndPrices(quantity: Long, minPrice: Option[Long], totalPrice: Long)

        // Méthode de calcul de la quantity et du prix à utiliser pour les règles
        def _searchReductionQuantityAndPrice(cartItems: List[Render.CartItem]) : QuantityAndPrices = {
          if (cartItems.isEmpty) new QuantityAndPrices(0, None, 0)
          else {
            val cartItem = cartItems.head
            if (skusIdList.contains(cartItem.skuId)) {
              val quantityAndPrice = _searchReductionQuantityAndPrice(cartItems.tail)

              val cartItemPrice = cartItem.saleEndPrice.getOrElse(cartItem.salePrice)

              val quantity = quantityAndPrice.quantity + cartItem.quantity
              val minPrice = if (cartItemPrice > 0) Some(Math.min(quantityAndPrice.minPrice.getOrElse(java.lang.Long.MAX_VALUE), cartItemPrice))
              else quantityAndPrice.minPrice
              val totalPrice = quantityAndPrice.totalPrice + cartItem.saleTotalEndPrice.getOrElse(0l)

              new QuantityAndPrices(quantity, minPrice, totalPrice)
            }
            else _searchReductionQuantityAndPrice(cartItems.tail)
          }
        }

        val quantityAndPrice = _searchReductionQuantityAndPrice(cartItems)
        if (quantityAndPrice.quantity > 0) {
          // Calcul la sommes des réductions de chaque règle du coupon
          def _calculateReduction(rules: List[ReductionRule]) : Long = {
            if (rules.isEmpty) 0
            else _calculateReduction(rules.tail) + _applyReductionRule(rules.head, quantityAndPrice.quantity, quantityAndPrice.minPrice.getOrElse(0), quantityAndPrice.totalPrice)
          }

          val couponPrice = _calculateReduction(coupon.rules)
          _createCouponVOFromCoupon(coupon, true, couponPrice)
        }
        else {
          // Pas de quantité, cela signifie que la réduction ne s'applique pas donc prix = 0
          _createCouponVOFromCoupon(coupon, true, 0)
        }
      }
      else {
        // Le coupon ne s'applique sur aucun SKU, donc prix = 0
        _createCouponVOFromCoupon(coupon, true, 0)
      }
    }
    else {
      // Le coupon n'est pas actif, donc prix = 0 et active = false
      _createCouponVOFromCoupon(coupon, false, 0)
    }
  }
  private def _createCouponVOFromCoupon(coupon: Mogobiz.Coupon, active: Boolean, price: Long) : Render.Coupon = {
    Render.Coupon(id = coupon.id, name = coupon.name, code = coupon.code, startDate = coupon.startDate, endDate = coupon.endDate, active = active, price = price)
  }

  private def _isCouponActive(coupon : Mogobiz.Coupon) : Boolean = {
    if (coupon.startDate.isEmpty && coupon.endDate.isEmpty) true
    else if (coupon.startDate.isDefined && coupon.endDate.isEmpty) coupon.startDate.get.isBeforeNow || coupon.startDate.get.isEqualNow
    else if (coupon.startDate.isEmpty && coupon.endDate.isDefined) coupon.endDate.get.isAfterNow || coupon.endDate.get.isEqualNow
    else (coupon.startDate.get.isBeforeNow || coupon.startDate.get.isEqualNow) && (coupon.endDate.get.isAfterNow || coupon.endDate.get.isEqualNow)
  }

  /**
   * Applique la règle sur le montant total (DISCOUNT) ou sur le montant uniquement en fonction de la quantité (X_PURCHASED_Y_OFFERED)
   * @param rule : règle à appliquer
   * @param quantity : quantité
   * @param price : prix unitaire
   * @param totalPrice : prix total
   * @return
   */
  private def _applyReductionRule(rule: ReductionRule, quantity: Long, price: Long, totalPrice: Long) : Long = {
    rule.xtype match {
      case ReductionRuleType.DISCOUNT =>
        computeDiscount(rule.discount, totalPrice)
      case ReductionRuleType.X_PURCHASED_Y_OFFERED =>
        val multiple = quantity / rule.xPurchased.getOrElse(1L)
        price * rule.yOffered.getOrElse(1L) * multiple
      case _ => 0L
    }
  }

  /**
   *
   * @param discountRule
   * @param prixDeBase
   * @return
   */
  private def computeDiscount(discountRule: Option[String], prixDeBase: Long) : Long = {
    discountRule match {
      case Some(regle) => {
        if (regle.endsWith("%")) {
          val pourcentage = java.lang.Float.parseFloat(regle.substring(0, regle.length() - 1))
          (prixDeBase * pourcentage / 100).toLong //TODO recheck the rounded value computed
        }
        else if (regle.startsWith("+")) {

          val increment = java.lang.Long.parseLong(regle.substring(1))
          prixDeBase + increment
        }
        else if (regle.startsWith("-")) {
          val decrement = java.lang.Long.parseLong(regle.substring(1))
          prixDeBase - decrement
        }
        else {
          java.lang.Long.parseLong(regle)
        }
      }
      case None => prixDeBase
    }
  }

}

case class ReductionAndCouponsList(reduction: Long, coupons: List[Render.Coupon])

object CouponDao {

  def findByCode(storeCode:String, couponCode:String):Option[Mogobiz.Coupon]={
    // Création de la requête
    val req = search in storeCode -> "coupon" filter termFilter("coupon.code.raw", couponCode)

    // Lancement de la requête
    EsClient.search[Mogobiz.Coupon](req);
  }

  def findPromotionsThatOnlyApplyOnCart(storeCode:String):List[Mogobiz.Coupon]={
    // Création de la requête
    val req = search in storeCode -> "coupon" filter
    and (
      termFilter("coupon.anonymous", true),
      termFilter("coupon.rules.xtype", "X_PURCHASED_Y_OFFERED"),
      or(
        and(missingFilter("coupon.start_date"), missingFilter("coupon.end_date")),
        and(rangeFilter("coupon.start_date").lte("now"), rangeFilter("coupon.end_date").gte("now"))
      )
    )

    // Lancement de la requête
    EsClient.searchAll[Mogobiz.Coupon](req).toList;
  }
}