/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import com.mogobiz.run.config.DefaultComplete
import com.mogobiz.run.config.MogobizHandlers.handlers._
import com.mogobiz.run.model.RequestParameters._
import de.heikoseeberger.akkahttpjson4s.Json4sSupport._

class SkuService extends Directives with DefaultComplete {
  import org.json4s._

  val route = {
    pathPrefix(Segment / "skus") { implicit storeCode =>
      /*
      optionalCookie(CookieTracking) {
        case Some(mogoCookie) =>
          skuRoutes(mogoCookie.content)
        case None =>
          val id = UUID.randomUUID.toString
          setCookie(HttpCookie(CookieTracking, content = id, path = Some("/api/store/" + storeCode))) {
            skuRoutes(id)
          }
      }
       */
      skuRoutes
    }
  }

  /*
  def skuRoutes(uuid:String)(implicit storeCode: String) = products ~
    find ~ compare ~ notation ~
    product(uuid)
   */

  def skuRoutes(implicit storeCode: String) = skus ~ skuSearchRoute

  def skuSearchRoute(implicit storeCode: String): Route = pathEnd {
    get {
      parameterMap { params =>
        val maxItemPerPage = params.get("maxItemPerPage").map(_.toInt)
        val pageOffset = params.get("pageOffset").map(_.toInt)
        val id = params.get("id")
        val productId = params.get("productId")
        val xtype = params.get("xtype")
        val name = params.get("name")
        val code = params.get("code")
        val categoryPath = params.get("categoryPath")
        val brandId = params.get("brandId")
        val notations = params.get("notations")
        val tagName = params.get("tagName")
        val priceRange = params.get("priceRange")
        val creationDateMin = params.get("creationDateMin")
        val featured = params.get("featured").map(_.toBoolean)
        val orderBy = params.get("orderBy")
        val orderDirection = params.get("orderDirection")
        val lang = params.getOrElse("lang", "_all")
        val currency = params.get("currency")
        val country = params.get("country")
        val promotionId = params.get("promotionId")
        val hasPromotion = params.get("hasPromotion").map(_.toBoolean)
        val inStockOnly = params.get("inStockOnly").map(_.toBoolean)
        val property = params.get("property")
        val feature = params.get("feature")
        val variations = params.get("variations")

        val promotionIds = hasPromotion.map(v => {
          if (v) {
            val ids = promotionHandler.getPromotionIds(storeCode)
            if (ids.isEmpty) None
            else Some(ids.mkString("|"))
          } else None
        }) match {
          case Some(s) => s
          case _       => promotionId
        }

        val skuRequest = SkuRequest(
          maxItemPerPage,
          pageOffset,
          id,
          productId,
          xtype,
          name,
          code,
          categoryPath,
          brandId,
          tagName,
          notations,
          priceRange,
          creationDateMin,
          featured,
          orderBy,
          orderDirection,
          lang,
          currency,
          country,
          promotionIds,
          hasPromotion,
          inStockOnly,
          property,
          feature,
          variations
        )
        handleCall(skuHandler.querySkusByCriteria(storeCode, skuRequest),
                   (json: JValue) => complete(StatusCodes.OK, json))
      }
    }
  }

  def skus(implicit storeCode: String) = pathPrefix(Segment) { skuId =>
    get {
      parameters('update ? false, 'stock ? false, 'date ?) {
        (update, stock, date) =>
          handleCall(skuHandler.getSku(storeCode, skuId, update, stock, date),
                     (json: JValue) => complete(StatusCodes.OK, json))
      }
    }
  }
}
