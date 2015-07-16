/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

curl -POST http://localhost:9200/mogobiz/product/_search

//recherche par filtre
q= {"query": {
    "filtered": {
        "filter": {
            "term": {
                "name": "pull"
            }
        }
    }
}
}
//recherche par post-filtre
q={"post_filter": {   "term": {     "name": "pull"   } }}}

//recherche query/match
q={"query": {
    "match": {
        "name": "pull"
    }
}}

req = {
    "query": {
        "query_string": {
            "query": "pull"
        }
    },
    "filter": {
    "bool": {
        "must": [
            {
                "term": {
                    "brand.name": "Samsung"
                }
            }
        ]
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
    "prices_histo": {
        "histogram": {
            "field": "price",
                "interval": 5000,
                "min_doc_count": 0
        }
    },
    "price_ranges": {
        "range": {
            "field": "price",
                "ranges": [
                {
                    "to": 5000
                },
                {
                    "from": 5000,
                    "to": 10000
                },
                {
                    "from": 10000
                }
            ]
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
agg ={
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


//http://localhost:8082/store/mogobiz/facets?priceInterval=5000&lang=fr&categoryName=habillement
//http://localhost:8082/store/mogobiz/facets?priceInterval=5000&lang=fr&brandName=samsung
//http://localhost:8082/store/mogobiz/facets?priceInterval=5000&lang=fr&priceMin=25000&priceMax=30000
//http://localhost:8082/store/mogobiz/facets?priceInterval=5000&lang=fr&brandName=samsung%7Cnike
//http://localhost:8082/store/mogobiz/facets?priceInterval=5000&lang=fr&features=Resulution:::Full%20HD%7CHD%20Ready

agg_notes= {
    "aggregations": {
        "note0": {
            "sum": {
                "field": "notations.0"
            }
        },
        "note1": {
            "sum": {
                "field": "notations.1"
            }
        },
        "note2": {
            "sum": {
                "field": "notations.2"
            }
        },
        "note3": {
            "sum": {
                "field": "notations.3"
            }
        },
        "note4": {
            "sum": {
                "field": "notations.4"
            }
        },
        "note5": {
            "sum": {
                "field": "notations.5"
            }
        },
        "per_notations": {
            "terms": {
                "field": "notations"
            }
        },
        "notations2_stat": {
            "stats": {
                "field": "notations2.value"
            }
        },
        "per_notations2": {
            "terms": {
                "field": "notations2.notation"
            },
            "aggs": {
                "per_notations2_value": {
                    "sum": {
                        "field": "notations2.value"
                    }
                }
            }
        }
    }
}

var query = {
    "aggregations": {
        "notations": {
            "nested": {
                "path": "notations"
            },
            "aggs": {
                "features_name": {
                    "terms": {
                        "field": "notations.notation"
                    },
                    "aggs": {
                        "features_value": {
                            "terms": {
                                "field": "notations.nbcomments"
                            }
                        }
                    }
                }
            }
        }
    }
}