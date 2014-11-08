package com.mogobiz.run.handlers

import java.util.Date

import com.mogobiz.run.cart.domain.{TicketType, StockCalendar}
import com.mogobiz.run.es.EsClientOld
import com.mogobiz.run.model.{StockCalendar => EsStockCalendar, Stock => EsStock}
import com.sksamuel.elastic4s.ElasticDsl.{update => esupdate4s}
import com.sksamuel.elastic4s.ElasticDsl._
import org.json4s.{DefaultFormats, Formats}

/**
 * Handle the stock update in es
 */
class StockHandler {

  def update(storeCode: String, uuid:String, sold: Long) = {
    println("update ES stock")

    val script = "ctx._source.stock = ctx._source.initialStock - sold"
    val req = esupdate4s id uuid in s"${storeCode}/stock" script script params {"sold" -> s"$sold"} retryOnConflict 4
    println(req)
    EsClientOld.updateRaw(req)

  }

  def update(storeCode: String, ticketType: TicketType, stockCalendar:StockCalendar):Boolean = {


    def doUpdate(storeCode:String, newStock:EsStock):Boolean = {

      println("update du stock:",newStock)

      import org.json4s.native.Serialization.write
      implicit def json4sFormats: Formats = DefaultFormats.lossless
      val json = write(newStock)
      EsClientOld.update2[EsStock](storeCode, newStock, json)
    }

    println("update ES stock with stockCalendar", stockCalendar)

    //1. get the doc
//    val wishlistList = EsClient.load[WishlistList](esStore(store), wishlistListId).getOrElse(throw NotFoundException(s"Unknown wishlistList $wishlistListId"))

    val startDate = stockCalendar.startDate.get.toDate
    val res = EsClientOld.load[EsStock](storeCode,ticketType.uuid)
    println(res)

    // Some(Stock(139,0ec602d5-3da8-4e64-9986-27e04e7b7fe1,f8a279fc-3836-4aed-93bd-64e1609598b0,135,07e020a0-5aaf-4def-a783-15edd33c360e,Wed Jan 01 01:00:00 CET 2014,Thu Jan 01 00:59:00 CET 2015,None,Thu Oct 02 02:54:15 CEST 2014,Thu Oct 02 02:54:15 CEST 2014,Fri Oct 03 02:10:01 CEST 2014,150,false,false,true,Some(DATE_ONLY),None,null))
    res match {
      case Some(stock) =>
        println(s"stock trouvé => recherche du stockCal avec startDate = ${startDate}")
        val stockCals = stock.stockByDateTime match {
          case Some(stocksByDateTime) =>
            stocksByDateTime.filter {
              stockCal =>
                //println(stockCal)
                //stockCal.startDate == startDate //pas de comparaison par date car la date récup de ES est désérialisé avec offset GMT
                stockCal.uuid == stockCalendar.uuid
            }
          case _ => Seq()
        }
        //println("stockCals=",stockCals)
        if(!stockCals.isEmpty){
          println(s" stockCal trouvé => mise à jour du stock")

          val newEsStockCal = stockCals.head.copy(stock = stockCalendar.stock - stockCalendar.sold, lastUpdated = new Date)
          val newStockByDateTime = stock.stockByDateTime.getOrElse(Seq()).map { stockCal =>
            if(stockCal.uuid == stockCalendar.uuid)
              newEsStockCal
            else
              stockCal
          }
          val newStock = stock.copy(stockByDateTime = Some(newStockByDateTime))

          doUpdate(storeCode, newStock)
        } else {
          println(s" stockCal non trouvé => ajout à la seq du stock")
          val newEsStockCal = EsStockCalendar(
            id = stockCalendar.id
            ,uuid = stockCalendar.uuid
            ,dateCreated = stockCalendar.dateCreated.toDate
            ,lastUpdated = stockCalendar.lastUpdated.toDate
            ,startDate = startDate
            ,stock = stockCalendar.stock - stockCalendar.sold
          )
          val newStockByDateTime = stock.stockByDateTime.getOrElse(Seq()) :+ newEsStockCal
          val newStock = stock.copy(stockByDateTime = Some(newStockByDateTime))

          doUpdate(storeCode, newStock)
        }

      case _ =>
        println(s"stock non trouvé => insertion du stock")
        val product = ticketType.product.get
        val stock = ticketType.stock.get
        val newStock = EsStock(
          id = ticketType.id
        , uuid = ticketType.uuid
        , sku = ticketType.sku
        , productId = product.id
        , productUuid = product.uuid
        , startDate = ticketType.startDate.get.toDate
        , stopDate = ticketType.stopDate.get.toDate
        , availabilityDate = ticketType.availabilityDate.map(_.toDate)
        , initialStock = stock.stock
        , stockUnlimited = stock.stockUnlimited
        , stockOutSelling = stock.stockOutSelling
        , stockDisplay = product.stockDisplay
        , calendarType = Some(product.calendarType.toString)
        , stock = None
        , stockByDateTime = Some(Seq(EsStockCalendar(
          id = stockCalendar.id
          ,uuid = stockCalendar.uuid
          ,dateCreated = stockCalendar.dateCreated.toDate
          ,lastUpdated = stockCalendar.lastUpdated.toDate
          ,startDate = startDate
          ,stock = stockCalendar.stock - stockCalendar.sold
        )))
        )

        doUpdate(storeCode, newStock)

    }

  }
}
