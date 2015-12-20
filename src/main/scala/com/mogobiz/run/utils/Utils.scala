/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.utils

import com.mogobiz.run.model.Mogobiz.{ ProductCalendar, IntraDayPeriod, Sku, Product }
import org.joda.time._
import scalikejdbc._

/**
 *
 */
object Utils {

  def remove[A](list: List[A], item: A): List[A] = {
    if (list.isEmpty) list
    else if (list.head == item) list.tail
    else remove(list.tail, item) ::: List(list.head)
  }

  def printJSON(o: Object) = {
    import org.json4s.JsonDSL._
    import org.json4s.native.JsonMethods._
    import org.json4s.native.Serialization._
    import org.json4s.{ DefaultFormats, Formats }

    implicit def json4sFormats: Formats = DefaultFormats
    println("-----------------------------------------------------------------------------------------------")
    val prettyJson = pretty(render(write(o)))
    println(prettyJson)
    println("-----------------------------------------------------------------------------------------------")

  }

  def computeAge(birthDate: Option[DateTime]): Int = {
    if (birthDate.isDefined) {
      val period = new Period(birthDate.get.toLocalDate, DateTime.now().toLocalDate, PeriodType.years());
      return period.getYears
    } else 0
  }

  /**
   * Cette méthode permet de récupérer la date de début et de fin d'utilisation d'un ticket à partir de la date
   * choisie par le client et de la configuration du produit et du TicketType.
   * Si la date n'est pas valide, la méthode renvoie null sinon elle renvoie un tableau de 2 dates, la première étant
   * la date de début et la seconde étant la date de fin
   * @param product : Product
   * @param sku : Sku
   * @param optDate
   * @return
   */
  def verifyAndExtractStartEndDate(product: Product, sku: Sku, optDate: Option[DateTime]): Option[(DateTime, DateTime)] = {
    if (optDate.isDefined) {
      val date = optDate.get.toDateTime(DateTimeZone.UTC)
      val localDate = date.toLocalDate
      val start = sku.startDate.getOrElse(DateTime.now()).toDateTime(DateTimeZone.UTC).toLocalDate
      val stop = sku.stopDate.map { date => date.toDateTime(DateTimeZone.UTC).toLocalDate }

      // On controle maintenant la date avec le calendrier du produit
      product.calendarType match {
        case ProductCalendar.NO_DATE => None // Pas de calendrier donc pas de date
        case ProductCalendar.DATE_ONLY => {
          // On cherche d'abord que la date n'est pas dans une période d'exclusion
          if (isExcludeDate(product, date)) None
          else {
            // On cherche si la date est dans une période autorisée
            val intraDatePeriod = product.intraDayPeriods.getOrElse(List()).find { period =>
              {
                val start = period.startDate.toLocalDate
                val end = period.endDate.toLocalDate
                checkWeekDay(period, date) && (start.isBefore(localDate) || start.isEqual(localDate)) && (end.isAfter(localDate) || end.isEqual(localDate))
              }
            }
            if (intraDatePeriod.isDefined) {
              val d = date.withTimeAtStartOfDay()
              Some(d, d)
            } else None
          }
        }
        case ProductCalendar.DATE_TIME => {
          // On charche d'abord que la date n'est pas dans une période d'exlusion
          if (isExcludeDate(product, date)) None
          else {
            // On cherche si la date et l'heure (et les minutes) sont autorisée
            val localTime = date.toLocalTime.withMillisOfSecond(0).withSecondOfMinute(0)
            val intraDatePeriod = product.intraDayPeriods.getOrElse(List()).find { period =>
              {
                val startDate = period.startDate.toDateTime(DateTimeZone.UTC).toLocalDate
                val startTime = period.startDate.toDateTime(DateTimeZone.UTC).toLocalTime.withMillisOfSecond(0).withSecondOfMinute(0)
                val endDate = period.endDate.toDateTime(DateTimeZone.UTC).toLocalDate
                val endTime = period.endDate.toDateTime(DateTimeZone.UTC).toLocalTime.withMillisOfSecond(0).withSecondOfMinute(0)
                checkWeekDay(period, date) &&
                  (startDate.isBefore(localDate) || startDate.isEqual(localDate)) &&
                  (endDate.isAfter(localDate) || endDate.isEqual(localDate)) &&
                  (startTime.isBefore(localTime) || startTime.isEqual(localTime)) &&
                  (endTime.isAfter(localTime) || endTime.isEqual(localTime))
              }
            }
            if (intraDatePeriod.isDefined) {
              val startTime = intraDatePeriod.get.startDate.toLocalTime
              val endTime = intraDatePeriod.get.endDate.toLocalTime
              val start = date.withHourOfDay(startTime.getHourOfDay).withMinuteOfHour(startTime.getMinuteOfHour)
              val end = date.withHourOfDay(endTime.getHourOfDay).withMinuteOfHour(endTime.getMinuteOfHour)
              Some((start, end))
            } else None
          }
        }
      }
    } else None
  }

  private def isExcludeDate(product: Product, date: DateTime): Boolean = {
    val localDate = date.toLocalDate
    product.datePeriods.getOrElse(List()).find { period =>
      {
        val start = period.startDate.toLocalDate
        val end = period.endDate.toLocalDate
        (start.isBefore(localDate) || start.isEqual(localDate)) && (end.isAfter(localDate) || end.isEqual(localDate))
      }
    }.isDefined
  }

  private def checkWeekDay(intraDayPeriod: IntraDayPeriod, date: DateTime): Boolean = {
    date.dayOfWeek().get() match {
      case DateTimeConstants.MONDAY => intraDayPeriod.weekday1
      case DateTimeConstants.TUESDAY => intraDayPeriod.weekday2
      case DateTimeConstants.WEDNESDAY => intraDayPeriod.weekday3
      case DateTimeConstants.THURSDAY => intraDayPeriod.weekday4
      case DateTimeConstants.FRIDAY => intraDayPeriod.weekday5
      case DateTimeConstants.SATURDAY => intraDayPeriod.weekday6
      case DateTimeConstants.SUNDAY => intraDayPeriod.weekday7
      case _ => false
    }
  }
}

/**
 * @see http://jayconrod.com/posts/32/convenient-updates-for-immutable-objects-in-scala
 * @tparam T
 */
trait Copying[T] {
  def copyWith(changes: (String, AnyRef)*): T = {
    val clas = getClass
    val constructor = clas.getDeclaredConstructors.head
    val argumentCount = constructor.getParameterTypes.size
    val fields = clas.getDeclaredFields
    val arguments = (0 until argumentCount) map { i =>
      val fieldName = fields(i).getName
      changes.find(_._1 == fieldName) match {
        case Some(change) => change._2
        case None =>
          val getter = clas.getMethod(fieldName)
          getter.invoke(this)
      }
    }
    constructor.newInstance(arguments: _*).asInstanceOf[T]
  }
}
