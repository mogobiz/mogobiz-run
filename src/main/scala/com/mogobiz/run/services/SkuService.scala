/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import java.util.UUID

import com.mogobiz.run.config.DefaultComplete
import com.mogobiz.run.config.MogobizHandlers._
import com.mogobiz.run.implicits.Json4sProtocol
import Json4sProtocol._
import com.mogobiz.run.model.RequestParameters._
import com.mogobiz.run.model._
import com.mogobiz.session.Session
import com.mogobiz.session.SessionESDirectives._
import com.mogobiz.pay.implicits.Implicits
import com.mogobiz.pay.implicits.Implicits.MogopaySession
import spray.http.{ HttpCookie, StatusCodes }
import spray.routing.Directives
//import com.mogobiz.pay.config.MogopayHandlers.accountHandler

import com.mogobiz.run.config.Settings._
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

  import shapeless._

  def skuSearchRoute(implicit storeCode: String) = pathEnd {
    get {
      val productsParams = parameters(

        'maxItemPerPage.?.as[Option[Int]] ::
          'pageOffset.?.as[Option[Int]] ::
          'id.? ::
          'productId.? ::
          'xtype.? ::
          'name.? ::
          'code.? ::
          'categoryPath.? ::
          'brandId.? ::
          'tagName.? ::
          'notations.? ::
          'priceRange.? ::
          'creationDateMin.? ::
          'featured.?.as[Option[Boolean]] ::
          'orderBy.? ::
          'orderDirection.? ::
          'lang ? "_all" ::
          'currency.? ::
          'country.? ::
          'promotionId.? ::
          'hasPromotion.?.as[Option[Boolean]] ::
          'inStockOnly.?.as[Option[Boolean]] ::
          'property.? ::
          'feature.? ::
          'variations.? :: HNil
      )

      productsParams.happly {
        case (maxItemPerPage :: pageOffset :: id :: productId :: xtype :: name :: code :: categoryPath :: brandId :: tagName :: notations :: priceRange :: creationDateMin
          :: featured :: orderBy :: orderDirection :: lang :: currencyCode :: countryCode :: promotionId :: hasPromotion :: inStockOnly :: property :: feature :: variations :: HNil) =>

          val promotionIds = hasPromotion.map(v => {
            if (v) {
              val ids = promotionHandler.getPromotionIds(storeCode)
              if (ids.isEmpty) None
              else Some(ids.mkString("|"))
            } else None
          }) match {
            case Some(s) => s
            case _ => promotionId
          }

          val params = new SkuRequest(
            maxItemPerPage, pageOffset, id, productId, xtype, name, code, categoryPath,
            brandId, tagName, notations, priceRange, creationDateMin,
            featured, orderBy, orderDirection, lang, currencyCode, countryCode, promotionIds, hasPromotion, inStockOnly, property, feature, variations
          )
          handleCall(skuHandler.querySkusByCriteria(storeCode, params),
            (json: JValue) => complete(StatusCodes.OK, json))
      }
    }
  }

  def skus(implicit storeCode: String) = pathPrefix(Segment) {
    skuId =>
      get {
        parameters('update ? false, 'stock ? false, 'date ?) { (update, stock, date) =>
          handleCall(skuHandler.getSku(storeCode, skuId, update, stock, date), (json: JValue) => complete(StatusCodes.OK, json))
        }
      }
  }
}
