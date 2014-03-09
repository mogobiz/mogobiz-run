// STATUS : OK
// StoreController
restpath = "/api/store/{storeCode}/currencies?lang=fr"

//Requete ES
method = "POST"
curl = "http://localhost:9200/mogobiz/rate/_search"
esquery = {
    "_source": {
        "include": [
            "id",
            "currencyFractionDigits",
            "rate",
            "code",
            "name",
            "<lang_prefix>.*"
        ]
    }
}