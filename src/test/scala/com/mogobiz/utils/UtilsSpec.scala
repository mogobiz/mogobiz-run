/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.utils

import com.mogobiz.MogobizRouteTest
import com.mogobiz.run.handlers.ProductDao
import com.mogobiz.run.utils.Utils
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.{DateTime, DateTimeZone}

class UtilsSpec extends MogobizRouteTest {

  "Utils " should "verifyAndExtractStartEndDate for no date product without date" in {
    val productAndSku = ProductDao.getProductAndSku(STORE, 63)
    val res = Utils.verifyAndExtractStartEndDate(productAndSku.get._1, productAndSku.get._2, None)
    res should be(None)
  }

  it should "verifyAndExtractStartEndDate for no date product with date now" in {
    val productAndSku = ProductDao.getProductAndSku(STORE, 63)
    val date = Some(DateTime.now)
    val res = Utils.verifyAndExtractStartEndDate(productAndSku.get._1, productAndSku.get._2, date)
    res should be(None)
  }

  it should "verifyAndExtractStartEndDate for DATE_ONLY product with accepted date and weekday" in {
    val productAndSku = ProductDao.getProductAndSku(STORE, 139)
    val date = Some(DateTime.parse("2014-10-04T16:00:00.000Z", ISODateTimeFormat.dateTimeParser()))
    val res = Utils.verifyAndExtractStartEndDate(productAndSku.get._1, productAndSku.get._2, date)
    res shouldBe a[Some[_]]
    res.get._1 should equal(DateTime.parse("2014-10-04T00:00:00.000Z", ISODateTimeFormat.dateTimeParser()).toDateTime(DateTimeZone.UTC))
    res.get._1 should equal(DateTime.parse("2014-10-04T00:00:00.000Z", ISODateTimeFormat.dateTimeParser()).toDateTime(DateTimeZone.UTC))
  }

  it should "verifyAndExtractStartEndDate for DATE_ONLY product with unaccepted weekday" in {
    val productAndSku = ProductDao.getProductAndSku(STORE, 139)
    val date = Some(DateTime.parse("2014-09-05T16:00:00.000Z", ISODateTimeFormat.dateTimeParser()))
    val res = Utils.verifyAndExtractStartEndDate(productAndSku.get._1, productAndSku.get._2, date)
    res should be(None)
  }

  it should "verifyAndExtractStartEndDate for DATE_ONLY product with unaccepted past date" in {
    val productAndSku = ProductDao.getProductAndSku(STORE, 139)
    val date = Some(DateTime.parse("2013-09-01T16:00:00.000Z", ISODateTimeFormat.dateTimeParser()))
    val res = Utils.verifyAndExtractStartEndDate(productAndSku.get._1, productAndSku.get._2, date)
    res should be(None)
  }

  it should "verifyAndExtractStartEndDate for DATE_ONLY product with unaccepted future date" in {
    val productAndSku = ProductDao.getProductAndSku(STORE, 139)
    val date = Some(DateTime.parse("2014-10-01T16:00:00.000Z", ISODateTimeFormat.dateTimeParser()))
    val res = Utils.verifyAndExtractStartEndDate(productAndSku.get._1, productAndSku.get._2, date)
    res should be(None)
  }

  it should "verifyAndExtractStartEndDate for DATE_TIME product with accepted date time and weekday" in {
    val productAndSku = ProductDao.getProductAndSku(STORE, 146)
    val date = Some(DateTime.parse("2014-10-04T16:00:00.000Z", ISODateTimeFormat.dateTimeParser()))
    val res = Utils.verifyAndExtractStartEndDate(productAndSku.get._1, productAndSku.get._2, date)
    res shouldBe a[Some[_]]
    res.get._1 should equal(DateTime.parse("2014-10-04T15:00:00.000Z", ISODateTimeFormat.dateTimeParser()))
    res.get._2 should equal(DateTime.parse("2014-10-04T17:00:00.000Z", ISODateTimeFormat.dateTimeParser()))
  }

  it should "verifyAndExtractStartEndDate for DATE_TIME product with unaccepted weekday" in {
    val productAndSku = ProductDao.getProductAndSku(STORE, 146)
    val date = Some(DateTime.parse("2014-09-05T16:00:00.000Z", ISODateTimeFormat.dateTimeParser()))
    val res = Utils.verifyAndExtractStartEndDate(productAndSku.get._1, productAndSku.get._2, date)
    res should be(None)
  }

  it should "verifyAndExtractStartEndDate for DATE_TIME product with unaccepted time" in {
    val productAndSku = ProductDao.getProductAndSku(STORE, 146)
    val date = Some(DateTime.parse("2014-09-06T10:00:00.000Z", ISODateTimeFormat.dateTimeParser()))
    val res = Utils.verifyAndExtractStartEndDate(productAndSku.get._1, productAndSku.get._2, date)
    res should be(None)
  }

  it should "verifyAndExtractStartEndDate for DATE_TIME product with unaccepted past date" in {
    val productAndSku = ProductDao.getProductAndSku(STORE, 146)
    val date = Some(DateTime.parse("2014-08-31T16:00:00.000Z", ISODateTimeFormat.dateTimeParser()))
    val res = Utils.verifyAndExtractStartEndDate(productAndSku.get._1, productAndSku.get._2, date)
    res should be(None)
  }

  it should "verifyAndExtractStartEndDate for DATE_TIME product with unaccepted future date" in {
    val productAndSku = ProductDao.getProductAndSku(STORE, 146)
    val date = Some(DateTime.parse("2015-01-03T16:00:00.000Z", ISODateTimeFormat.dateTimeParser()))
    val res = Utils.verifyAndExtractStartEndDate(productAndSku.get._1, productAndSku.get._2, date)
    res should be(None)
  }
}
