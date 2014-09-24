package com.mogobiz.model

import com.mogobiz.utils.GlobalUtil._
import java.util.Date

/**
 *
 * Created by smanciot on 24/09/14.
 */

/**
 *
 * @param id - id within sgbd
 * @param uuid - id within elastic search
 * @param sku - sku id
 * @param startDate - sku start date
 * @param stopDate - sku stop date
 * @param availabilityDate -
 * @param dateCreated - sku creation date
 * @param lastUpdated -
 * @param initialStock - initial stock value
 * @param stockUnlimited - whether stock is unlimited or not
 * @param stockOutSelling -
 * @param stockDisplay - whether to display stock or not
 * @param calendarType -
 * @param stock - stock value
 * @param stockByDateTime -
 */
case class Stock(
                  id:Long,
                  uuid:String = newUUID,
                  sku:String,
                  startDate:Date,
                  stopDate:Date,
                  availabilityDate:Option[Date],
                  dateCreated:Date = new Date(),
                  lastUpdated:Date = new Date(),
                  initialStock:Long,
                  stockUnlimited:Boolean,
                  stockOutSelling:Boolean,
                  stockDisplay:Boolean,
                  calendarType:Option[String],
                  stock:Option[Long],
                  stockByDateTime:Seq[StockCalendar])

/**
 *
 * @param id - id within sgbd
 * @param uuid -
 * @param dateCreated -
 * @param lastUpdated -
 * @param startDate -
 * @param stock - stock value
 */
case class StockCalendar(
                          id:Long,
                          uuid:String = newUUID,
                          dateCreated:Date = new Date(),
                          lastUpdated:Date = new Date(),
                          startDate:Date,
                          stock:Long)
