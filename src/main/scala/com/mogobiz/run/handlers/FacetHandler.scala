/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import java.util.Calendar

import com.mogobiz.es.EsClient
import com.mogobiz.es.aggregations.Aggregations._
import com.mogobiz.run.config.MogobizHandlers.handlers._
import com.mogobiz.run.es._
import com.mogobiz.run.model.RequestParameters.FacetRequest
import com.sksamuel.elastic4s.ElasticDsl.{ search => esearch4s, _ }
import com.sksamuel.elastic4s.{ FilterDefinition, SearchDefinition, SearchType }
import org.joda.time.format.DateTimeFormat
import org.json4s.JsonAST.{ JObject, JValue }
import org.json4s._

class FacetHandler {

  private val sdf = DateTimeFormat.forPattern("yyyy-MM-dd")

  private val ES_TYPE_SKU = "sku"
  private val ES_TYPE_PRODUCT = "product"

  /**
   * Returns facets for skus
   *
   * @param storeCode - store code
   * @param req - facet request
   * @return
   */
  def getSkuCriteria(storeCode: String, req: FacetRequest): JValue = {
    val lang = if (req.lang == "_all") "" else s"${req.lang}."
    val _lang = if (req.lang == "_all") "_" else s"_${req.lang}"

    val priceWithVatField = req.country match {
      case Some(countryCode) => s"$countryCode.saleEndPrice"
      case _ => "salePrice"
    }

    val defaultStockFilter = if (req.inStockOnly.getOrElse(true)) {
      val orFilters = List(
        not(existsFilter("stock")),
        createTermFilter("stock.available", Some(true)).get
      )
      Some(or(orFilters: _*))
    } else {
      None
    }

    val fixeFilters: List[Option[FilterDefinition]] = List(
      defaultStockFilter,
      req.country.map { countryCode => termFilter(s"sku.$countryCode.enabled", true) },
      createTermFilter("sku.product.id", req.productId),
      createOrFilterBySplitValues(req.code, v => createTermFilter("sku.product.code", Some(v))),
      createOrFilterBySplitValues(req.xtype, v => createTermFilter("sku.product.xtype", Some(v))),
      createOrFilterBySplitValues(req.creationDateMin, v => createRangeFilter("sku.product.dateCreated", Some(v), None)),
      createOrFilterBySplitValues(retrievePromotionsIds(storeCode, req), v => createTermFilter("sku.coupons.id", Some(v))),
      createFeaturedRangeFilters(req, "sku.product"),
      createAndOrFilterBySplitKeyValues(req.property, (k, v) => createTermFilter(k, Some(v)))
    )

    val categoryQuery = buildQueryAndFilters(FilterBuilder(withCategory = !req.multiCategory.getOrElse(false)), storeCode, ES_TYPE_SKU, req, fixeFilters) aggregations {
      aggregation terms "category" field "sku.product.category.path" aggs {
        agg terms "name" field "sku.product.category.name.raw"
      } aggs {
        agg terms s"name${_lang}" field s"sku.product.category.${lang}name.raw"
      }
    }

    val brandQuery = buildQueryAndFilters(FilterBuilder(withBrand = !req.multiBrand.getOrElse(false)), storeCode, ES_TYPE_SKU, req, fixeFilters) aggs {
      aggregation terms "brand" field "sku.product.brand.id" aggs {
        agg terms "name" field "sku.product.brand.name.raw"
      } aggs {
        agg terms s"name${_lang}" field s"sku.product.brand.${lang}name.raw"
      }
    }

    val tagQuery = buildQueryAndFilters(FilterBuilder(withTags = !req.multiTag.getOrElse(false)), storeCode, ES_TYPE_SKU, req, fixeFilters) aggs {
      aggregation nested "tags" path "sku.product.tags" aggs {
        aggregation terms "tags" field "sku.product.tags.name.raw"
      }
    }

    val featuresQuery = buildQueryAndFilters(FilterBuilder(withFeatures = !req.multiFeatures.getOrElse(false)), storeCode, ES_TYPE_SKU, req, fixeFilters) aggs {
      aggregation nested "features" path "sku.product.features" aggs {
        aggregation terms "features_name" field s"sku.product.features.name.raw" aggs {
          aggregation terms s"features_name${_lang}" field s"sku.product.features.${lang}name.raw"
        } aggs {
          aggregation terms s"feature_values" field s"sku.product.features.value.raw"
        } aggs {
          aggregation terms s"feature_values${_lang}" field s"sku.product.features.${lang}value.raw"
        }
      }
    }

    val variationsQuery = buildQueryAndFilters(FilterBuilder(withVariations = !req.multiVariations.getOrElse(false)), storeCode, ES_TYPE_SKU, req, fixeFilters) aggs {
      aggregation terms "variation1_name" field s"sku.variation1.name.raw" aggs {
        aggregation terms s"variation1_name${_lang}" field s"sku.variation1.${lang}name.raw"
      } aggs {
        aggregation terms s"variation1_values" field s"sku.variation1.value.raw"
      } aggs {
        aggregation terms s"variation1_values${_lang}" field s"sku.variation1.${lang}value.raw"
      }
    } aggs {
      aggregation terms "variation2_name" field s"sku.variation2.name.raw" aggs {
        aggregation terms s"variation2_name${_lang}" field s"sku.variation2.${lang}name.raw"
      } aggs {
        aggregation terms s"variation2_values" field s"sku.variation2.value.raw"
      } aggs {
        aggregation terms s"variation2_values${_lang}" field s"sku.variation2.${lang}value.raw"
      }
    } aggs {
      aggregation terms "variation3_name" field s"sku.variation3.name.raw" aggs {
        aggregation terms s"variation3_name${_lang}" field s"sku.variation3.${lang}name.raw"
      } aggs {
        aggregation terms s"variation3_values" field s"sku.variation3.value.raw"
      } aggs {
        aggregation terms s"variation3_values${_lang}" field s"sku.variation3.${lang}value.raw"
      }
    }

    val notationQuery = buildQueryAndFilters(FilterBuilder(withNotation = !req.multiNotation.getOrElse(false)), storeCode, ES_TYPE_SKU, req, fixeFilters) aggs {
      aggregation nested "notations" path "notations" aggs {
        aggregation terms "notation" field s"product.notations.notation" aggs {
          aggregation sum "nbcomments" field s"product.notations.nbcomments"
        }
      }
    }

    val priceQuery = buildQueryAndFilters(FilterBuilder(withPrice = !req.multiPrices.getOrElse(false)), storeCode, ES_TYPE_SKU, req, fixeFilters) aggs {
      aggregation histogram "prices" field s"sku.$priceWithVatField" interval req.priceInterval minDocCount 0
    } aggs {
      aggregation min "price_min" field s"sku.$priceWithVatField"
    } aggs {
      aggregation max "price_max" field s"sku.$priceWithVatField"
    }

    val multiQueries = List(
      categoryQuery searchType SearchType.Count,
      brandQuery searchType SearchType.Count,
      tagQuery searchType SearchType.Count,
      featuresQuery searchType SearchType.Count,
      variationsQuery searchType SearchType.Count,
      //      notationQuery searchType SearchType.Count,
      priceQuery searchType SearchType.Count
    )

    EsClient.multiSearchAgg(multiQueries)
  }

  /**
   * Returns products facets
   *
   * @param storeCode - store code
   * @param req - facet request
   * @return
   */
  def getProductCriteria(storeCode: String, req: FacetRequest): JValue = {

    val lang = if (req.lang == "_all") "" else s"${req.lang}."
    val _lang = if (req.lang == "_all") "_" else s"_${req.lang}"

    val priceWithVatField = req.country match {
      case Some(countryCode) => s"$countryCode.saleEndPrice"
      case _ => "salePrice"
    }

    val defaultStockFilter = if (req.inStockOnly.getOrElse(true)) {
      val orFilters = List(
        not(existsFilter("stockAvailable")),
        createTermFilter("stockAvailable", Some(true)).get
      )
      Some(or(orFilters: _*))
    } else {
      None
    }

    val fixeFilters: List[Option[FilterDefinition]] = List(
      defaultStockFilter,
      req.country.map { countryCode => termFilter(s"product.$countryCode.enabled", true) },
      createOrFilterBySplitValues(req.code, v => createTermFilter("product.code", Some(v))),
      createOrFilterBySplitValues(req.xtype, v => createTermFilter("product.xtype", Some(v))),
      createOrFilterBySplitValues(req.creationDateMin, v => createRangeFilter("product.dateCreated", Some(v), None)),
      createOrFilterBySplitValues(retrievePromotionsIds(storeCode, req), v => createTermFilter("product.coupons.id", Some(v))),
      createFeaturedRangeFilters(req, "product"),
      createAndOrFilterBySplitKeyValues(req.property, (k, v) => createTermFilter(k, Some(v)))
    )

    val categoryAggregation = aggregation terms "category" field "product.category.path" aggs {
      agg terms "name" field "product.category.name.raw"
    } aggs {
      agg terms s"name${_lang}" field s"product.category.${lang}name.raw"
    }

    val brandAggregation = aggregation terms "brand" field "product.brand.id" aggs {
      agg terms "name" field "product.brand.name.raw"
    } aggs {
      agg terms s"name${_lang}" field s"product.brand.${lang}name.raw"
    }

    val tagAggregation = aggregation nested "tags" path "tags" aggs {
      aggregation terms "tags" field "product.tags.name.raw"
    }

    val featuresAggregation = aggregation nested "features" path "features" aggs {
      aggregation terms "features_name" field s"product.features.name.raw" aggs {
        aggregation terms s"features_name${_lang}" field s"product.features.${lang}name.raw"
      } aggs {
        aggregation terms s"feature_values" field s"product.features.value.raw"
      } aggs {
        aggregation terms s"feature_values${_lang}" field s"product.features.${lang}value.raw"
      }
    }

    val variation1Aggregation = aggregation terms "variation1_name" field s"product.skus.variation1.name.raw" aggs {
      aggregation terms s"variation1_name${_lang}" field s"product.skus.variation1.${lang}name.raw"
    } aggs {
      aggregation terms s"variation1_values" field s"product.skus.variation1.value.raw"
    } aggs {
      aggregation terms s"variation1_values${_lang}" field s"product.skus.variation1.${lang}value.raw"
    }
    val variation2Aggregation = aggregation terms "variation2_name" field s"product.skus.variation2.name.raw" aggs {
      aggregation terms s"variation2_name${_lang}" field s"product.skus.variation2.${lang}name.raw"
    } aggs {
      aggregation terms s"variation2_values" field s"product.skus.variation2.value.raw"
    } aggs {
      aggregation terms s"variation2_values${_lang}" field s"product.skus.variation2.${lang}value.raw"
    }
    val variation3Aggregation = aggregation terms "variation3_name" field s"product.skus.variation3.name.raw" aggs {
      aggregation terms s"variation3_name${_lang}" field s"product.skus.variation3.${lang}name.raw"
    } aggs {
      aggregation terms s"variation3_values" field s"product.skus.variation3.value.raw"
    } aggs {
      aggregation terms s"variation3_values${_lang}" field s"product.skus.variation3.${lang}value.raw"
    }

    val notationAggregation = aggregation nested "notations" path "notations" aggs {
      aggregation terms "notation" field s"product.notations.notation" aggs {
        aggregation sum "nbcomments" field s"product.notations.nbcomments"
      }
    }

    val priceAggregation = aggregation histogram "prices" field s"product.skus.$priceWithVatField" interval req.priceInterval minDocCount 0
    val priceMinAggregation = aggregation min "price_min" field s"product.skus.$priceWithVatField"
    val priceMaxAggregation = aggregation max "price_max" field s"product.skus.$priceWithVatField"

    val query = buildQueryAndFilters(FilterBuilder(withCategory = !req.multiCategory.getOrElse(false)), storeCode, ES_TYPE_PRODUCT, req, fixeFilters)

    val singleQuery = query aggs {
      List(
        categoryAggregation,
        brandAggregation,
        tagAggregation,
        featuresAggregation,
        variation1Aggregation,
        variation2Aggregation,
        variation3Aggregation,
        notationAggregation,
        priceAggregation,
        priceMinAggregation,
        priceMaxAggregation
      ):_*
    } searchType SearchType.Count

    EsClient.searchAgg(singleQuery)

//    val categoryQuery = query aggs { categoryAggregation }
//    val brandQuery = query aggs { brandAggregation }
//    val tagQuery = query aggs { tagAggregation }
//    val featuresQuery = query aggs { featuresAggregation }
//    val variationsQuery = query aggs { variation1Aggregation } aggs {variation2Aggregation } aggs { variation3Aggregation }
//    val notationQuery = query aggs { notationAggregation }
//    val priceQuery = query aggs { priceAggregation } aggs { priceMinAggregation } aggs { priceMaxAggregation }
//    val multiQueries = List(
//      categoryQuery searchType SearchType.Count,
//      brandQuery searchType SearchType.Count,
//      tagQuery searchType SearchType.Count,
//      featuresQuery searchType SearchType.Count,
//      variationsQuery searchType SearchType.Count,
//      notationQuery searchType SearchType.Count,
//      priceQuery searchType SearchType.Count
//    )
//    EsClient.multiSearchAgg(multiQueries)
  }

  private def buildQueryAndFilters(builder: FilterBuilder, storeCode: String, esType: String, req: FacetRequest, fixeFilters: List[Option[FilterDefinition]]): SearchDefinition = {

    //println("filterBuilder=",builder)
    val priceWithVatField = req.country match {
      case Some(countryCode) => s"$countryCode.saleEndPrice"
      case _ => "salePrice"
    }

    val filters = esType match {
      case ES_TYPE_PRODUCT =>
        (
          fixeFilters
          :+ (if (builder.withCategory) createOrFilterBySplitValues(req.categoryPath, v => createRegexFilter("product.category.path", Some(v))) else None)
          :+ (if (builder.withCategory) createOrFilterBySplitValues(req.categoryName.map(_.toLowerCase), v => createTermFilter("product.category.name", Some(v))) else None)
          :+ (if (builder.withBrand) createOrFilterBySplitValues(req.brandId, v => createTermFilter("product.brand.id", Some(v))) else None)
          :+ (if (builder.withBrand) createOrFilterBySplitValues(req.brandName.map(_.toLowerCase), v => createTermFilter("product.brand.name", Some(v))) else None)
          :+ (if (builder.withTags) createOrFilterBySplitValues(req.tags.map(_.toLowerCase), v => createNestedTermFilter("tags", "tags.name", Some(v))) else None)
          :+ (if (builder.withFeatures) createFeaturesFilters(req) else None)
          :+ (if (builder.withVariations) createVariationsFilters(req, "product.skus") else None)
          :+ (if (builder.withNotation) createOrFilterBySplitValues(req.notations, v => createNestedTermFilter("notations", "notations.notation", Some(v))) else None)
          :+ (if (builder.withPrice) createOrFilterBySplitKeyValues(req.priceRange, (min, max) => createNumericRangeFilter(s"product.skus.$priceWithVatField", min, max)) else None)
        ).flatten
      case ES_TYPE_SKU =>
        (
          fixeFilters
          :+ (if (builder.withCategory) createOrFilterBySplitValues(req.categoryPath, v => createRegexFilter("sku.product.category.path", Some(v))) else None)
          :+ (if (builder.withCategory) createOrFilterBySplitValues(req.categoryName.map(_.toLowerCase), v => createTermFilter("sku.product.category.name", Some(v))) else None)
          :+ (if (builder.withBrand) createOrFilterBySplitValues(req.brandId, v => createTermFilter("sku.product.brand.id", Some(v))) else None)
          :+ (if (builder.withBrand) createOrFilterBySplitValues(req.brandName.map(_.toLowerCase), v => createTermFilter("sku.product.brand.name", Some(v))) else None)
          :+ (if (builder.withTags) createOrFilterBySplitValues(req.tags.map(_.toLowerCase), v => createNestedTermFilter("product.tags", "product.tags.name", Some(v))) else None)
          :+ (if (builder.withFeatures) {
            //createFeaturesFilters (req,"product")
            createAndOrFilterBySplitKeyValues(req.features, (k, v) => {
              Some(
                must(
                  List(
                    createNestedTermFilter("product.features", "product.features.name.raw", Some(k)),
                    createNestedTermFilter("product.features", "product.features.value.raw", Some(v))
                  ).flatten: _*
                )
              )
            })
          } else None)
          :+ (if (builder.withVariations) createVariationsFilters(req, "sku") else None)
          //:+ (if (builder.withNotation) createOrFilterBySplitValues (req.notations, v => createNestedTermFilter ("notations", "product.notations.notation", Some (v) ) ) else None)
          :+ (if (builder.withPrice) createOrFilterBySplitKeyValues(req.priceRange, (min, max) => createNumericRangeFilter(s"sku.$priceWithVatField", min, max)) else None)
        ).flatten
      case _ => List()
    }
    filterRequest(buildQueryPart(storeCode, esType, req), filters)
  }

  /**
   * Renvoie tous les ids des promotions si la requêtes demandes n'importe
   * quelle promotion ou l'id de la promotion fournie dans la requête
   *
   * @param storeCode - store code
   * @param req - facet request
   */
  private def retrievePromotionsIds(storeCode: String, req: FacetRequest): Option[String] = {
    if (req.hasPromotion.getOrElse(false)) {
      val ids = promotionHandler.getPromotionIds(storeCode)
      if (ids.isEmpty) None
      else Some(ids.mkString("|"))
    } else req.promotionId
  }

  /**
   * Renvoie la requête (sans les filtres) à utiliser. Si le nom du produit
   * est spécifié, la requête contient un match sur le nom du produit
   *
   * @param storeCode - store code
   * @param req - facet request
   * @return
   */
  private def buildQueryPart(storeCode: String, esType: String, req: FacetRequest) = {
    req.name match {
      case Some(s) =>
        esearch4s in storeCode -> esType query {
          matchQuery("name", s)
        }
      case None => esearch4s in storeCode -> esType
    }
  }

  /**
   * Renvoie le filtres permettant de filtrer les produits mis en avant
   * si la requête le demande
   *
   * @param req - facet request
   * @param propertyPathPrefix prefixe du chemin vers la propriété (ex: product ou sku.product)
   * @return
   */
  private def createFeaturedRangeFilters(req: FacetRequest, propertyPathPrefix: String): Option[FilterDefinition] = {
    if (req.featured.getOrElse(false)) {
      val today = sdf.print(Calendar.getInstance().getTimeInMillis)
      val list = List(
        createRangeFilter(s"$propertyPathPrefix.startFeatureDate", None, Some(s"$today")),
        createRangeFilter(s"$propertyPathPrefix.stopFeatureDate", Some(s"$today"), None)
      ).flatten
      Some(and(list: _*))
    } else None
  }

  /**
   * Renvoie le filtre pour les features
   *
   * @param req - facet request
   * @return
   */
  private def createFeaturesFilters(req: FacetRequest): Option[FilterDefinition] = {
    createAndOrFilterBySplitKeyValues(req.features, (k, v) => {
      Some(
        must(
          List(
            createNestedTermFilter("features", "features.name.raw", Some(k)),
            createNestedTermFilter("features", "features.value.raw", Some(v))
          ).flatten: _*
        )
      )
    })
  }

  /**
   * Renvoie la liste des filtres pour les variations
   *
   * @param req - facet request
   * @return
   */
  private def createVariationsFilters(req: FacetRequest, propertyPathPrefix: String): Option[FilterDefinition] = {
    createAndOrFilterBySplitKeyValues(req.variations, (k, v) => {
      Some(
        or(
          must(
            List(
              createTermFilter(s"$propertyPathPrefix.variation1.name.raw", Some(k)),
              createTermFilter(s"$propertyPathPrefix.variation1.value.raw", Some(v))
            ).flatten: _*
          ), must(
            List(
              createTermFilter(s"$propertyPathPrefix.variation2.name.raw", Some(k)),
              createTermFilter(s"$propertyPathPrefix.variation2.value.raw", Some(v))
            ).flatten: _*
          ), must(
            List(
              createTermFilter(s"$propertyPathPrefix.variation3.name.raw", Some(k)),
              createTermFilter(s"$propertyPathPrefix.variation3.value.raw", Some(v))
            ).flatten: _*
          )
        )
      )
    })
  }

  def getCommentNotations(storeCode: String, productId: Option[Long]): List[JValue] = {
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
    } yield JObject(List(JField("notation", JString(key)), JField("nbcomments", JInt(value))))
  }

  private case class FilterBuilder(withCategory: Boolean = true,
    withBrand: Boolean = true,
    withTags: Boolean = true,
    withFeatures: Boolean = true,
    withVariations: Boolean = true,
    withNotation: Boolean = true,
    withPrice: Boolean = true)
}
