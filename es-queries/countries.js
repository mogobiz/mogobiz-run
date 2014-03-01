//STATUS : a tester
// StoreController
restpath = "/mogobiz/store/{storeCode}/countries?lang=fr"

/* TODO dans StoreController

 */

//Requete ES
method = "POST"
curl = "http://localhost:9200/mogobiz/country/_search"
query = {

}
//si lang=_all
query = {
    "_source": {
        "include": [
            "id",
            "code",
            "name*"
        ]
    }
}
//sinon
query = {
    "_source": {
        "include": [
            "id",
            "code",
            "name",
            "name.en"
        ]
    }
}
