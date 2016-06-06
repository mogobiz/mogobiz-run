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
import com.sksamuel.elastic4s.{ TermAggregationDefinition, FilterDefinition, SearchDefinition, SearchType }
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
      createOrFilterBySplitValues(req.rootCategoryPath, v => createRegexFilter(s"sku.product.category.path", Some(v))),
      createOrFilterBySplitValues(retrievePromotionsIds(storeCode, req), v => createTermFilter("sku.coupons.id", Some(v))),
      createFeaturedRangeFilters(req, "sku.product"),
      createAndOrFilterBySplitKeyValues(req.property, (k, v) => createTermFilter(k, Some(v)))
    )

    val definedCategory = (req.categoryPath.isEmpty && req.categoryName.isEmpty) || !req.multiCategory.getOrElse(false)
    val definedBrand = (req.brandId.isEmpty && req.brandName.isEmpty) || !req.multiBrand.getOrElse(false)
    val definedTags = req.tags.isEmpty || !req.multiTag.getOrElse(false)
    val withVariations = req.variations.isEmpty || !req.multiVariations.getOrElse(false)
    val withFeature = req.features.isEmpty || !req.multiFeatures.getOrElse(false)
    val definedNotations = req.notations.isEmpty || !req.multiNotation.getOrElse(false)
    val definedPrices = req.priceRange.isEmpty || !req.multiPrices.getOrElse(false)

    val definedVariations: List[String] = if (req.variations.isDefined && req.multiVariations.getOrElse(false))
      (req.variations.get.split("""\|\|\|""") map (v => { v.split("""\:\:\:""").head })).toList else List()

    val definedFeatures: List[String] = if (req.features.isDefined && req.multiFeatures.getOrElse(false))
      (req.features.get.split("""\|\|\|""") map (v => { v.split("""\:\:\:""").head })).toList else List()

    val categoryAggregation = aggregation terms "category" field "sku.product.category.path" aggs {
      agg terms "name" field "sku.product.category.name.raw"
    } aggs {
      agg terms s"name${_lang}" field s"sku.product.category.${lang}name.raw"
    }

    val brandAggregation = aggregation terms "brand" field "sku.product.brand.id" aggs {
      agg terms "name" field "sku.product.brand.name.raw"
    } aggs {
      agg terms s"name${_lang}" field s"sku.product.brand.${lang}name.raw"
    }

    val tagAggregation = aggregation nested "tags" path "sku.product.tags" aggs {
      aggregation terms "tags" field "sku.product.tags.name.raw"
    }

    def featuresAggregation(includeFeatures: List[String] = List(), excludeFeatures: List[String] = List()) = {
      val nested: TermAggregationDefinition = aggregation terms "features_name" field s"sku.product.features.name.raw" aggs {
        aggregation terms s"features_name${_lang}" field s"sku.product.features.${lang}name.raw"
      } aggs {
        aggregation terms s"feature_values" field s"sku.product.features.value.raw"
      } aggs {
        aggregation terms s"feature_values${_lang}" field s"sku.product.features.${lang}value.raw"
      }
      if (includeFeatures.nonEmpty) nested.builder.include(includeFeatures.toArray)
      if (excludeFeatures.nonEmpty) nested.builder.exclude(excludeFeatures.toArray)
      aggregation nested "features" path "sku.product.features" aggs nested
    }

    def variationAggregation(index: String, includeVariations: List[String] = List(), excludeVariations: List[String] = List()) = {
      val aggregate = aggregation terms s"variation${index}_name" field s"sku.variation$index.name.raw" aggs {
        aggregation terms s"variation${index}_name${_lang}" field s"sku.variation$index.${lang}name.raw"
      } aggs {
        aggregation terms s"variation${index}_values" field s"sku.variation$index.value.raw"
      } aggs {
        aggregation terms s"variation${index}_values${_lang}" field s"sku.variation$index.${lang}value.raw"
      }
      if (includeVariations.nonEmpty) aggregate.builder.include(includeVariations.toArray)
      if (excludeVariations.nonEmpty) aggregate.builder.exclude(excludeVariations.toArray)
      aggregate
    }

    val notationAggregation = aggregation nested "notations" path "notations" aggs {
      aggregation terms "notation" field s"product.notations.notation" aggs {
        aggregation sum "nbcomments" field s"product.notations.nbcomments"
      }
    }

    val priceAggregation = aggregation histogram "prices" field s"sku.$priceWithVatField" interval req.priceInterval minDocCount 0
    val priceMinAggregation = aggregation min "price_min" field s"sku.$priceWithVatField"
    val priceMaxAggregation = aggregation max "price_max" field s"sku.$priceWithVatField"

    val query = buildQueryAndFilters(FilterBuilder(), storeCode, ES_TYPE_SKU, req, fixeFilters)

    val singleQuery = Some(query aggs {
      List(
        if (definedCategory) Some(categoryAggregation) else None,
        if (definedBrand) Some(brandAggregation) else None,
        if (definedTags) Some(tagAggregation) else None,
        Some(featuresAggregation(excludeFeatures = definedVariations)),
        Some(variationAggregation("1", excludeVariations = definedVariations)),
        Some(variationAggregation("2", excludeVariations = definedVariations)),
        Some(variationAggregation("3", excludeVariations = definedVariations)),
        if (definedPrices) Some(priceAggregation) else None,
        if (definedPrices) Some(priceMinAggregation) else None,
        if (definedPrices) Some(priceMaxAggregation) else None
      ).flatten: _*
    } searchType SearchType.Count)

    val categoryQuery = if (definedCategory) None else
      Some(buildQueryAndFilters(FilterBuilder(withCategory = false), storeCode, ES_TYPE_SKU, req, fixeFilters) aggs
        categoryAggregation searchType SearchType.Count)

    val brandQuery = if (definedBrand) None else
      Some(buildQueryAndFilters(FilterBuilder(withBrand = false), storeCode, ES_TYPE_SKU, req, fixeFilters) aggs
        brandAggregation searchType SearchType.Count)

    val tagQuery = if (definedTags) None else
      Some(buildQueryAndFilters(FilterBuilder(withTags = false), storeCode, ES_TYPE_SKU, req, fixeFilters) aggs
        tagAggregation searchType SearchType.Count)

    val featuresQueries = definedFeatures map { v =>
      Some(buildQueryAndFilters(FilterBuilder(excludedFeatures = List(v)), storeCode, ES_TYPE_SKU, req, fixeFilters) aggs
        featuresAggregation(includeFeatures = List(v)) searchType SearchType.Count)
    }

    val variationsQueries = definedVariations map { v =>
      Some(buildQueryAndFilters(FilterBuilder(excludedVariations = List(v)), storeCode, ES_TYPE_SKU, req, fixeFilters) aggs
        variationAggregation(index = "1", includeVariations = List(v)) aggs variationAggregation(index = "2", includeVariations = List(v)) aggs variationAggregation(index = "3", includeVariations = List(v)) searchType SearchType.Count)
    }

    val priceQuery = if (definedPrices) None else Some(buildQueryAndFilters(FilterBuilder(withPrice = false), storeCode, ES_TYPE_SKU, req, fixeFilters) aggs
      priceAggregation aggs priceMinAggregation aggs priceMaxAggregation searchType SearchType.Count)

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
      createOrFilterBySplitValues(req.rootCategoryPath, v => createRegexFilter(s"product.category.path", Some(v))),
      createOrFilterBySplitValues(retrievePromotionsIds(storeCode, req), v => createTermFilter("product.coupons.id", Some(v))),
      createFeaturedRangeFilters(req, "product"),
      createAndOrFilterBySplitKeyValues(req.property, (k, v) => createTermFilter(k, Some(v)))
    )

    val definedCategory = (req.categoryPath.isEmpty && req.categoryName.isEmpty) || !req.multiCategory.getOrElse(false)
    val definedBrand = (req.brandId.isEmpty && req.brandName.isEmpty) || !req.multiBrand.getOrElse(false)
    val definedTags = req.tags.isEmpty || !req.multiTag.getOrElse(false)
    val withVariations = req.variations.isEmpty || !req.multiVariations.getOrElse(false)
    val withFeature = req.features.isEmpty || !req.multiFeatures.getOrElse(false)
    val definedNotations = req.notations.isEmpty || !req.multiNotation.getOrElse(false)
    val definedPrices = req.priceRange.isEmpty || !req.multiPrices.getOrElse(false)

    val definedVariations: List[String] = if (req.variations.isDefined && req.multiVariations.getOrElse(false))
      (req.variations.get.split("""\|\|\|""") map (v => { v.split("""\:\:\:""").head })).toList else List()

    val definedFeatures: List[String] = if (req.features.isDefined && req.multiFeatures.getOrElse(false))
      (req.features.get.split("""\|\|\|""") map (v => { v.split("""\:\:\:""").head })).toList else List()

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

    def featuresAggregation(includeFeatures: List[String] = List(), excludeFeatures: List[String] = List()) = {
      val nested: TermAggregationDefinition = aggregation terms "features_name" field s"product.features.name.raw" aggs {
        aggregation terms s"features_name${_lang}" field s"product.features.${lang}name.raw"
      } aggs {
        aggregation terms s"feature_values" field s"product.features.value.raw"
      } aggs {
        aggregation terms s"feature_values${_lang}" field s"product.features.${lang}value.raw"
      }
      if (includeFeatures.nonEmpty) nested.builder.include(includeFeatures.toArray)
      if (excludeFeatures.nonEmpty) nested.builder.exclude(excludeFeatures.toArray)
      aggregation nested "features" path "features" aggs nested
    }

    def variationAggregation(index: String, includeVariations: List[String] = List(), excludeVariations: List[String] = List()) = {
      val aggregate = aggregation terms s"variation${index}_name" field s"product.skus.variation$index.name.raw" aggs {
        aggregation terms s"variation${index}_name${_lang}" field s"product.skus.variation$index.${lang}name.raw"
      } aggs {
        aggregation terms s"variation${index}_values" field s"product.skus.variation$index.value.raw"
      } aggs {
        aggregation terms s"variation${index}_values${_lang}" field s"product.skus.variation$index.${lang}value.raw"
      }
      if (includeVariations.nonEmpty) aggregate.builder.include(includeVariations.toArray)
      if (excludeVariations.nonEmpty) aggregate.builder.exclude(excludeVariations.toArray)
      aggregate
    }

    val notationAggregation = aggregation nested "notations" path "notations" aggs {
      aggregation terms "notation" field s"product.notations.notation" aggs {
        aggregation sum "nbcomments" field s"product.notations.nbcomments"
      }
    }

    val priceAggregation = aggregation histogram "prices" field s"product.skus.$priceWithVatField" interval req.priceInterval minDocCount 0
    val priceMinAggregation = aggregation min "price_min" field s"product.skus.$priceWithVatField"
    val priceMaxAggregation = aggregation max "price_max" field s"product.skus.$priceWithVatField"

    val query = buildQueryAndFilters(FilterBuilder(), storeCode, ES_TYPE_PRODUCT, req, fixeFilters)

    val singleQuery = Some(query aggs {
      List(
        if (definedCategory) Some(categoryAggregation) else None,
        if (definedBrand) Some(brandAggregation) else None,
        if (definedTags) Some(tagAggregation) else None,
        Some(featuresAggregation(excludeFeatures = definedVariations)),
        Some(variationAggregation("1", excludeVariations = definedVariations)),
        Some(variationAggregation("2", excludeVariations = definedVariations)),
        Some(variationAggregation("3", excludeVariations = definedVariations)),
        if (definedNotations) Some(notationAggregation) else None,
        if (definedPrices) Some(priceAggregation) else None,
        if (definedPrices) Some(priceMinAggregation) else None,
        if (definedPrices) Some(priceMaxAggregation) else None
      ).flatten: _*
    } searchType SearchType.Count)

    val categoryQuery = if (definedCategory) None else
      Some(buildQueryAndFilters(FilterBuilder(withCategory = false), storeCode, ES_TYPE_PRODUCT, req, fixeFilters) aggs
        categoryAggregation searchType SearchType.Count)

    val brandQuery = if (definedBrand) None else
      Some(buildQueryAndFilters(FilterBuilder(withBrand = false), storeCode, ES_TYPE_PRODUCT, req, fixeFilters) aggs
        brandAggregation searchType SearchType.Count)

    val tagQuery = if (definedTags) None else
      Some(buildQueryAndFilters(FilterBuilder(withTags = false), storeCode, ES_TYPE_PRODUCT, req, fixeFilters) aggs
        tagAggregation searchType SearchType.Count)

    val featuresQueries = definedFeatures map { v =>
      Some(buildQueryAndFilters(FilterBuilder(excludedFeatures = List(v)), storeCode, ES_TYPE_PRODUCT, req, fixeFilters) aggs
        featuresAggregation(includeFeatures = List(v)) searchType SearchType.Count)
    }

    val variationsQueries = definedVariations map { v =>
      Some(buildQueryAndFilters(FilterBuilder(excludedVariations = List(v)), storeCode, ES_TYPE_PRODUCT, req, fixeFilters) aggs
        variationAggregation(index = "1", includeVariations = List(v)) aggs variationAggregation(index = "2", includeVariations = List(v)) aggs variationAggregation(index = "3", includeVariations = List(v)) searchType SearchType.Count)
    }

    val notationQuery = if (definedNotations) None else Some(buildQueryAndFilters(FilterBuilder(withNotation = false), storeCode, ES_TYPE_PRODUCT, req, fixeFilters) aggs
      notationAggregation searchType SearchType.Count)

    val priceQuery = if (definedPrices) None else Some(buildQueryAndFilters(FilterBuilder(withPrice = false), storeCode, ES_TYPE_PRODUCT, req, fixeFilters) aggs
      priceAggregation aggs priceMinAggregation aggs priceMaxAggregation searchType SearchType.Count)

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

  private def buildFilter: FacetRequest => FilterBuilder = req => FilterBuilder(
    withCategory = !req.multiCategory.getOrElse(false),
    withBrand = !req.multiBrand.getOrElse(false),
    withTags = !req.multiTag.getOrElse(false),
    withFeatures = !req.multiFeatures.getOrElse(false),
    withVariations = !req.multiVariations.getOrElse(false),
    withNotation = !req.multiNotation.getOrElse(false),
    withPrice = !req.multiPrices.getOrElse(false)
  )

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
          :+ (if (builder.withFeatures) createFeaturesFilters(req, "features", builder.excludedFeatures) else None)
          :+ (if (builder.withVariations) createVariationsFilters(req, "product.skus", builder.excludedVariations) else None)
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
          :+ (if (builder.withFeatures) createFeaturesFilters(req, "product.features", builder.excludedVariations) else None)
          :+ (if (builder.withVariations) createVariationsFilters(req, "sku", builder.excludedVariations) else None) //:+ (if (builder.withNotation) createOrFilterBySplitValues (req.notations, v => createNestedTermFilter ("notations", "product.notations.notation", Some (v) ) ) else None)
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
  private def createFeaturesFilters(req: FacetRequest, propertyPathPrefix: String, excludedFeatures: List[String]): Option[FilterDefinition] = {
    createAndOrFilterBySplitKeyValues(req.features, (k, v) => {
      if (excludedFeatures.contains(k)) None else Some(
        must(
          List(
            createNestedTermFilter(propertyPathPrefix, s"$propertyPathPrefix.name.raw", Some(k)),
            createNestedTermFilter(propertyPathPrefix, s"$propertyPathPrefix.value.raw", Some(v))
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
  private def createVariationsFilters(req: FacetRequest, propertyPathPrefix: String, excludedVariations: List[String]): Option[FilterDefinition] = {
    createAndOrFilterBySplitKeyValues(req.variations, (k, v) => {
      if (excludedVariations.contains(k)) None else Some(
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
    excludedFeatures: List[String] = List(),
    withVariations: Boolean = true,
    excludedVariations: List[String] = List(),
    withNotation: Boolean = true,
    withPrice: Boolean = true)
}
