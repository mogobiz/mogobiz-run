curl -POST http://localhost:9200/mogobiz/product/_search

//recherche par filtre
"query": {
    "filtered": {
        "filter": {
            "term":{
                "name":"pull"
            }
        }
    }
},

//recherche par post-filtre
"post_filter": {   "term": {     "name": "pull"   } }}

//recherche query/match
"query": {
    "match": {
        "name": "pull"
    }
},

{
    "query": {
        "query_string": {
            "query": "pull"
        }
    },
    "aggregations": {
    "price_stats":{
      "stats":{ "field" : "price"}
    },
    "price_min": {
        "min": {
            "field": "price"
        }
    },
    "price_max": {
        "max": {
            "field": "price"
        }
    },
    "per_category": {
        "terms": {
            "field": "category.fr.name.raw" //"category.path"
        }
    },
    "per_brand": {
        "terms": {
            "field": "brand.fr.name.raw"
        }
    },
    "per_feature": {
        "terms": {
            "field": "features.fr.name.raw"
        },
        "aggregations": {
            "per_feature_value": {
                "terms": {
                    "field": "features.fr.value.raw"
                }
            }
        }
    }
}
}


//requete d'aggregation pour critere de notation

curl -POST http://localhost:9200/mogobiz_comment/_search
{
    "aggregations": {
    "per_notation": {
        "terms": {
            "field": "notation",
                "order": {
                "_term": "desc"
            }
        }
    }
}
}