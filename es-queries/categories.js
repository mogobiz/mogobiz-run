//STATUS : waiting for data => jeux de données non pertinent
// TODO gestion du HIDE et ParentId
// StoreController
restpath = "/mogobiz/store/{storeCode}/categories?parent=id&hidden=true&lang=fr"

/* TODO dans StoreController

 */

//Requete ES
method = "POST"
curl = "http://localhost:9200/mogobiz/category/_search"
query = {

}
//si lang=_all
query = {
    "_source": {
        "include": [
            "id",
            "uuid",
            "path",
            "name*",
            "description*",
            "keywords*"
        ]
    }
}
//sinon
query = {
    "_source": {
        "include": [
            "id",
            "uuid",
            "path",
            "name*",
            "name.en",
            "description*",
            "description.en",
            "keywords*",
            "keywords.en"
        ]
    }
}
