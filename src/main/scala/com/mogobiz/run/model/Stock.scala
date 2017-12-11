/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.model

import java.util.Date

import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import com.mogobiz.run.model.Mogobiz.ProductCalendar.ProductCalendar
import com.mogobiz.run.model.Mogobiz.ProductCalendarRef
import com.mogobiz.utils.GlobalUtil._
import org.joda.time.DateTime

/**
  *
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
    id: Long,
    uuid: String = newUUID,
    sku: String,
    productId: Long,
    productUuid: String,
    startDate: Option[DateTime],
    stopDate: Option[DateTime],
    availabilityDate: Option[DateTime],
    imported: DateTime,
    initialStock: Long,
    stockUnlimited: Boolean,
    stockOutSelling: Boolean,
    stockDisplay: Boolean,
    @JsonScalaEnumeration(classOf[ProductCalendarRef]) calendarType: ProductCalendar,
    stock: Option[Long],
    stockByDateTime: Option[Seq[StockByDateTime]],
    var lastUpdated: Date,
    var dateCreated: Date)

case class StockByDateTime(id: Long,
                           uuid: String,
                           var lastUpdated: Date,
                           var dateCreated: Date,
                           startDate: DateTime,
                           stock: Long)

/**
  *
  * @param id - id within sgbd
  * @param uuid -
  * @param dateCreated -
  * @param lastUpdated -
  * @param startDate -
  * @param stock - stock value
  */
case class StockCalendar(id: Long,
                         dateCreated: DateTime,
                         lastUpdated: DateTime,
                         productFk: Long,
                         sold: Long,
                         startDate: Option[DateTime],
                         stock: Long,
                         ticketTypeFk: Long,
                         uuid: String)
