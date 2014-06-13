package com.mogobiz

import com.mogobiz.cart.{ProductCalendar, TicketType}
import org.joda.time.DateTime
import scalikejdbc._, SQLInterpolation._

/**
 * Created by Christophe on 24/04/2014.
 */
object Utils {

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
    val start = ticketType.startDate.getOrElse(DateTime.now())
    val stop = ticketType.stopDate.getOrElse(DateTime.now())
    val dateValidForTicketType = date.isAfter(start) && date.isBefore(stop)

    // On controle maintenant la date avec le calendrier du produit
    product.calendarType match {
      case ProductCalendar.NO_DATE => (None,None)  // Pas de calendrier donc pas de date
      case ProductCalendar.DATE_ONLY => {
        if (dateValidForTicketType) {
          // Calendrier jour seulement, la date doit être comprise entres les dates du produits
          val dateonly = date.toLocalDate
          if (dateonly.isAfter(product.startDate.get.toLocalDate) && dateonly.isBefore(product.stopDate.get.toLocalDate)) {
            (optDate, optDate)
          }
          else {
            emptyResult
          }
        } else {
          emptyResult
        }
      }
      case ProductCalendar.DATE_TIME => {

        if (dateValidForTicketType)
        {

          val listIncluded = DB readOnly { implicit session =>
            sql"select * from intra_day_period where product_fk = ${product.id} and ${date}<=startDate and ${date}>=endDate".map(rs => IntraDayPeriod(startDate = rs.dateTime("start_date"), endDate = rs.dateTime("end_date"))).list.apply

          }
          //TODO

            /*
            def listIncluded = IntraDayPeriod.createCriteria().list{
              eq('product',product)
              le('startDate',date)
              ge('endDate',date)
            }*/
            /*
            for (IntraDayPeriod intraDayPeriod in listIncluded) {
              // On vérifie que l'heure demandé correspond à la place horaire du calendrier
              String patternComparaisonHeure = "HHmm";
              if (DateUtilitaire.isBeforeOrEqual(intraDayPeriod.startDate, date, patternComparaisonHeure)
                && DateUtilitaire.isAfterOrEqual(intraDayPeriod.endDate, date, patternComparaisonHeure))
              {
                Calendar endDate = DateUtilitaire.copy(intraDayPeriod.startDate);
                endDate.set(Calendar.HOUR, intraDayPeriod.endDate.get(Calendar.HOUR))
                endDate.set(Calendar.MINUTE, intraDayPeriod.endDate.get(Calendar.MINUTE))
                return [
                intraDayPeriod.startDate,
                endDate
                ]
              }
            }*/
          (optDate,optDate)  //TODO
        }else emptyResult
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
