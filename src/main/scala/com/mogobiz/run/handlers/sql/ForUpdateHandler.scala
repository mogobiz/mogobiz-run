/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers.sql

import com.mogobiz.run.model.Mogobiz.Sku
import org.joda.time.DateTime
import scalikejdbc._

class ForUpdateHandler {

  def selectStockCalendarBySkuAndNullDate(sku: Sku, lock: Boolean) = {
    if (lock)
      sql"""select * from stock_calendar where ticket_type_fk=${sku.id} and start_date is null for update"""
    else
      sql"""select * from stock_calendar where ticket_type_fk=${sku.id} and start_date is null"""
  }

  def selectStockCalendarBySkuAndDate(sku: Sku, date: DateTime, lock: Boolean) = {
    if (lock)
      sql"""select * from stock_calendar where ticket_type_fk=${sku.id} and start_date=${date} for update"""
    else
      sql"""select * from stock_calendar where ticket_type_fk=${sku.id} and start_date=${date}"""
  }
}
