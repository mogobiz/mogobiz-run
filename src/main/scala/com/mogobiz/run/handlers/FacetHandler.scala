package com.mogobiz.run.handlers


import java.text.SimpleDateFormat
import java.util.Calendar

import com.mogobiz.es.EsClient
import com.mogobiz.run.config.HandlersConfig._
import com.mogobiz.run.es._
import com.mogobiz.run.model.RequestParameters.FacetRequest
import com.sksamuel.elastic4s.ElasticDsl.{search => esearch4s}
import com.sksamuel.elastic4s.{SearchDefinition, SearchType, FilterDefinition}
import org.json4s._
import org.json4s.JsonAST.{JObject, JValue}
import com.mogobiz.es.aggregations.Aggregations._
import com.sksamuel.elastic4s.ElasticDsl._

class FacetHandler {

  private val sdf = new SimpleDateFormat("yyyy-MM-dd")

  def getProductCriteria(storeCode: String, req: FacetRequest) : JValue = {
    val lang = if(req.lang=="_all") "" else s"${req.lang}."
    val _lang = if(req.lang=="_all") "_" else s"_${req.lang}"

    val fixeFilters: List[Option[FilterDefinition]] = List(
      createOrFilterBySplitValues(req.code, v => createTermFilter("product.code", Some(v))),
      createOrFilterBySplitValues(req.xtype, v => createTermFilter("product.xtype", Some(v))),
      createOrFilterBySplitValues(req.creationDateMin, v => createRangeFilter("product.dateCreated", Some(v), None)),
      createOrFilterBySplitValues(retrievePromotionsIds(storeCode, req), v => createTermFilter("product.coupons.id", Some(v))),
      createFeaturedRangeFilters(req),
      createAndOrFilterBySplitKeyValues(req.property, (k, v) => createTermFilter(k, Some(v)))
    )

    val categoryQuery = buildQueryAndFilters(FilterBuilder(withCategory = !req.multiCategory.getOrElse(false)), storeCode, req, fixeFilters) aggregations {
      aggregation terms "category" field "product.category.path" aggs {
        agg terms "name" field "product.category.name.raw"
      } aggs {
        agg terms s"name${_lang}" field s"product.category.${lang}name.raw"
      }
    }

    val brandQuery = buildQueryAndFilters(FilterBuilder(withBrand = !req.multiBrand.getOrElse(false)), storeCode, req, fixeFilters) aggs {
      aggregation terms "brand" field "product.brand.id" aggs {
        agg terms "name" field "product.brand.name.raw"
      } aggs {
        agg terms s"name${_lang}" field s"product.brand.${lang}name.raw"
      }
    }

    val tagQuery = buildQueryAndFilters(FilterBuilder(withTags = !req.multiTag.getOrElse(false)), storeCode, req, fixeFilters) aggs {
      aggregation nested ("tags") path("tags") aggs {
        aggregation terms "tags" field "product.tags.name.raw"
      }
    }

    val featuresQuery = buildQueryAndFilters(FilterBuilder(withFeatures = !req.multiFeatures.getOrElse(false)), storeCode, req, fixeFilters) aggs {
      aggregation nested ("features") path("features") aggs {
        aggregation terms "features_name" field s"product.features.name.raw" aggs {
          aggregation terms s"features_name${_lang}" field s"product.features.${lang}name.raw"
        } aggs {
          aggregation terms s"feature_values" field s"product.features.value.raw"
        } aggs {
          aggregation terms s"feature_values${_lang}" field s"product.features.${lang}value.raw"
        }
      }
    }

    val variationsQuery = buildQueryAndFilters(FilterBuilder(withVariations = !req.multiVariations.getOrElse(false)), storeCode, req, fixeFilters) aggs {
      aggregation terms "variation1_name" field s"product.skus.variation1.name.raw" aggs {
        aggregation terms s"variation1_name${_lang}" field s"product.skus.variation1.${lang}name.raw"
      } aggs {
        aggregation terms s"variation1_values" field s"product.skus.variation1.value.raw"
      } aggs {
        aggregation terms s"variation1_values${_lang}" field s"product.skus.variation1.${lang}value.raw"
      }
    } aggs {
      aggregation terms "variation2_name" field s"product.skus.variation2.name.raw" aggs {
        aggregation terms s"variation2_name${_lang}" field s"product.skus.variation2.${lang}name.raw"
      } aggs {
        aggregation terms s"variation2_values" field s"product.skus.variation2.value.raw"
      } aggs {
        aggregation terms s"variation2_values${_lang}" field s"product.skus.variation2.${lang}value.raw"
      }
    } aggs {
      aggregation terms "variation3_name" field s"product.skus.variation3.name.raw" aggs {
        aggregation terms s"variation3_name${_lang}" field s"product.skus.variation3.${lang}name.raw"
      } aggs {
        aggregation terms s"variation3_values" field s"product.skus.variation3.value.raw"
      } aggs {
        aggregation terms s"variation3_values${_lang}" field s"product.skus.variation3.${lang}value.raw"
      }
    }

    val notationQuery = buildQueryAndFilters(FilterBuilder(withNotation = !req.multiNotation.getOrElse(false)), storeCode, req, fixeFilters) aggs {
      aggregation nested ("notations") path("notations") aggs {
        aggregation terms "notation" field s"product.notations.notation" aggs {
          aggregation sum "nbcomments" field s"product.notations.nbcomments"
        }
      }
    }

    val priceQuery = buildQueryAndFilters(FilterBuilder(withPrice = !req.multiPrices.getOrElse(false)), storeCode, req, fixeFilters) aggs {
      aggregation histogram "prices" field "product.price" interval req.priceInterval minDocCount 0
    } aggs {
      aggregation min "price_min" field "product.price"
    } aggs {
      aggregation max "price_max" field "product.price"
    }

    val multiQueries = List(
      categoryQuery searchType SearchType.Count,
      brandQuery searchType SearchType.Count,
      tagQuery searchType SearchType.Count,
      featuresQuery searchType SearchType.Count,
      variationsQuery searchType SearchType.Count,
      notationQuery searchType SearchType.Count,
      priceQuery searchType SearchType.Count
    )

    EsClient.multiSearchAgg(multiQueries)
  }

  private def buildQueryAndFilters(builder: FilterBuilder, storeCode: String, req: FacetRequest, fixeFilters: List[Option[FilterDefinition]]) : SearchDefinition = {
    val filters = (
      fixeFilters
      :+ (if (builder.withCategory) createOrFilterBySplitValues(req.categoryPath, v => createRegexFilter("product.category.path", Some(v))) else None)
      :+ (if (builder.withCategory) createOrFilterBySplitValues(req.categoryName.map(_.toLowerCase), v => createTermFilter("product.category.name", Some(v))) else None)
      :+ (if (builder.withBrand) createOrFilterBySplitValues(req.brandId, v => createTermFilter("product.brand.id", Some(v))) else None)
      :+ (if (builder.withBrand) createOrFilterBySplitValues(req.brandName.map(_.toLowerCase), v => createTermFilter("product.brand.name", Some(v))) else None)
      :+ (if (builder.withTags) createOrFilterBySplitValues(req.tags.map(_.toLowerCase), v => createNestedTermFilter("tags", "product.tags.name", Some(v))) else None)
      :+ (if (builder.withFeatures) createFeaturesFilters(req) else None)
      :+ (if (builder.withVariations) createVariationsFilters(req) else None)
      :+ (if (builder.withNotation) createOrFilterBySplitValues(req.notations, v => createNestedTermFilter("notations","product.notations.notation", Some(v))) else None)
      :+ (if (builder.withPrice) createOrFilterBySplitKeyValues(req.priceRange, (min, max) => createNumericRangeFilter("product.price", min, max)) else None)
    ).flatten

    filterRequest(buildQueryPart(storeCode, req), filters)
  }

  /**
   * Renvoie tous les ids des promotions si la requêtes demandes n'importe
   * quelle promotion ou l'id de la promotion fournie dans la requête
   * @param storeCode
   * @param req
   */
  private def retrievePromotionsIds(storeCode: String, req: FacetRequest) : Option[String] = {
    if (req.hasPromotion.getOrElse(false)) {
      val ids = promotionHandler.getPromotionIds(storeCode)
      if (ids.isEmpty) None
      else Some(ids.mkString("|"))
    }
    else req.promotionId
  }

  /**
   * Renvoie la requête (sans les filtres) à utiliser. Si le nom du produit
   * est spécifié, la requête contient un match sur le nom du produit
   * @param storeCode
   * @param req
   * @return
   */
  private def buildQueryPart(storeCode: String, req: FacetRequest) = {
    req.name match {
      case Some(s) =>
        esearch4s in storeCode -> "product" query {
          matchQuery("name", s)
        }
      case None => esearch4s in storeCode -> "product"
    }
  }

  /**
   * Renvoie le filtres permettant de filtrer les produits mis en avant
   * si la requête le demande
   * @param req
   * @return
   */
  private def createFeaturedRangeFilters(req: FacetRequest): Option[FilterDefinition] = {
    if (req.featured.getOrElse(false)) {
      val today = sdf.format(Calendar.getInstance().getTime)
      val list = List(
        createRangeFilter("product.startFeatureDate", None, Some(s"$today")),
        createRangeFilter("product.stopFeatureDate", Some(s"$today"), None)
      ).flatten
      Some(and(list: _*))
    }
    else None
  }

  /**
   * Renvoie le filtre pour les features
   * @param req
   * @return
   */
  private def createFeaturesFilters(req: FacetRequest): Option[FilterDefinition] = {
    createAndOrFilterBySplitKeyValues(req.features, (k, v) => {
      Some(
        must(
          List(
            createNestedTermFilter("features", s"product.features.name.raw", Some(k)),
            createNestedTermFilter("features", s"product.features.value.raw", Some(v))
          ).flatten: _*
        )
      )
    })
  }

  /**
   * Renvoie la liste des filtres pour les variations
   * @param req
   * @return
   */
  private def createVariationsFilters(req: FacetRequest): Option[FilterDefinition] = {
    createAndOrFilterBySplitKeyValues(req.variations, (k, v) => {
      Some(
        or(
          must(
            List(
              createTermFilter(s"product.skus.variation1.name.raw", Some(k)),
              createTermFilter(s"product.skus.variation1.value.raw", Some(v))
            ).flatten:_*
          )
          ,must(
            List(
              createTermFilter(s"product.skus.variation2.name.raw", Some(k)),
              createTermFilter(s"product.skus.variation2.value.raw", Some(v))
            ).flatten:_*
          )
          ,must(
            List(
              createTermFilter(s"product.skus.variation3.name.raw", Some(k)),
              createTermFilter(s"product.skus.variation3.value.raw", Some(v))
            ).flatten:_*
          )
        )
      )
    })
  }

  def getCommentNotations(storeCode: String, productId: Option[Long]) : List[JValue] = {
    val filters = List(createTermFilter("productId", productId)).flatten
    val req = filterRequest(esearch4s in s"${storeCode}_comment" types "comment", filters) aggs {
        agg terms "notations" field "notation"
      } searchType SearchType.Count

    val resagg = EsClient searchAgg req

    for {
      JArray(buckets) <- resagg \ "notations" \ "buckets"
      JObject(bucket) <- buckets
      JField("key_as_string", JString(key)) <- bucket
      JField("doc_count", JInt(value)) <- bucket
    } yield  JObject(List(JField("notation", JString(key)), JField("nbcomments", JInt(value))))
  }

  private case class FilterBuilder(withCategory: Boolean = true,
                                   withBrand: Boolean = true,
                                   withTags: Boolean = true,
                                   withFeatures: Boolean = true,
                                   withVariations: Boolean = true,
                                   withNotation: Boolean = true,
                                   withPrice: Boolean = true)
}
