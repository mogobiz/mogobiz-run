/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.services

import com.mogobiz.MogobizRouteTest
import org.specs2.matcher._
import org.json4s.native.JsonParser
import org.json4s.JsonAST._

class CurrencyServiceSpec extends MogobizRouteTest {

  "The Currency service" should {
    "return currencies" in {
      Get("/store/" + STORE + "/currencies") ~> sealRoute(routes) ~> check {
        val currencies: List[JValue] = checkJArray(JsonParser.parse(responseAs[String]))
        currencies must have size 2

        checkCurrencyGBP(currencies(0))
        checkCurrencyEuro(currencies(1))
      }
    }
  }

  def checkCurrencyEuro(currency: JValue): MatchResult[JValue] = {

    currency \ "code" must be_==(JString("EUR"))
    currency \ "name" must be_==(JString("EUR"))
    currency \ "rate" must be_==(JDouble(0.01))
    currency \ "currencyFractionDigits" must be_==(JInt(2))

  }

  def checkCurrencyGBP(currency: JValue): MatchResult[JValue] = {
    currency \ "code" must be_==(JString("GBP"))
    currency \ "name" must be_==(JString("GBP"))
    currency \ "rate" must be_==(JDouble(0.00829348))
    currency \ "currencyFractionDigits" must be_==(JInt(2))

  }
}