// Statut : OK
// StoreController
//REMARQUE : idem que products avec un query parameter featured supplémentaire
restpath = "/api/store/{storeCode}/products?lang=fr&currency={curCode}&country={countryCode}&featured=true";
method = "POST"; // et/ou "GET"



method="POST";
curl = "http://localhost:9200/mogobiz/product/_search";

esquery={
    "_source": {
        "exclude": [
            "*en",
            "*fr",
            "*es"
        ]
    },
    "query": {
        "filtered": {
            "query": {
                "match": {
                    "name": {
                        "query": "pull tshirt",
                        "operator": "and"
                    }
                }
            },
            "filter": {
                "bool": {
                    "must": [
                        {
                            "range": {
                                "startFeatureDate": {
                                    "lte": "2014-02-01"
                                }
                            }
                        },{
                            "range": {
                                "endFeatureDate": {
                                    "gte": "2014-02-01"
                                }
                            }
                        }
                        ,{
                            "term": {
                                "code": "pack_pull_tshirt"
                            }
                        },{
                            "term": {
                                "category.id": 17
                            }
                        }
                        ,{
                            "term": {
                                "xtype": "PACKAGE"
                            }
                        }
                        ,
                        {
                            "term": {
                                "path": "habillage"
                            }
                        },
                        {
                            "term": {
                                "brand.id": 28
                            }
                        },
                        {
                            "term": {
                                "tags.name": "pull"
                            }
                        },
                        {
                            "range": {
                                "price": {
                                    "gte": 500,
                                    "lte": 2000
                                }
                            }
                        },
                        {
                            "range": {
                                "imported": {
                                    "from": "2013-12-31"
                                }
                            }
                        }
                    ]
                }
            }
        }
    },
    "sort": {"name": "desc"},
    "from": 0, //pageOffset * maxItemsPerPage
    "size": 3 //maxItemsPerPage
};
