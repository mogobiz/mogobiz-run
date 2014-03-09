//Statut OK
// StoreController
restpath = "/api/store/{storeCode}/products?lang=fr&currency={curCode}&country={countryCode}"
method = "POST" // et/ou "GET"

/* les post-params sont:
 maxItemsPerPage => valeur pour size
 pageOffset => valeur à TRANSFORMER pour from car from est l'index à partir duquelle on renvoie les données et pas la page


*/
/* TODO dans StoreController
 si historize==true alors la méthode déclenche l'appel à la méthode addToHistory avec visitorId en paramètre
 calcul des prix
 virer les traductions en trop (voir remarque ES en-dessous)
 */

method="POST"
curl = "http://localhost:9200/mogobiz/product/_search"

// remarque sur le exclude:
// 1. permet de virer toute les langues non voulu mais il faut les lister dans la query => donc connaitre la liste des lang sur le store
// 2. TODO revoir le mapping car le pattern du exclude peut virer des champs finissant par la code_lang. => utiliser un mapping trad.<code_land> :{ nested_objects }

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
}
