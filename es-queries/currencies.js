//STATUS : OK
// StoreController
restpath = "/mogobiz/store/{storeCode}/currencies"

/* TODO dans StoreController

 */

//Requete ES
method = "POST"
curl = "http://localhost:9200/mogobiz/rate/_search"
query = {
    "_source": {
        "include": [
            "code"
        ]
    }
}
