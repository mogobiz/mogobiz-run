package com.mogobiz.handlers


import com.mogobiz.es.{EsClient, _}
import com.sksamuel.elastic4s.ElasticDsl.{search => esearch4s, _}
import com.sksamuel.elastic4s.{SearchType, FilterDefinition}
import com.mogobiz.model.FacetRequest
import org.json4s._

class FacetHandler {

  def getProductCriteria(storeCode: String, req: FacetRequest) : JValue = {
    val query = req.name match {
      case Some(s) =>
        esearch4s in storeCode -> "product" query {
          matchQuery("name", s)
        }
      case None => esearch4s in storeCode -> "product"
    }

    val lang = if(req.lang=="_all") "" else s"${req.lang}."

    val filters:List[FilterDefinition] = (List(
      createTermFilter("brand.name", req.brandName),
      createTermFilter("category.name", req.categoryName),
      createNumericRangeFilter("price", req.priceMin, req.priceMax)
    )::: List(/*feature*/
      req.features match {
        case Some(x:String) =>
          val features:List[FilterDefinition] = (for(feature <- x.split("""\|\|\|""")) yield {
            val kv = feature.split("""\:\:\:""")
            if(kv.size == 2)
              Some(
                must(
                  List(
                    createTermFilter(s"features.${lang}name.raw", Some(kv(0))),
                    createTermFilter(s"features.${lang}value.raw", Some(kv(1)))
                  ).flatten:_*
                )
              )
            else
              None
          }).toList.flatten
          if(features.size > 1) Some(and(features:_*)) else if(features.size == 1) Some(features(0)) else None
        case _ => None
      }
    )).flatten

    val esq = (filterRequest(query, filters) aggs {
      aggregation terms "category" field s"category.${lang}name.raw"
    } aggs {
      aggregation terms "brand" field s"brand.${lang}name.raw"
    } aggs {
      aggregation terms "features" field s"features.${lang}name.raw" aggs {
        aggregation terms "feature_values" field s"features.${lang}value.raw"
      }
    } aggs {
      aggregation histogram "prices" field "price" interval req.priceInterval minDocCount 0
    } aggs {
      aggregation min "price_min" field "price"
    } aggs {
      aggregation max "price_max" field "price"
    }
      searchType SearchType.Count)

    EsClient searchAgg esq
  }
}
