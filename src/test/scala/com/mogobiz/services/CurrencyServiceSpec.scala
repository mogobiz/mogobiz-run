/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.services

import com.mogobiz.MogobizRouteTest
import org.json4s.JsonAST._
import org.json4s.native.JsonParser

class CurrencyServiceSpec extends MogobizRouteTest {

  "The Currency service" should "return currencies" in {
      Get("/store/" + STORE + "/currencies") ~> sealRoute(routes) ~> check {
        val currencies: List[JValue] = checkJArray(JsonParser.parse(responseAs[String]))
        currencies should have size 2
        checkCurrencyGBP(currencies(0))
        checkCurrencyEuro(currencies(1))
      }
    }

  def checkCurrencyEuro(currency: JValue) = {

    currency \ "code" should be(JString("EUR"))
    currency \ "name" should be(JString("EUR"))
    currency \ "rate" should be(JDouble(0.01))
    currency \ "currencyFractionDigits" should be(JInt(2))
  }

  def checkCurrencyGBP(currency: JValue) = {
    currency \ "code" should be(JString("GBP"))
    currency \ "name" should be(JString("GBP"))
    currency \ "rate" should be(JDouble(0.00829348))
    currency \ "currencyFractionDigits" should be(JInt(2))

  }
}