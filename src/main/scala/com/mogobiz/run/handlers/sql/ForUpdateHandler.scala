package com.mogobiz.run.handlers.sql

import com.mogobiz.run.model.Mogobiz.Sku
import org.joda.time.DateTime
import scalikejdbc._

class ForUpdateHandler {

  def selectStockCalendarBySkuAndNullDate(sku: Sku) = {
    sql"""select * from stock_calendar where ticket_type_fk=${sku.id} and start_date is null for update"""
  }

  def selectStockCalendarBySkuAndDate(sku: Sku, date:DateTime) = {
    sql"""select * from stock_calendar where ticket_type_fk=${sku.id} and start_date=${date} for update"""
  }
}
