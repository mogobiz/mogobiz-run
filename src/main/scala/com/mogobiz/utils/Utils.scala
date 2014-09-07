package com.mogobiz.utils

import com.mogobiz.cart.{ProductCalendar, TicketType}
import org.joda.time.{DateTime, LocalTime}
import scalikejdbc._

/**
 * Created by Christophe on 24/04/2014.
 */
object Utils {

  def printJSON(o:Object)={
    import org.json4s.JsonDSL._
    import org.json4s.native.JsonMethods._
    import org.json4s.native.Serialization._
    import org.json4s.{DefaultFormats, Formats}

    implicit def json4sFormats: Formats = DefaultFormats
    println("-----------------------------------------------------------------------------------------------")
    val prettyJson = pretty(render(write(o)))
    println(prettyJson)
    println("-----------------------------------------------------------------------------------------------")

  }

  /**
   * Cette méthode permet de récupérer la date de début et de fin d'utilisation d'un ticket à partir de la date
   * choisie par le client et de la configuration du produit et du TicketType.
   * Si la date n'est pas valide, la méthode renvoie null sinon elle renvoie un tableau de 2 dates, la première étant
   * la date de début et la seconde étant la date de fin
   * @param optTicketType : Facultatif (si non fourni, la date n'est pas controllée au niveau du ticketType)
   * @param optDate
   * @return
   */
  def verifyAndExtractStartEndDate(optTicketType:Option[TicketType], optDate:Option[DateTime] ): (Option[DateTime],Option[DateTime])=
  {
    val emptyResult = (None,None)
    if(optTicketType.isEmpty || optDate.isEmpty){
      emptyResult
    }else{


    val ticketType = optTicketType.get
    val product = ticketType.product.get
    val date = optDate.get
    val dateWithTimeReset = date.withTimeAtStartOfDay()
    val start = ticketType.startDate.getOrElse(DateTime.now().withTimeAtStartOfDay)
    val stop = ticketType.stopDate.getOrElse(DateTime.now().withTimeAtStartOfDay)
    val dateValidForTicketType = (dateWithTimeReset.isAfter(start) || dateWithTimeReset.isEqual(start)) && (dateWithTimeReset.isBefore(stop) || dateWithTimeReset.isEqual(stop))

    if(!dateValidForTicketType) emptyResult
    else

      // On controle maintenant la date avec le calendrier du produit
      product.calendarType match {
        case ProductCalendar.NO_DATE => emptyResult  // Pas de calendrier donc pas de date
        case ProductCalendar.DATE_ONLY => {

          // Calendrier jour seulement, la date doit être comprise entres les dates du produits
          val dateonly = date.toLocalDate
          val startDateOnly = product.startDate.get.toLocalDate
          val stopDateOnly = product.stopDate.get.toLocalDate
          if ((dateonly.isAfter(startDateOnly) || dateonly.isEqual(startDateOnly)) && (dateonly.isBefore(stopDateOnly) || dateonly.isEqual(stopDateOnly))) {
            (optDate, optDate)
          }
          else {
            emptyResult
          }

        }
        case ProductCalendar.DATE_TIME => {

          // on récupère le créneau horraire qui correspond à l'interval pour la date/heure donnée
          val listIncluded:Seq[IntraDayPeriod] = DB readOnly { implicit session =>
            //FIXME pb de précision sur les dates :(
            sql"select * from intra_day_period where product_fk = ${product.id} and start_date <= ${date.plusSeconds(1)} and end_date >= ${date.minusSeconds(1)}"
              .map(rs => IntraDayPeriod(startDate = rs.get("start_date"), endDate = rs.get("end_date"))).list.apply
          }
          val timeonly = date.toLocalTime
          val res = listIncluded.filter{
            intraDayPeriod => {
              val startDateTime = intraDayPeriod.startDate.toLocalTime
              val endDateTime = intraDayPeriod.endDate.toLocalTime
              val rightStartDateTime = new LocalTime(startDateTime.getHourOfDay, startDateTime.minuteOfHour().get())
              val rightEndDateTime = new LocalTime(endDateTime.getHourOfDay, endDateTime.minuteOfHour().get())
              (timeonly.isAfter(rightStartDateTime)|| timeonly.isEqual(rightStartDateTime)) && (timeonly.isBefore(rightEndDateTime) || timeonly.isEqual(rightEndDateTime))
            }
          }
          // et on renvoie le créneau horaire
            res.headOption match{
              case Some(idp) => {
                //DateTime(int year, int monthOfYear, int dayOfMonth, int hourOfDay, int minuteOfHour)
                val startDate = new DateTime(date.getYear, date.getMonthOfYear, date.dayOfMonth().get(), idp.startDate.hourOfDay().get(), idp.startDate.minuteOfHour().get())
                val endDate = new DateTime(date.getYear, date.getMonthOfYear, date.dayOfMonth().get(), idp.endDate.hourOfDay().get(), idp.endDate.minuteOfHour().get())

                (Some(startDate),Some(endDate))
              }
              case _ => emptyResult
            }
        }
      }
    }
  }
  case class IntraDayPeriod(startDate:DateTime, endDate:DateTime)

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
        case None => {
          val getter = clas.getMethod(fieldName)
          getter.invoke(this)
        }
      }
    }
    constructor.newInstance(arguments: _*).asInstanceOf[T]
  }
}
