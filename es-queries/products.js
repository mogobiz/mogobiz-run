//Statut OK
// StoreController
restpath = "/api/store/{storeCode}/products?lang=fr&currency={curCode}&country={countryCode}";
method = "POST"; // et/ou "GET"

/* les post-params sont:
 maxItemsPerPage => valeur pour size
 pageOffset => valeur à TRANSFORMER pour from car from est l'index à partir duquelle on renvoie les données et pas la page


*/
/* TODO dans StoreController
 si historize==true alors la méthode déclenche l'appel à la méthode addToHistory avec visitorId en paramètre
 calcul des prix
 */

method="POST";
curl = "http://localhost:9200/mogobiz/product/_search";

// remarque sur le exclude:
// permet de virer toute les langues non voulu mais il faut les lister dans la query => donc connaitre la liste des lang sur le store

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
                        /* inutile {
                            "range": {
                                "startDate": {
                                    "from": "2013-12-31",
                                    "to": "2014-02-01"
                                }
                            }
                        }
                        ,*/{
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
                        }/* createDateMin mais pas sûr,
                        {
                            "range": {
                                "imported": {
                                    "from": "2013-12-31"
                                }
                            }
                        }*/
                    ]
                }
            }
        }
    },
    "sort": {"name": "desc"},
    "from": 0, //pageOffset * maxItemsPerPage
    "size": 3 //maxItemsPerPage
};


// update product with notation
url = "http://localhost:9200/mogobiz/product/61/_update" //POST
//70,79
q={
    "doc": {
        "notations": [
            {
                "5": 10
            },
            {
                "4": 50
            },
            {
                "3": 80
            },
            {
                "2": 30
            },
            {
                "0": 15
            }
        ]
    }
}

q={
    "doc": {
        "notations": [
            {
                "notation": "5",
                "value": "10"
            },
            {
                "4": 50
            },
            {
                "3": 80
            },
            {
                "2": 30
            },
            {
                "0": 15
            }
        ]
    }
}

q2={
    "doc":{
        "notations2": [
            {
                "notation":"5",
                "value":10
            },
            {
                "notation":"4",
                "value":50
            },
            {
                "notation":"3",
                "value":80
            },
            {
                "notation":"2",
                "value":30
            },
            {
                "notation":"1",
                "value":15
            }]
    }
}
