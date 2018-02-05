/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives
import com.mogobiz.run.config.DefaultComplete
import com.mogobiz.run.config.MogobizHandlers.handlers._
import com.mogobiz.run.model.RequestParameters.FacetRequest
import de.heikoseeberger.akkahttpjson4s.Json4sSupport._
import org.json4s.JValue

class FacetService extends Directives with DefaultComplete {

  val route = {
    pathPrefix(Segment / "facets") { implicit storeCode =>
      getFacets
    }
  }

  def getFacets(implicit storeCode: String) =
    pathEnd {
      get {
        parameterMap { params =>
          val priceInterval = params("priceInterval").toLong
          val xtype = params.get("xtype")
          val name = params.get("name")
          val code = params.get("code")
          val rootCategoryPath = params.get("rootCategoryPath")
          val categoryPath = params.get("categoryPath")
          val brandId = params.get("brandId")
          val tags = params.get("tags")
          val notations = params.get("notations")
          val priceRange = params.get("priceRange")
          val creationDateMin = params.get("creationDateMin")
          val featured = params.get("featured").map(_.toBoolean)
          val lang = params.getOrElse("lang", "_all")
          val country = params.get("country")
          val promotionId = params.get("promotionId")
          val hasPromotion = params.get("hasPromotion").map(_.toBoolean)
          val inStockOnly = params.get("inStockOnly").map(_.toBoolean)
          val property = params.get("property")
          val features = params.get("features")
          val variations = params.get("variations")
          val brandName = params.get("brandName")
          val categoryName = params.get("categoryName")
          val multiCategory = params.get("multiCategory").map(_.toBoolean)
          val multiBrand = params.get("multiBrand").map(_.toBoolean)
          val multiTag = params.get("multiTag").map(_.toBoolean)
          val multiFeatures = params.get("multiFeatures").map(_.toBoolean)
          val multiVariations = params.get("multiVariations").map(_.toBoolean)
          val multiNotation = params.get("multiNotation").map(_.toBoolean)
          val multiPrices = params.get("multiPrices").map(_.toBoolean)
          val param = FacetRequest(
            priceInterval,
            None,
            xtype,
            name,
            code,
            rootCategoryPath,
            categoryPath,
            brandId,
            tags,
            notations,
            priceRange,
            creationDateMin,
            featured,
            lang,
            country,
            promotionId,
            hasPromotion,
            inStockOnly,
            property,
            features,
            variations,
            brandName,
            categoryName,
            multiCategory,
            multiBrand,
            multiTag,
            multiFeatures,
            multiVariations,
            multiNotation,
            multiPrices
          )

          handleCall(facetHandler.getProductCriteria(storeCode, param),
                     (json: JValue) => complete(StatusCodes.OK, json))
        }
      }
    } ~ pathPrefix("skus") {
      pathEnd {
        get {
          parameterMap { params =>
            val priceInterval = params("priceInterval").toLong
            val productId = params.get("productId")
            val xtype = params.get("xtype")
            val name = params.get("name")
            val code = params.get("code")
            val rootCategoryPath = params.get("rootCategoryPath")
            val categoryPath = params.get("categoryPath")
            val brandId = params.get("brandId")
            val tags = params.get("tags")
            val notations = params.get("notations")
            val priceRange = params.get("priceRange")
            val creationDateMin = params.get("creationDateMin")
            val featured = params.get("featured").map(_.toBoolean)
            val lang = params.getOrElse("lang", "_all")
            val country = params.get("country")
            val promotionId = params.get("promotionId")
            val hasPromotion = params.get("hasPromotion").map(_.toBoolean)
            val inStockOnly = params.get("inStockOnly").map(_.toBoolean)
            val property = params.get("property")
            val features = params.get("features")
            val variations = params.get("variations")
            val brandName = params.get("brandName")
            val categoryName = params.get("categoryName")
            val multiCategory = params.get("multiCategory").map(_.toBoolean)
            val multiBrand = params.get("multiBrand").map(_.toBoolean)
            val multiTag = params.get("multiTag").map(_.toBoolean)
            val multiFeatures = params.get("multiFeatures").map(_.toBoolean)
            val multiVariations = params.get("multiVariations").map(_.toBoolean)
            val multiNotation = params.get("multiNotation").map(_.toBoolean)
            val multiPrices = params.get("multiPrices").map(_.toBoolean)
            val param = FacetRequest(
              priceInterval,
              productId,
              xtype,
              name,
              code,
              rootCategoryPath,
              categoryPath,
              brandId,
              tags,
              notations,
              priceRange,
              creationDateMin,
              featured,
              lang,
              country,
              promotionId,
              hasPromotion,
              inStockOnly,
              property,
              features,
              variations,
              brandName,
              categoryName,
              multiCategory,
              multiBrand,
              multiTag,
              multiFeatures,
              multiVariations,
              multiNotation,
              multiPrices
            )

            handleCall(facetHandler.getSkuCriteria(storeCode, param),
                       (json: JValue) => complete(StatusCodes.OK, json))
          }
        }
      }
    }
}
