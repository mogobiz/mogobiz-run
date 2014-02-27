// StoreController
restpath = "/mogobiz/store/{storeCode}/product/productId}/dates"
method="GET"

/* TODO dans StoreController
 calcul des montants
 filtrer les traductions

 Remarque doc YBA : quel cookie ??
 */

//Requete ES
method = "POST"
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
                        }
                ]
                }
            }
        }
    }
}
/* TODO ES : renvoyer la liste de dates


 */
