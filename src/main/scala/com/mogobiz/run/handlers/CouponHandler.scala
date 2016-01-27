/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import com.mogobiz.es.EsClient
import com.mogobiz.run.model.Mogobiz.{ ReductionRuleType, ReductionRule }
import com.mogobiz.run.model._
import com.sksamuel.elastic4s.ElasticDsl._
import org.json4s.JsonAST.{ JNothing, JObject, JValue }
import scalikejdbc._
import com.mogobiz.es._

/**
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

  def computeCouponPriceForCartItem(storeCode: String, coupon: CouponWithData, cartItem: StoreCartItemWithPrice): Long = {
    if (coupon.active) {
      val skusIdList = ProductDao.getSkusIdByCoupon(storeCode, coupon.coupon.id)

      if (skusIdList.size > 0 && skusIdList.contains(cartItem.cartItem.skuId)) {
        val price = cartItem.saleEndPrice.getOrElse(cartItem.salePrice)
        val totalPrice = cartItem.saleTotalEndPrice.getOrElse(cartItem.saleTotalPrice)
        _applyReductionRules(coupon.coupon.rules, cartItem.quantity, price, totalPrice)
      } else {
        // Le coupon ne s'applique sur aucun SKU, donc prix = 0
        0
      }
    } else {
      // Le coupon n'est pas actif, donc prix = 0 et active = false
      0
    }
  }

  def getWithData(storeCode: String, storeCoupon: StoreCoupon): CouponWithData = {
    val coupon = CouponDao.findByCode(storeCode, storeCoupon.code).get
    CouponWithData(coupon, _isCouponActive(coupon), 0, true)
  }

  def getWithData(coupon: Mogobiz.Coupon): CouponWithData = {
    CouponWithData(coupon, _isCouponActive(coupon), 0, true)
  }

  def transformAsRender(storeCode: String, coupon: CouponWithData): Option[Render.Coupon] = {
    CouponDao.findByCodeAsJSon(storeCode, coupon.coupon.code) match {
      case jobject: JObject => {
        Some(new Render.Coupon(jobject.obj, coupon.active, coupon.reduction, coupon.promotion))
      }
      case _ => None
    }
  }

  private def _isCouponActive(coupon: Mogobiz.Coupon): Boolean = {
    if (coupon.startDate.isEmpty && coupon.endDate.isEmpty) true
    else if (coupon.startDate.isDefined && coupon.endDate.isEmpty) coupon.startDate.get.isBeforeNow || coupon.startDate.get.isEqualNow
    else if (coupon.startDate.isEmpty && coupon.endDate.isDefined) coupon.endDate.get.isAfterNow || coupon.endDate.get.isEqualNow
    else (coupon.startDate.get.isBeforeNow || coupon.startDate.get.isEqualNow) && (coupon.endDate.get.isAfterNow || coupon.endDate.get.isEqualNow)
  }

  private def _applyReductionRules(rules: List[ReductionRule], quantity: Long, price: Long, totalPrice: Long): Long = {
    if (rules.isEmpty) 0
    else _applyReductionRules(rules.tail, quantity, price, totalPrice) + _applyReductionRule(rules.head, quantity, price, totalPrice)
  }

  /**
   * Applique la règle sur le montant total (DISCOUNT) ou sur le montant uniquement en fonction de la quantité (X_PURCHASED_Y_OFFERED)
   * @param rule : règle à appliquer
   * @param quantity : quantité
   * @param price : prix unitaire
   * @param totalPrice : prix total
   * @return
   */
  private def _applyReductionRule(rule: ReductionRule, quantity: Long, price: Long, totalPrice: Long): Long = {
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
  def computeDiscount(discountRule: Option[String], prixDeBase: Long): Long = {
    discountRule match {
      case Some(regle) => {
        if (regle.endsWith("%")) {
          val pourcentage = java.lang.Float.parseFloat(regle.substring(0, regle.length() - 1))
          (prixDeBase * pourcentage / 100).toLong //TODO recheck the rounded value computed
        } else if (regle.startsWith("+")) -java.lang.Long.parseLong(regle.substring(1))
        else if (regle.startsWith("-")) java.lang.Long.parseLong(regle.substring(1))
        else 0
      }
      case None => 0L
    }
  }

}

case class CouponWithData(val coupon: Mogobiz.Coupon, val active: Boolean, val reduction: Long, val promotion: Boolean)

object CouponDao {

  def findByCodeAsJSon(storeCode: String, couponCode: String): JValue = {
    // Création de la requête
    val req = search in storeCode -> "coupon" postFilter termFilter("coupon.code.raw", couponCode)

    // Lancement de la requête
    val coupon: JValue = EsClient.searchRaw(req) match {
      case Some(jobject) => jobject
      case _ => JNothing
    }
    coupon
  }

  def findByCode(storeCode: String, couponCode: String): Option[Mogobiz.Coupon] = {
    // Création de la requête
    val req = search in storeCode -> "coupon" postFilter termFilter("coupon.code.raw", couponCode)

    // Lancement de la requête
    EsClient.search[Mogobiz.Coupon](req);
  }

  def findPromotionsThatOnlyApplyOnCart(storeCode: String): List[Mogobiz.Coupon] = {
    // Création de la requête
    val req = search in storeCode -> "coupon" postFilter
      and(
        termFilter("coupon.anonymous", true),
        termFilter("coupon.rules.xtype", "X_PURCHASED_Y_OFFERED"),
        or(
          and(missingFilter("coupon.start_date"), missingFilter("coupon.end_date")),
          and(rangeFilter("coupon.start_date").lte("now"), rangeFilter("coupon.end_date").gte("now"))
        )
      ) from 0 size EsClient.MAX_SIZE

    // Lancement de la requête
    EsClient.searchAll[Mogobiz.Coupon](req).toList;
  }
}
