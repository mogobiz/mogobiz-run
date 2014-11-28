package com.mogobiz.run.handlers

import com.mogobiz.es.EsClient
import com.mogobiz.run.model.Mogobiz.Company
import com.sksamuel.elastic4s.ElasticDsl._

/**
 * Created by yoannbaudy on 27/11/2014.
 */
class CompanyHandler {

}

object CompanyDao {

  def findByCode(code:String):Option[Company]= {
    EsClient.load[Company](code, code, "company")
  }
}
