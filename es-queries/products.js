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

query = {
    "query": {
        "filtered": {
            "query": {
                "bool": {
                    "must": [
                        {
                            "range": {
                                "startDate": {
                                    "from": "2013-12-31",
                                    "to": "2014-02-01"
                                }
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
    "sort": {
        "price": "desc"
    },
    "from": 1,
    "size": 2
}