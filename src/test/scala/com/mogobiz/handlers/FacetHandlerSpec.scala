package com.mogobiz.handlers

import com.mogobiz.model.FacetRequest
import org.specs2.mutable.Specification

class FacetHandlerSpec extends Specification {

  val handler = new FacetHandler

  val storeCode = "mogobiz"


  "the facet handler" should {
    "send back aggregation with nested features" in {
      val req = FacetRequest(priceInterval = 5000, lang = "fr", None, None, None, None, None, None)

      val res = handler.getProductCriteria(storeCode, req)

      println(res)

      res must_!=(null)
    }
  }

}
