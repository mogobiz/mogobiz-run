//STATUS : a tester => pas de jeux de données
// StoreController
restpath = "/api/store/{storeCode}/countries?lang=fr"

/* TODO dans StoreController
 récuperer le _source
 */

//Requete ES
method = "POST"
curl = "http://localhost:9200/mogobiz/country/_search"

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
//sinon lang=en
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
