/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import java.text.SimpleDateFormat
import java.util.{UUID, Date, Calendar, Locale}

import com.mogobiz.es._
import com.mogobiz.es.EsClient
import com.mogobiz.es.EsClient._
import com.mogobiz.pay.model.Mogopay.Account
import com.mogobiz.run.config.Settings
import com.mogobiz.run.es._
import com.mogobiz.json.{JacksonConverter, JsonUtil}
import com.mogobiz.run.exceptions.{CommentAlreadyExistsException, NotAuthorizedException}
import com.mogobiz.run.learning.UserActionRegistration
import com.mogobiz.run.model.Learning.UserAction
import com.mogobiz.run.model.RequestParameters._
import com.mogobiz.run.model._
import com.mogobiz.run.services.RateBoService
import com.mogobiz.run.utils.Paging
import com.sksamuel.elastic4s.ElasticDsl.{update => esupdate4s, search => esearch4s, delete => esdelete4s, _}
import com.sksamuel.elastic4s.{SearchType, FilterDefinition}
import com.sksamuel.elastic4s.source.DocumentSource
import org.elasticsearch.action.get.MultiGetItemResponse
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.search.{SearchHit, SearchHits}
import org.elasticsearch.search.aggregations.bucket.terms.Terms
import org.elasticsearch.search.sort.SortOrder
import org.json4s.JsonAST.{JObject, JArray, JNothing}
import org.json4s.JsonDSL._
import org.json4s._


class SkuHandler  extends JsonUtil {

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
    val priceWithVatField = skuRequest.countryCode match{
      case Some(countryCode) => s"${countryCode}.endPrice"
      case _ => "price"
    }

    val filters: List[FilterDefinition] = List(
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
    val _sort = skuRequest.orderBy.getOrElse("name.raw")
    val _sortOrder = skuRequest.orderDirection.getOrElse("asc")
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

  private val sdf = new SimpleDateFormat ("yyyy-MM-dd")
  //THH:mm:ssZ
  private val hours = new SimpleDateFormat ("HH:mm")
  /**
   * Renvoie le filtres permettant de filtrer les skus via pour les produits mis en avant
   * si la requête le demande
   * @param req - product request
   * @return FilterDefinition
   */
  private def createFeaturedRangeFilters (req: SkuRequest): Option[FilterDefinition] = {
    if (req.featured.getOrElse (false) ) {
      val today = sdf.format (Calendar.getInstance ().getTime)
      val list = List (
        createRangeFilter ("product.startFeatureDate", None, Some (s"$today") ),
        createRangeFilter ("product.stopFeatureDate", Some (s"$today"), None)
      ).flatten
      Some (and (list: _*) )
    }
    else None
  }

  /**
   * Renvoie le filtre pour les features
   * @param req - product request
   * @return FilterDefinition
   */
  private def createFeaturesFilters (req: SkuRequest): Option[FilterDefinition] = {
    createAndOrFilterBySplitKeyValues (req.feature, (k, v) => {
      Some (
        must (
          List (
            createNestedTermFilter ("product.features", s"product.features.name.raw", Some (k) ),
            createNestedTermFilter ("product.features", s"product.features.value.raw", Some (v) )
          ).flatten: _*
        )
      )
    })
  }

  /**
   * Renvoie la liste des filtres pour les variations
   * @param req - product request
   * @return FilterDefinition
   */
  private def createVariationsFilters (req: SkuRequest): Option[FilterDefinition] = {
    createAndOrFilterBySplitKeyValues (req.variations, (k, v) => {
      Some (
        or (
          must (
            List (
              createTermFilter (s"variation1.name.raw", Some (k) ),
              createTermFilter (s"variation1.value.raw", Some (v) )
            ).flatten: _*
          )
          , must (
            List (
              createTermFilter (s"variation2.name.raw", Some (k) ),
              createTermFilter (s"variation2.value.raw", Some (v) )
            ).flatten: _*
          )
          , must (
            List (
              createTermFilter (s"variation3.name.raw", Some (k) ),
              createTermFilter (s"variation3.value.raw", Some (v) )
            ).flatten: _*
          )
        )
      )
    })
  }
}
