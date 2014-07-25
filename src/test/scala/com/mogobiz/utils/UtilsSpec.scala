package com.mogobiz.utils

import com.mogobiz.cart.TicketType
import org.joda.time.DateTime
import org.specs2.mutable.Specification
import scalikejdbc.config.DBs

/**
 * Created by Christophe on 15/07/2014.
 */
class UtilsSpec  extends Specification  {

  DBs.setupAll()

  "no ticket type, no date" in {
    val res = Utils.verifyAndExtractStartEndDate(None,None)
    res._1 must beNone
    res._2 must beNone
  }

  "no ticket type, a date" in {
    val date = Some(DateTime.now)
    val res = Utils.verifyAndExtractStartEndDate(None,date)
    res._1 must beNone
    res._2 must beNone
  }

  "some ticket type, no date" in {
    val ticketType = TicketType.get(65)
    val res = Utils.verifyAndExtractStartEndDate(Some(ticketType),None)
    res._1 must beNone
    res._2 must beNone
  }

  "a NO_DATE ticket type, date is now" in {
    val ticketType = TicketType.get(65)
    val date = Some(DateTime.now)
    val res = Utils.verifyAndExtractStartEndDate(Some(ticketType),date)
    res._1 must beNone
    res._2 must beNone
  }

  "a DATE_ONLY ticket type, date is now" in {
    val ticketType = TicketType.get(121)
    val date = Some(DateTime.now)
    val res = Utils.verifyAndExtractStartEndDate(Some(ticketType),date)
    res._1 must beSome[DateTime]
    res._2 must beSome[DateTime]
  }

  "a DATE_ONLY ticket type, date is out of range (old)" in {
    val ticketType = TicketType.get(121)
    val date = Some(new DateTime(2013,10,1,0,0))
    val res = Utils.verifyAndExtractStartEndDate(Some(ticketType),date)
    res._1 must beNone
    res._2 must beNone
  }

  "a DATE_ONLY ticket type, date is out of range (future)" in {
    val ticketType = TicketType.get(121)
    val date = Some(new DateTime(2015,10,1,0,0))
    val res = Utils.verifyAndExtractStartEndDate(Some(ticketType),date)
    res._1 must beNone
    res._2 must beNone
  }

  "a DATE_TIME ticket type, with the right date & time" in {
    val ticketType = TicketType.get(126)
    val date = Some(new DateTime(2014,5,1,15,0))
    val res = Utils.verifyAndExtractStartEndDate(Some(ticketType),date)
    res._1 must beSome[DateTime]
    res._2 must beSome[DateTime]
  }

  "a DATE_TIME ticket type, with on old date & time" in {
    val ticketType = TicketType.get(126)
    val date = Some(new DateTime(2014,2,1,15,0))
    val res = Utils.verifyAndExtractStartEndDate(Some(ticketType),date)
    res._1 must beNone
    res._2 must beNone
  }

  "a DATE_TIME ticket type, with on future date & time" in {
    val ticketType = TicketType.get(126)
    val date = Some(new DateTime(2014,11,1,15,0))
    val res = Utils.verifyAndExtractStartEndDate(Some(ticketType),date)
    res._1 must beNone
    res._2 must beNone
  }

}