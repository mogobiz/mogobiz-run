// Statut : requete fausse, manque de donnnées valorisé (endPeriods) ==> revoir le jeu de données
// StoreController
restpath = "/api/store/{storeCode}/product/productId}/times?start={startDate}&end={endDate}"
method="GET"

/* TODO dans StoreController
 calcul des montants
 filtrer les traductions

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
