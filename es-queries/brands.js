//STATUS : a finir
// TODO  gestion du hide
// StoreController
restpath = "/mogobiz/store/{storeCode}/brands?lang=fr"

/* TODO dans StoreController
ne récupérer que la source
 */

//Requete ES
method = "POST"
curl = "http://localhost:9200/mogobiz/brand/_search"
//si lang=_all
query = {
    "_source": {
        "include": [
            "id",
            "name*",
            "website*"
        ]
    }
}
//sinon
query = {
    "_source": {
        "include": [
            "id",
            "name",
            "name.en",
            "website",
            "website.en"
        ]
    }
}

//TODO réfléchir à une gestion des trads dans ES comme suit:
brand = {
    "name":"value_by_default",
    "name.lang":[
        {"fr":"trad_fr"},{"en":"trad_en"},{"es":"trad_it"},{"it":"trad_it"}
    ]
}
// permettrait de requeter comme suit : *.lang.<lang> pour récupérer toutes les trad d'une lang de tous les champs internationalisable

