/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import java.util.Calendar

import com.mogobiz.es.EsClient
import com.mogobiz.run.config.MogobizHandlers.handlers._
import com.mogobiz.run.es._
import com.mogobiz.run.model.RequestParameters.FacetRequest
import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.searches.SearchDefinition
import com.sksamuel.elastic4s.searches.aggs.TermsAggregationDefinition
import com.sksamuel.elastic4s.searches.queries.{BoolQueryDefinition, QueryDefinition}
import org.elasticsearch.action.search.SearchType
import org.joda.time.format.DateTimeFormat
import org.json4s.JsonAST.{JObject, JValue}
import org.json4s._

class FacetHandler {

  private val sdf = DateTimeFormat.forPattern("yyyy-MM-dd")

  private val EsTypeSku     = "sku"
  private val EsTypeProduct = "product"

  /**
    * Returns facets for skus
    *
    * @param storeCode - store code
    * @param req       - facet request
    * @return
    */
  def getSkuCriteria(storeCode: String, req: FacetRequest): JValue = {
    val lang  = if (req.lang == "_all") "" else s"${req.lang}."
    val _lang = if (req.lang == "_all") "_" else s"_${req.lang}"

    val priceWithVatField = req.country match {
      case Some(countryCode) => s"$countryCode.saleEndPrice"
      case _                 => "salePrice"
    }

    val defaultStockFilter: Option[BoolQueryDefinition] = if (req.inStockOnly.getOrElse(true)) {
      val orFilters = List(
          not(existsQuery("stock")),
          createtermQuery("stock.available", Some(true)).get
      )
      Some(should(orFilters))
    } else {
      None
    }

    val fixeFilters: List[Option[QueryDefinition]] = List(
        defaultStockFilter,
        req.country.map { countryCode =>
          termQuery(s"sku.$countryCode.enabled", true)
        },
        createtermQuery("sku.product.id", req.productId),
        createOrFilterBySplitValues(req.code, v => createtermQuery("sku.product.code", Some(v))),
        createOrFilterBySplitValues(req.xtype, v => createtermQuery("sku.product.xtype", Some(v))),
        createOrFilterBySplitValues(req.creationDateMin, v =>
              createRangeFilter("sku.product.dateCreated", Some(v), None)),
        createOrFilterBySplitValues(req.rootCategoryPath, v =>
              createRegexFilter(s"sku.product.category.path", Some(v))),
        createOrFilterBySplitValues(retrievePromotionsIds(storeCode, req), v =>
              createtermQuery("sku.coupons.id", Some(v))),
        createFeaturedRangeFilters(req, "sku.product"),
        createAndOrFilterBySplitKeyValues(req.property, (k, v) => createtermQuery(k, Some(v)))
    )

    val includeCategoryInGlobalQuery = (req.categoryPath.isEmpty && req.categoryName.isEmpty) || !req.multiCategory
        .getOrElse(false)
    val includeBrandInGlobalQuery = (req.brandId.isEmpty && req.brandName.isEmpty) || !req.multiBrand.getOrElse(false)
    val includeTagsInGlobalQuery  = req.tags.isEmpty || !req.multiTag.getOrElse(false)
    val includePriceInGlobalQuery = req.priceRange.isEmpty || !req.multiPrices.getOrElse(false)

    val variationsExcludedFromGlobalQuery: List[String] =
      if (req.variations.isDefined && req.multiVariations.getOrElse(false))
        (req.variations.get.split("""\|\|\|""") map (v => {
                  v.split("""\:\:\:""").head
                })).toList
      else List()

    val featuresExcludedFromGlobalQuery: List[String] =
      if (req.features.isDefined && req.multiFeatures.getOrElse(false))
        (req.features.get.split("""\|\|\|""") map (v => {
                  v.split("""\:\:\:""").head
                })).toList
      else List()

    val categoryAggregation = termsAggregation("category").field("sku.product.category.path").subAggregation {
      termsAggregation("name").field("sku.product.category.name.raw")
    } subAggregation {
      termsAggregation(s"name${_lang}").field(s"sku.product.category.${lang}name.raw")
    }

    val brandAggregation = termsAggregation("brand").field("sku.product.brand.id").subAggregation {
      termsAggregation("name").field("sku.product.brand.name.raw")
    } subAggregation {
      termsAggregation(s"name${_lang}").field(s"sku.product.brand.${lang}name.raw")
    }

    val tagAggregation = nestedAggregation("tags", "sku.product.tags").subAggregation {
      termsAggregation("tags").field("sku.product.tags.name.raw")
    }

    def featuresAggregation(includeFeatures: List[String] = List(), excludeFeatures: List[String] = List()) = {
      val nested: TermsAggregationDefinition = termsAggregation("features_name")
          .field(s"sku.product.features.name.raw")
          .subAggregation {
          termsAggregation(s"features_name${_lang}").field(s"sku.product.features.${lang}name.raw")
        } subAggregation {
        termsAggregation(s"feature_values").field(s"sku.product.features.value.raw")
      } subAggregation {
        termsAggregation(s"feature_values${_lang}").field(s"sku.product.features.${lang}value.raw")
      }
      nestedAggregation("features", "sku.product.features").subaggs(
          nested.includeExclude(includeFeatures, excludeFeatures))
    }

    def variationAggregation(index: String,
                             includeVariations: List[String] = List(),
                             excludeVariations: List[String] = List()) = {
      val aggregate = termsAggregation(s"variation${index}_name")
          .field(s"sku.variation$index.name.raw")
          .subAggregation {
          termsAggregation(s"variation${index}_name${_lang}").field(s"sku.variation$index.${lang}name.raw")
        } subAggregation {
        termsAggregation(s"variation${index}_values").field(s"sku.variation$index.value.raw")
      } subAggregation {
        termsAggregation(s"variation${index}_values${_lang}").field(s"sku.variation$index.${lang}value.raw")
      }
      aggregate.includeExclude(includeVariations, excludeVariations)
    }

    val notationAggregation = nestedAggregation("notations", "notations").subaggs {
      termsAggregation("notation").field(s"product.notations.notation") subaggs {
        sumAgg("nbcomments", s"product.notations.nbcomments")
      }
    }

    val priceAggregation =
      histogramAggregation("prices").field(s"sku.$priceWithVatField").interval(req.priceInterval).minDocCount(0)
    val priceMinAggregation = minAggregation("price_min").field(s"sku.$priceWithVatField")
    val priceMaxAggregation = maxAggregation("price_max").field(s"sku.$priceWithVatField")

    val query = buildQueryAndFilters(FilterBuilder(), storeCode, EsTypeSku, req, fixeFilters)

    val singleQuery = Some(query aggs {
      List(
          if (includeCategoryInGlobalQuery) Some(categoryAggregation) else None,
          if (includeBrandInGlobalQuery) Some(brandAggregation) else None,
          if (includeTagsInGlobalQuery) Some(tagAggregation) else None,
          Some(featuresAggregation(excludeFeatures = variationsExcludedFromGlobalQuery)),
          Some(variationAggregation("1", excludeVariations = variationsExcludedFromGlobalQuery)),
          Some(variationAggregation("2", excludeVariations = variationsExcludedFromGlobalQuery)),
          Some(variationAggregation("3", excludeVariations = variationsExcludedFromGlobalQuery)),
          if (includePriceInGlobalQuery) Some(priceAggregation) else None,
          if (includePriceInGlobalQuery) Some(priceMinAggregation) else None,
          if (includePriceInGlobalQuery) Some(priceMaxAggregation) else None
      ).flatten
    } searchType SearchType.DFS_QUERY_THEN_FETCH)

    val categoryQuery =
      if (includeCategoryInGlobalQuery) None
      else
        Some(
            buildQueryAndFilters(FilterBuilder(withCategoryFilter = false), storeCode, EsTypeSku, req, fixeFilters) aggs
              categoryAggregation searchType SearchType.DFS_QUERY_THEN_FETCH)

    val brandQuery =
      if (includeBrandInGlobalQuery) None
      else
        Some(
            buildQueryAndFilters(FilterBuilder(withBrandFilter = false), storeCode, EsTypeSku, req, fixeFilters) aggs
              brandAggregation searchType SearchType.DFS_QUERY_THEN_FETCH)

    val tagQuery =
      if (includeTagsInGlobalQuery) None
      else
        Some(
            buildQueryAndFilters(FilterBuilder(withTagsFilter = false), storeCode, EsTypeSku, req, fixeFilters) aggs
              tagAggregation searchType SearchType.DFS_QUERY_THEN_FETCH)

    val featuresQueries = featuresExcludedFromGlobalQuery map { v =>
      Some(
          buildQueryAndFilters(FilterBuilder(featuresExcludedFromFilter = List(v)),
                               storeCode,
                               EsTypeSku,
                               req,
                               fixeFilters) aggs
            featuresAggregation(includeFeatures = List(v)) searchType SearchType.DFS_QUERY_THEN_FETCH)
    }

    val variationsQueries = variationsExcludedFromGlobalQuery map { v =>
      Some(
          buildQueryAndFilters(FilterBuilder(variationsExcludedFromFilter = List(v)),
                               storeCode,
                               EsTypeSku,
                               req,
                               fixeFilters) aggs
            variationAggregation(index = "1", includeVariations = List(v)) aggs variationAggregation(index = "2",
                                                                                                     includeVariations =
                                                                                                       List(v)) aggs variationAggregation(
              index = "3",
              includeVariations = List(v)) searchType SearchType.DFS_QUERY_THEN_FETCH)
    }

    val priceQuery =
      if (includePriceInGlobalQuery) None
      else
        Some(
            buildQueryAndFilters(FilterBuilder(withPriceFilter = false), storeCode, EsTypeSku, req, fixeFilters) aggs
              priceAggregation aggs priceMinAggregation aggs priceMaxAggregation searchType SearchType.DFS_QUERY_THEN_FETCH)

    val multiQueries = List(
          singleQuery,
          categoryQuery,
          brandQuery,
          tagQuery,
          priceQuery
      ) ::: featuresQueries ::: variationsQueries

    EsClient.multiSearchAgg(multiQueries.flatten)
  }

  /**
    * Returns products facets
    *
    * @param storeCode - store code
    * @param req       - facet request
    * @return
    */
  def getProductCriteria(storeCode: String, req: FacetRequest): JValue = {

    val lang  = if (req.lang == "_all") "" else s"${req.lang}."
    val _lang = if (req.lang == "_all") "_" else s"_${req.lang}"

    val priceWithVatField = req.country match {
      case Some(countryCode) => s"$countryCode.saleEndPrice"
      case _                 => "salePrice"
    }

    val defaultStockFilter = if (req.inStockOnly.getOrElse(true)) {
      val orFilters = List(
          not(existsQuery("stockAvailable")),
          createtermQuery("stockAvailable", Some(true)).get
      )
      Some(should(orFilters))
    } else {
      None
    }

    val fixeFilters: List[Option[QueryDefinition]] = List(
        defaultStockFilter,
        req.country.map { countryCode =>
          termQuery(s"product.$countryCode.enabled", true)
        },
        createOrFilterBySplitValues(req.code, v => createtermQuery("product.code", Some(v))),
        createOrFilterBySplitValues(req.xtype, v => createtermQuery("product.xtype", Some(v))),
        createOrFilterBySplitValues(req.creationDateMin, v => createRangeFilter("product.dateCreated", Some(v), None)),
        createOrFilterBySplitValues(req.rootCategoryPath, v => createRegexFilter(s"product.category.path", Some(v))),
        createOrFilterBySplitValues(retrievePromotionsIds(storeCode, req), v =>
              createtermQuery("product.coupons.id", Some(v))),
        createFeaturedRangeFilters(req, "product"),
        createAndOrFilterBySplitKeyValues(req.property, (k, v) => createtermQuery(k, Some(v)))
    )

    val includeCategoryInGlobalQuery = (req.categoryPath.isEmpty && req.categoryName.isEmpty) || !req.multiCategory
        .getOrElse(false)
    val includeBrandInGlobalQuery     = (req.brandId.isEmpty && req.brandName.isEmpty) || !req.multiBrand.getOrElse(false)
    val includeTagsInGlobalQuery      = req.tags.isEmpty || !req.multiTag.getOrElse(false)
    val includeNotationsInGlobalQuery = req.notations.isEmpty || !req.multiNotation.getOrElse(false)
    val includePriceInGlobalQuery     = req.priceRange.isEmpty || !req.multiPrices.getOrElse(false)

    val variationsExcludedFromGlobalQuery: List[String] =
      if (req.variations.isDefined && req.multiVariations.getOrElse(false))
        (req.variations.get.split("""\|\|\|""") map (v => {
                  v.split("""\:\:\:""").head
                })).toList
      else List()

    val featuresExcludedFromGlobalQuery: List[String] =
      if (req.features.isDefined && req.multiFeatures.getOrElse(false))
        (req.features.get.split("""\|\|\|""") map (v => {
                  v.split("""\:\:\:""").head
                })).toList
      else List()

    val categoryAggregation = termsAggregation("category").field("product.category.path").subaggs {
      termsAgg("name", "product.category.name.raw")
    } subaggs {
      termsAgg(s"name${_lang}", s"product.category.${lang}name.raw")
    }

    val brandAggregation = termsAgg("brand", "product.brand.id") subagg {
      termsAgg("name", "product.brand.name.raw")
    } subagg {
      termsAgg(s"name${_lang}", s"product.brand.${lang}name.raw")
    }

    val tagAggregation = nestedAggregation("tags", "tags") subaggs {
      termsAgg("tags", "product.tags.name.raw")
    }

    def featuresAggregation(includeFeatures: List[String] = List(), excludeFeatures: List[String] = List()) = {
      val nested = termsAgg("features_name", s"product.features.name.raw") subagg {
        termsAgg(s"features_name${_lang}", s"product.features.${lang}name.raw")
      } subagg {
        termsAgg(s"feature_values", s"product.features.value.raw")
      } subagg {
        termsAgg(s"feature_values${_lang}", s"product.features.${lang}value.raw")
      }
      nestedAggregation("features", "features") subaggs nested.includeExclude(includeFeatures, excludeFeatures)
    }

    def variationAggregation(index: String,
                             includeVariations: List[String] = List(),
                             excludeVariations: List[String] = List()) = {
      val aggregate = termsAgg(s"variation${index}_name", s"product.skus.variation$index.name.raw") subagg {
        termsAgg(s"variation${index}_name${_lang}", s"product.skus.variation$index.${lang}name.raw")
      } subagg {
        termsAgg(s"variation${index}_values", s"product.skus.variation$index.value.raw")
      } subagg {
        termsAgg(s"variation${index}_values${_lang}", s"product.skus.variation$index.${lang}value.raw")
      }
      aggregate.includeExclude(includeVariations, excludeVariations)
    }

    val notationAggregation = nestedAggregation("notations", "notations") subagg {
      termsAgg("notation", s"product.notations.notation") subagg {
        sumAgg("nbcomments", s"product.notations.nbcomments")
      }
    }

    val priceAggregation = histogramAggregation("prices")
      .field(s"product.skus.$priceWithVatField")
      .interval(req.priceInterval)
      .minDocCount(0)
    val priceMinAggregation = minAgg("price_min", s"product.skus.$priceWithVatField")
    val priceMaxAggregation = maxAgg("price_max", s"product.skus.$priceWithVatField")

    val query = buildQueryAndFilters(FilterBuilder(), storeCode, EsTypeProduct, req, fixeFilters)

    val singleQuery = Some(query aggs {
      List(
          if (includeCategoryInGlobalQuery) Some(categoryAggregation) else None,
          if (includeBrandInGlobalQuery) Some(brandAggregation) else None,
          if (includeTagsInGlobalQuery) Some(tagAggregation) else None,
          Some(featuresAggregation(excludeFeatures = variationsExcludedFromGlobalQuery)),
          Some(variationAggregation("1", excludeVariations = variationsExcludedFromGlobalQuery)),
          Some(variationAggregation("2", excludeVariations = variationsExcludedFromGlobalQuery)),
          Some(variationAggregation("3", excludeVariations = variationsExcludedFromGlobalQuery)),
          if (includeNotationsInGlobalQuery) Some(notationAggregation) else None,
          if (includePriceInGlobalQuery) Some(priceAggregation) else None,
          if (includePriceInGlobalQuery) Some(priceMinAggregation) else None,
          if (includePriceInGlobalQuery) Some(priceMaxAggregation) else None
      ).flatten
    } searchType SearchType.DFS_QUERY_THEN_FETCH)

    val categoryQuery =
      if (includeCategoryInGlobalQuery) None
      else
        Some(buildQueryAndFilters(FilterBuilder(withCategoryFilter = false),
                                  storeCode,
                                  EsTypeProduct,
                                  req,
                                  fixeFilters) aggs
              categoryAggregation searchType SearchType.DFS_QUERY_THEN_FETCH)

    val brandQuery =
      if (includeBrandInGlobalQuery) None
      else
        Some(
            buildQueryAndFilters(FilterBuilder(withBrandFilter = false), storeCode, EsTypeProduct, req, fixeFilters) aggs
              brandAggregation searchType SearchType.DFS_QUERY_THEN_FETCH)

    val tagQuery =
      if (includeTagsInGlobalQuery) None
      else
        Some(
            buildQueryAndFilters(FilterBuilder(withTagsFilter = false), storeCode, EsTypeProduct, req, fixeFilters) aggs
              tagAggregation searchType SearchType.DFS_QUERY_THEN_FETCH)

    val featuresQueries = featuresExcludedFromGlobalQuery map { v =>
      Some(
          buildQueryAndFilters(FilterBuilder(featuresExcludedFromFilter = List(v)),
                               storeCode,
                               EsTypeProduct,
                               req,
                               fixeFilters) aggs
            featuresAggregation(includeFeatures = List(v)) searchType SearchType.DFS_QUERY_THEN_FETCH)
    }

    val variationsQueries = variationsExcludedFromGlobalQuery map { v =>
      Some(
          buildQueryAndFilters(FilterBuilder(variationsExcludedFromFilter = List(v)),
                               storeCode,
                               EsTypeProduct,
                               req,
                               fixeFilters) aggs
            variationAggregation(index = "1", includeVariations = List(v)) aggs variationAggregation(index = "2",
                                                                                                     includeVariations =
                                                                                                       List(v)) aggs variationAggregation(
              index = "3",
              includeVariations = List(v)) searchType SearchType.DFS_QUERY_THEN_FETCH)
    }

    val notationQuery =
      if (includeNotationsInGlobalQuery) None
      else
        Some(buildQueryAndFilters(FilterBuilder(withNotationFilter = false),
                                  storeCode,
                                  EsTypeProduct,
                                  req,
                                  fixeFilters) aggs
              notationAggregation searchType SearchType.DFS_QUERY_THEN_FETCH)

    val priceQuery =
      if (includePriceInGlobalQuery) None
      else
        Some(
            buildQueryAndFilters(FilterBuilder(withPriceFilter = false), storeCode, EsTypeProduct, req, fixeFilters) aggs
              priceAggregation aggs priceMinAggregation aggs priceMaxAggregation searchType SearchType.DFS_QUERY_THEN_FETCH)

    val multiQueries = List(
          singleQuery,
          categoryQuery,
          brandQuery,
          tagQuery,
          notationQuery,
          priceQuery
      ) ::: featuresQueries ::: variationsQueries

    EsClient.multiSearchAgg(multiQueries.flatten)
  }

  private def buildQueryAndFilters(builder: FilterBuilder,
                                   storeCode: String,
                                   esType: String,
                                   req: FacetRequest,
                                   fixeFilters: List[Option[QueryDefinition]]): SearchDefinition = {

    //println("filterBuilder=",builder)
    val priceWithVatField = req.country match {
      case Some(countryCode) => s"$countryCode.saleEndPrice"
      case _                 => "salePrice"
    }

    val filters = esType match {
      case EsTypeProduct =>
        (
            fixeFilters
              :+ (if (builder.withCategoryFilter)
                    createOrFilterBySplitValues(req.categoryPath,
                                                v => createRegexFilter("product.category.path", Some(v)))
                  else None)
              :+ (if (builder.withCategoryFilter)
                    createOrFilterBySplitValues(req.categoryName.map(_.toLowerCase),
                                                v => createtermQuery("product.category.name", Some(v)))
                  else None)
              :+ (if (builder.withBrandFilter)
                    createOrFilterBySplitValues(req.brandId, v => createtermQuery("product.brand.id", Some(v)))
                  else None)
              :+ (if (builder.withBrandFilter)
                    createOrFilterBySplitValues(req.brandName.map(_.toLowerCase),
                                                v => createtermQuery("product.brand.name", Some(v)))
                  else None)
              :+ (if (builder.withTagsFilter)
                    createOrFilterBySplitValues(req.tags.map(_.toLowerCase),
                                                v => createNestedtermQuery("tags", "tags.name", Some(v)))
                  else None)
              :+ (if (builder.withFeaturesFilter)
                    createFeaturesFilters(req, "features", builder.featuresExcludedFromFilter)
                  else None)
              :+ (if (builder.withVariationsFilter)
                    createVariationsFilters(req, "product.skus", builder.variationsExcludedFromFilter)
                  else None)
              :+ (if (builder.withNotationFilter)
                    createOrFilterBySplitValues(req.notations,
                                                v => createNestedtermQuery("notations", "notations.notation", Some(v)))
                  else None)
              :+ (if (builder.withPriceFilter)
                    createOrFilterBySplitKeyValues(req.priceRange, (min, max) =>
                          createNumericRangeFilter(s"product.skus.$priceWithVatField", min, max))
                  else None)
        ).flatten
      case EsTypeSku =>
        (
            fixeFilters
              :+ (if (builder.withCategoryFilter)
                    createOrFilterBySplitValues(req.categoryPath,
                                                v => createRegexFilter("sku.product.category.path", Some(v)))
                  else None)
              :+ (if (builder.withCategoryFilter)
                    createOrFilterBySplitValues(req.categoryName.map(_.toLowerCase),
                                                v => createtermQuery("sku.product.category.name", Some(v)))
                  else None)
              :+ (if (builder.withBrandFilter)
                    createOrFilterBySplitValues(req.brandId, v => createtermQuery("sku.product.brand.id", Some(v)))
                  else None)
              :+ (if (builder.withBrandFilter)
                    createOrFilterBySplitValues(req.brandName.map(_.toLowerCase),
                                                v => createtermQuery("sku.product.brand.name", Some(v)))
                  else None)
              :+ (if (builder.withTagsFilter)
                    createOrFilterBySplitValues(req.tags.map(_.toLowerCase), v =>
                          createNestedtermQuery("product.tags", "product.tags.name", Some(v)))
                  else None)
              :+ (if (builder.withFeaturesFilter)
                    createFeaturesFilters(req, "product.features", builder.featuresExcludedFromFilter)
                  else None)
              :+ (if (builder.withVariationsFilter)
                    createVariationsFilters(req, "sku", builder.variationsExcludedFromFilter)
                  else
                    None) //:+ (if (builder.withNotation) createOrFilterBySplitValues (req.notations, v => createNestedtermQuery ("notations", "product.notations.notation", Some (v) ) ) else None)
              :+ (if (builder.withPriceFilter)
                    createOrFilterBySplitKeyValues(req.priceRange, (min, max) =>
                          createNumericRangeFilter(s"sku.$priceWithVatField", min, max))
                  else None)
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
    * @param req       - facet request
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
    * @param req       - facet request
    * @return
    */
  private def buildQueryPart(storeCode: String, esType: String, req: FacetRequest) = {
    req.name match {
      case Some(s) =>
        search(storeCode -> esType) query {
          matchQuery("name", s)
        }
      case None => search(storeCode -> esType)
    }
  }

  /**
    * Renvoie le filtres permettant de filtrer les produits mis en avant
    * si la requête le demande
    *
    * @param req                - facet request
    * @param propertyPathPrefix prefixe du chemin vers la propriété (ex: product ou sku.product)
    * @return
    */
  private def createFeaturedRangeFilters(req: FacetRequest, propertyPathPrefix: String): Option[QueryDefinition] = {
    if (req.featured.getOrElse(false)) {
      val today = sdf.print(Calendar.getInstance().getTimeInMillis)
      val list = List(
          createRangeFilter(s"$propertyPathPrefix.startFeatureDate", None, Some(s"$today")),
          createRangeFilter(s"$propertyPathPrefix.stopFeatureDate", Some(s"$today"), None)
      ).flatten
      Some(must(list))
    } else None
  }

  /**
    * Renvoie le filtre pour les features
    *
    * @param req - facet request
    * @return
    */
  private def createFeaturesFilters(req: FacetRequest,
                                    propertyPathPrefix: String,
                                    excludedFeatures: List[String]): Option[QueryDefinition] = {
    createAndOrFilterBySplitKeyValues(req.features, (k, v) => {
      if (excludedFeatures.contains(k)) None
      else
        Some(
            must(
                List(
                    createNestedtermQuery(propertyPathPrefix, s"$propertyPathPrefix.name.raw", Some(k)),
                    createNestedtermQuery(propertyPathPrefix, s"$propertyPathPrefix.value.raw", Some(v))
                ).flatten
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
  private def createVariationsFilters(req: FacetRequest,
                                      propertyPathPrefix: String,
                                      excludedVariations: List[String]): Option[QueryDefinition] = {
    createAndOrFilterBySplitKeyValues(req.variations, (k, v) => {
      if (excludedVariations.contains(k)) None
      else
        Some(
            should(
                must(
                    List(
                        createtermQuery(s"$propertyPathPrefix.variation1.name.raw", Some(k)),
                        createtermQuery(s"$propertyPathPrefix.variation1.value.raw", Some(v))
                    ).flatten
                ),
                must(
                    List(
                        createtermQuery(s"$propertyPathPrefix.variation2.name.raw", Some(k)),
                        createtermQuery(s"$propertyPathPrefix.variation2.value.raw", Some(v))
                    ).flatten
                ),
                must(
                    List(
                        createtermQuery(s"$propertyPathPrefix.variation3.name.raw", Some(k)),
                        createtermQuery(s"$propertyPathPrefix.variation3.value.raw", Some(v))
                    ).flatten
                )
            )
        )
    })
  }

  def getCommentNotations(storeCode: String, productId: Option[Long]): List[JValue] = {
    val filters = List(createtermQuery("productId", productId)).flatten
    val req = filterRequest(search(s"${storeCode}_comment" -> "comment"), filters) aggregations {
      termsAggregation("notations").field("notation")
    } searchType SearchType.DFS_QUERY_THEN_FETCH

    val resagg = EsClient searchAgg req

    for {
      JArray(buckets)                       <- resagg \ "notations" \ "buckets"
      JObject(bucket)                       <- buckets
      JField("key_as_string", JString(key)) <- bucket
      JField("doc_count", JInt(value))      <- bucket
    } yield JObject(List(JField("notation", JString(key)), JField("nbcomments", JInt(value))))
  }

  private case class FilterBuilder(withCategoryFilter: Boolean = true,
                                   withBrandFilter: Boolean = true,
                                   withTagsFilter: Boolean = true,
                                   withFeaturesFilter: Boolean = true,
                                   featuresExcludedFromFilter: List[String] = List(),
                                   withVariationsFilter: Boolean = true,
                                   variationsExcludedFromFilter: List[String] = List(),
                                   withNotationFilter: Boolean = true,
                                   withPriceFilter: Boolean = true)

}
