/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import java.util.Calendar

import com.mogobiz.es.{ EsClient, _ }
import com.mogobiz.json.JsonUtil
import com.mogobiz.run.es._
import com.mogobiz.run.model.Mogobiz.{ ProductCalendar, Sku }
import com.mogobiz.run.model.RequestParameters._
import com.mogobiz.run.model._
import com.mogobiz.run.services.RateBoService
import com.mogobiz.run.utils.Paging
import com.sksamuel.elastic4s.ElasticDsl.{ delete => esdelete4s, search => esearch4s, update => esupdate4s, _ }
import com.sksamuel.elastic4s.FilterDefinition
import org.elasticsearch.search.SearchHits
import org.elasticsearch.search.sort.SortOrder
import org.joda.time.format.DateTimeFormat
import org.json4s.JsonAST.JArray
import org.json4s.JsonDSL._
import org.json4s._

class SkuHandler extends JsonUtil {

  private val MIN_NOTATION = 1
  private val MAX_NOTATION = 5

  private val ES_TYPE_SKU = "sku"

  val rateService = RateBoService
  private val fieldsToRemoveForProductSearchRendering = List() //List("skus", "features", "resources", "datePeriods", "intraDayPeriods")

  def querySkusByCriteria(storeCode: String, skuRequest: SkuRequest): JValue = {
    if (skuRequest.hasPromotion.getOrElse(false) && skuRequest.promotionId.isEmpty) {
      return Paging.wrap(0, JArray(List.empty), skuRequest)
    }
    val _query = skuRequest.name match {
      case Some(s) =>
        esearch4s in storeCode -> ES_TYPE_SKU query {
          matchQuery("name", s)
        }
      case None => esearch4s in storeCode -> ES_TYPE_SKU query {
        matchall
      }
    }

    val lang = if (skuRequest.lang == "_all") "" else s"${skuRequest.lang}."
    val priceWithVatField = skuRequest.countryCode match {
      case Some(countryCode) => s"${countryCode}.saleEndPrice"
      case _ => "salePrice"
    }

    val defaultStockFilter = if (skuRequest.inStockOnly.getOrElse(true)) {
      val orFilters = List(
        not(existsFilter("stock")),
        createTermFilter("stock.available", Some(true)).get
      )
      Some(or(orFilters: _*))
    } else {
      None
    }

    val filters: List[FilterDefinition] = List(
      defaultStockFilter,
      skuRequest.countryCode.map { countryCode => termFilter(s"${countryCode}.enabled", true) },
      createOrFilterBySplitValues(skuRequest.id, v => createTermFilter("id", Some(v))),
      createOrFilterBySplitValues(skuRequest.productId, v => createTermFilter("product.id", Some(v))),
      createOrFilterBySplitValues(skuRequest.code, v => createTermFilter("product.code", Some(v))),
      createOrFilterBySplitValues(skuRequest.xtype, v => createTermFilter("product.xtype", Some(v))),
      createOrFilterBySplitValues(skuRequest.categoryPath, v => createRegexFilter("path", Some(v))),
      createOrFilterBySplitValues(skuRequest.brandId, v => createTermFilter("product.brand.id", Some(v))),
      createOrFilterBySplitValues(skuRequest.tagName.map(_.toLowerCase), v => createNestedTermFilter("product.tags", "product.tags.name", Some(v))),
      //TODO      createOrFilterBySplitValues(skuRequest.notations, v => createNestedTermFilter("notations", "notations.notation", Some(v))),
      createOrFilterBySplitKeyValues(skuRequest.priceRange, (min, max) => createNumericRangeFilter(s"${priceWithVatField}", min, max)),
      createOrFilterBySplitValues(skuRequest.creationDateMin, v => createRangeFilter("product.dateCreated", Some(v), None)),
      createOrFilterBySplitValues(skuRequest.promotionId, v => createTermFilter("coupons.id", Some(v))),
      createFeaturedRangeFilters(skuRequest),
      createAndOrFilterBySplitKeyValues(skuRequest.property, (k, v) => createTermFilter(k, Some(v))),
      createFeaturesFilters(skuRequest),
      createVariationsFilters(skuRequest)
    ).flatten

    val fieldsToExclude = getAllExcludedLanguagesExceptAsList(storeCode, skuRequest.lang) ::: fieldsToRemoveForProductSearchRendering
    val _size: Int = skuRequest.maxItemPerPage.getOrElse(100)
    val _from: Int = skuRequest.pageOffset.getOrElse(0) * _size
    val _sortOrder = skuRequest.orderDirection.getOrElse("asc")
    val _sort = skuRequest.orderBy.getOrElse("name.raw") match {
      case a if a startsWith "name" => s"${lang}name.raw"
      case b if b startsWith "price" => skuRequest.countryCode match {
        case Some(countryCode) => s"$countryCode.saleEndPrice"
        case _ => "salePrice"
      }
      case s => s
    }
    lazy val currency = queryCurrency(storeCode, skuRequest.currencyCode)
    val response: SearchHits = EsClient.searchAllRaw(
      filterRequest(_query, filters)
        sourceExclude (fieldsToExclude: _*)
        from _from
        size _size
        sort {
          by field _sort order SortOrder.valueOf(_sortOrder.toUpperCase)
        }
    )
    val hits: JArray = response.getHits

    /* pas besoin pour l'instant
    val products: JValue = hits.children.map {
      hit => renderProduct(storeCode, hit, skuRequest.countryCode, skuRequest.currencyCode, skuRequest.lang, currency, fieldsToRemoveForProductSearchRendering)
    }*/

    val skus: JValue = hits.children
    Paging.wrap(response.getTotalHits.toInt, skus, skuRequest)
  }

  private val sdf = DateTimeFormat.forPattern("yyyy-MM-dd")
  //THH:mm:ssZ
  /**
   * Renvoie le filtres permettant de filtrer les skus via pour les produits mis en avant
   * si la requête le demande
   *
   * @param req - product request
   * @return FilterDefinition
   */
  private def createFeaturedRangeFilters(req: SkuRequest): Option[FilterDefinition] = {
    if (req.featured.getOrElse(false)) {
      val today = sdf.print(Calendar.getInstance().getTimeInMillis)
      val list = List(
        createRangeFilter("product.startFeatureDate", None, Some(s"$today")),
        createRangeFilter("product.stopFeatureDate", Some(s"$today"), None)
      ).flatten
      Some(and(list: _*))
    } else None
  }

  /**
   * Renvoie le filtre pour les features
   *
   * @param req - product request
   * @return FilterDefinition
   */
  private def createFeaturesFilters(req: SkuRequest): Option[FilterDefinition] = {
    createAndOrFilterBySplitKeyValues(req.feature, (k, v) => {
      Some(
        must(
          List(
            createNestedTermFilter("product.features", s"product.features.name.raw", Some(k)),
            createNestedTermFilter("product.features", s"product.features.value.raw", Some(v))
          ).flatten: _*
        )
      )
    })
  }

  /**
   * Renvoie la liste des filtres pour les variations
   *
   * @param req - product request
   * @return FilterDefinition
   */
  private def createVariationsFilters(req: SkuRequest): Option[FilterDefinition] = {
    createAndOrFilterBySplitKeyValues(req.variations, (k, v) => {
      Some(
        or(
          must(
            List(
              createTermFilter(s"variation1.name.raw", Some(k)),
              createTermFilter(s"variation1.value.raw", Some(v))
            ).flatten: _*
          ), must(
            List(
              createTermFilter(s"variation2.name.raw", Some(k)),
              createTermFilter(s"variation2.value.raw", Some(v))
            ).flatten: _*
          ), must(
            List(
              createTermFilter(s"variation3.name.raw", Some(k)),
              createTermFilter(s"variation3.value.raw", Some(v))
            ).flatten: _*
          )
        )
      )
    })
  }

  case class AvailableStockByDateTime(startDate: String, available: Boolean)
  case class AvailableStock(available: Boolean, byDateTime: List[AvailableStockByDateTime])

  /**
   * Helper method to debug sku resource
   *
   * @param storeCode
   * @param skuId
   * @param update
   * @param stockValue
   * @param date
   * @return
   */
  def getSku(storeCode: String, skuId: String, update: Boolean, stockValue: Boolean, date: Option[String]) = {
    //    println(s"--------------------------------- getSku update=$update, stock=$stockValue, date="+date)
    val res = EsClient.loadRaw(get(skuId) from storeCode + "/" + ES_TYPE_SKU).get
    val sku = response2JValue(res)

    /* for debug purpose only
    if(update) {
      println("should update stock")
      val stockInfo = if(date.isEmpty){
        JObject(JField("stock", JObject(JField("available",JBool(stockValue)))))
      }else{
//.as[Option[Int]
        val startDate = date.get + ".000+02:00" //"2015-07-18T17:00:00.000+02:00" //stockCalendar.startDate.get

        implicit val formats = native.Serialization.formats(NoTypeHints)

        val jsonStock = sku \ "stock"
        val stockInfo = jsonStock.extract[AvailableStock]
//        println("stockINfo=",stockInfo)

        val dtStockOpt = stockInfo.byDateTime.find(_.startDate == startDate)
//          println("dtStockOpt=",dtStockOpt)
        val newDtStock = dtStockOpt match {
          case Some(dtStock) => dtStock.copy(startDate = dtStock.startDate, available = stockValue)
          case None => AvailableStockByDateTime(startDate = startDate, available = stockValue)
        }
//          println("newDtStock=",newDtStock)
        val newDtStockList = stockInfo.byDateTime.filter(_.startDate != startDate):+ newDtStock
//          println("newDtStockList=",newDtStockList)
        val stockAvailability = !newDtStockList.forall(_.available == false)

        val newStock = stockInfo.copy(available = stockAvailability, byDateTime = newDtStockList)
//        println("newStock = ",newStock )

        JObject(JField("stock", parse(write(newStock))))

      }

      val updatedSku = (sku removeField { f => f._1 == "stock"})merge stockInfo
      EsClient.updateRaw(esupdate4s id skuId in storeCode -> ES_TYPE_SKU doc updatedSku retryOnConflict 4)
      updatedSku
    }else{*/
    sku
    //}

  }

  /**
   * Return all available sku (in stock) for a specific product
   *
   * @param indexEs
   * @param productId
   */
  def existsAvailableSkus(indexEs: String, productId: Long): Boolean = {

    val _query = esearch4s in indexEs -> ES_TYPE_SKU query {
      matchall
    }

    val filters: List[FilterDefinition] = List(createTermFilter("product.id", Some(productId)).get, createTermFilter("available", Some(true)).get)

    val response: SearchHits = EsClient.searchAllRaw(
      filterRequest(_query, filters)
    )
    //    println(s"----- existsAvailableSkus for productId=$productId => nb skus that have stock : "+response.getTotalHits)
    response.getTotalHits > 0
  }

  /**
   * Update sku stock availability only for NO_DATE product
   */
  def updateStockAvailability(indexEs: String, pSku: Sku, stock: Stock, stockCalendar: StockCalendar) = {

    stock.calendarType match {
      case ProductCalendar.NO_DATE =>
        val stockAvailable = ((stock.initialStock - stockCalendar.sold) > 0)
        val script = s"ctx._source.available=$stockAvailable"
        EsClient.updateRaw(esupdate4s id pSku.id in indexEs -> ES_TYPE_SKU script script retryOnConflict 4)
        true
      case _ => false
    }
  }
}
