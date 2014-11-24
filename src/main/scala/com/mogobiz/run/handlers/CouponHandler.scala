package com.mogobiz.run.handlers

import com.mogobiz.es.EsClient
import com.mogobiz.run.model.Mogobiz
import com.mogobiz.run.model.Mogobiz.Coupon
import com.sksamuel.elastic4s.ElasticDsl._

/**
 * Created by yoannbaudy on 24/11/2014.
 */
class CouponHandler {

}

object CouponDao {

  def findByCode(storeCode:String, couponCode:String):Option[Coupon]={
    // Création de la requête
    val req = search in storeCode -> "coupong" filter termFilter("coupon.code", couponCode)

    // Lancement de la requête
    EsClient.search[Coupon](req);
  }

}