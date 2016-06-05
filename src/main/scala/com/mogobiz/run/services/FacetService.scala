/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.services

import com.mogobiz.run.config.DefaultComplete
import com.mogobiz.run.config.MogobizHandlers.handlers._
import com.mogobiz.run.model.RequestParameters.FacetRequest
import spray.http.StatusCodes
import spray.routing.Directives
import com.mogobiz.run.implicits.Json4sProtocol
import Json4sProtocol._
import org.json4s._

import scala.concurrent.ExecutionContext
import scala.util.Try

class FacetService extends Directives with DefaultComplete {

  val route = {
    pathPrefix(Segment / "facets") { implicit storeCode =>
      getFacets
    }
  }

  import shapeless._

  def getFacets(implicit storeCode: String) = pathEnd {
    get {
      val facetParam = parameters('priceInterval.as[Long] ::
        'xtype.? ::
        'name.? ::
        'code.? ::
        'rootCategoryPath.? ::
        'categoryPath.? ::
        'brandId.? ::
        'tags.? ::
        'notations.? ::
        'priceRange.? ::
        'creationDateMin.? ::
        'featured.?.as[Option[Boolean]] ::
        'lang ? "_all" ::
        'country.? ::
        'promotionId.? ::
        'hasPromotion.?.as[Option[Boolean]] ::
        'inStockOnly.?.as[Option[Boolean]] ::
        'property.? ::
        'features.? ::
        'variations.? ::
        'brandName.? ::
        'categoryName.? ::
        'multiCategory.?.as[Option[Boolean]] ::
        'multiBrand.?.as[Option[Boolean]] ::
        'multiTag.?.as[Option[Boolean]] ::
        'multiFeatures.?.as[Option[Boolean]] ::
        'multiVariations.?.as[Option[Boolean]] ::
        'multiNotation.?.as[Option[Boolean]] ::
        'multiPrices.?.as[Option[Boolean]] ::
        HNil
      )

      facetParam.happly {
        case (priceInterval :: xtype :: name :: code :: rootCategoryPath :: categoryPath :: brandId :: tags :: notations :: priceRange ::
          creationDateMin :: featured :: lang :: country :: promotionId :: hasPromotion :: inStockOnly :: property :: features :: variations ::
          brandName :: categoryName :: multiCategory :: multiBrand :: multiTag :: multiFeatures :: multiVariations ::
          multiNotation :: multiPrices :: HNil) =>

          val param = new FacetRequest(priceInterval, None, xtype, name, code, rootCategoryPath, categoryPath, brandId, tags, notations, priceRange,
            creationDateMin, featured, lang, country, promotionId, hasPromotion, inStockOnly, property, features, variations,
            brandName, categoryName, multiCategory, multiBrand, multiTag, multiFeatures, multiVariations,
            multiNotation, multiPrices)

          handleCall(facetHandler.getProductCriteria(storeCode, param), (json: JValue) => complete(StatusCodes.OK, json))
      }
    }
  } ~ pathPrefix("skus") {
    pathEnd {
      get {
        val facetParam = parameters('priceInterval.as[Long] ::
          'productId.? ::
          'xtype.? ::
          'name.? ::
          'code.? ::
          'rootCategoryPath.? ::
          'categoryPath.? ::
          'brandId.? ::
          'tags.? ::
          'notations.? ::
          'priceRange.? ::
          'creationDateMin.? ::
          'featured.?.as[Option[Boolean]] ::
          'lang ? "_all" ::
          'country.? ::
          'promotionId.? ::
          'hasPromotion.?.as[Option[Boolean]] ::
          'inStockOnly.?.as[Option[Boolean]] ::
          'property.? ::
          'features.? ::
          'variations.? ::
          'brandName.? ::
          'categoryName.? ::
          'multiCategory.?.as[Option[Boolean]] ::
          'multiBrand.?.as[Option[Boolean]] ::
          'multiTag.?.as[Option[Boolean]] ::
          'multiFeatures.?.as[Option[Boolean]] ::
          'multiVariations.?.as[Option[Boolean]] ::
          'multiNotation.?.as[Option[Boolean]] ::
          'multiPrices.?.as[Option[Boolean]] ::
          HNil
        )
        facetParam.happly {
          case (priceInterval :: productId :: xtype :: name :: code :: rootCategoryPath :: categoryPath :: brandId :: tags :: notations :: priceRange ::
            creationDateMin :: featured :: lang :: country :: promotionId :: hasPromotion :: inStockOnly :: property :: features :: variations ::
            brandName :: categoryName :: multiCategory :: multiBrand :: multiTag :: multiFeatures :: multiVariations ::
            multiNotation :: multiPrices :: HNil) =>

            val param = new FacetRequest(priceInterval, productId, xtype, name, code, rootCategoryPath, categoryPath, brandId, tags, notations, priceRange,
              creationDateMin, featured, lang, country, promotionId, hasPromotion, inStockOnly, property, features, variations,
              brandName, categoryName, multiCategory, multiBrand, multiTag, multiFeatures, multiVariations,
              multiNotation, multiPrices)

            handleCall(facetHandler.getSkuCriteria(storeCode, param), (json: JValue) => complete(StatusCodes.OK, json))
        }
      }
    }
  }
}
