//STATUS : a finir
// TODO  gestion du hide
// StoreController
restpath = "/api/store/{storeCode}/tags?lang=fr"

/* TODO dans StoreController
 ne récupérer que la source
 */

//Requete ES
method = "POST"
curl = "http://localhost:9200/mogobiz/tag/_search"
//si lang=_all
query = {
    "_source": {
        "include": [
            "id",
            "name*"
        ]
    }
}
//sinon
query = {
    "_source": {
        "include": [
            "id",
            "name",
            "name.en"
        ]
    }
}