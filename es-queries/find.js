// Statut OK
// StoreController
restpath = "/api/store/{storeCode}/products?lang=fr&currency={curCode}&country={countryCode}"
method = "POST" // et/ou "GET"

var payload = {
    "query":"pull tv"
}

/* TODO dans StoreController
 si historize==true alors la méthode déclenche l'appel à la méthode addToHistory avec visitorId en paramètre
 calcul des prix
 virer les traductions en trop (voir remarque ES en-dessous)
 */

//requete ES
method="POST"
curl = "http://localhost:9200/mogobiz/product/_search"

var esquery={
    "_source": {
    "exclude": [
        "*en",
        "*fr",
        "*es"
    ]
},
    "query": {
    "query_string": {
        "query": "pull tv"
    }
}
}