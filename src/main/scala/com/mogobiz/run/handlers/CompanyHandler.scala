/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import com.mogobiz.es.EsClient
import com.mogobiz.run.model.Mogobiz.{Company,ShippingRule}
import com.sksamuel.elastic4s.ElasticDsl._

/**
 */
class CompanyHandler {

}

object CompanyDao {

  def findByCode(code:String):Option[Company]= {
    EsClient.load[Company](code, code, "company")
  }
}

object ShippingRuleDao {

  def findByCompany(storeCode:String) : List[ShippingRule] = {
    // Création de la requête
    val req = search in storeCode -> "shipping_rule" from 0 size EsClient.MAX_SIZE

    // Lancement de la requête
    EsClient.searchAll[ShippingRule](req).toList;
  }
}
