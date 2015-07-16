/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

// STATUS : OK
// StoreController
restpath = "/api/store/{storeCode}/brands?lang=fr&hidden=true";

/* TODO dans StoreController
ne récupérer que la _source
 */

//Requete ES
method = "POST";
curl = "http://localhost:9200/{storeCode}/brand/_search";
//si lang=_all alors lang_prefix=* sinon =fr
//si hidden=true alors supprimer la partie "query"
esquery = {
  "query": {
        "filtered": {
            "filter": {
                "term": {
                    "hide": false
                }
            }
        }
    },
    "_source": {
        "include": [
            "id",
            "<lang_prefix>.*"
        ]
    }
};


//TODO réfléchir à une gestion des trads dans ES comme suit:
brand = {
    "name":"value_by_default",
    "name.lang":[
        {"fr":"trad_fr"},{"en":"trad_en"},{"es":"trad_it"},{"it":"trad_it"}
    ]
};
// permettrait de requeter comme suit : *.lang.<lang> pour récupérer toutes les trad d'une lang de tous les champs internationalisable



//insertion d'une données brand
var data= {
    "id": 1,
    "hide": true,
    "name": "Sony",
    "website": "sony.com",
    "fr":{
        "name":"Sony",
        "website":"sony.com"
    },"en":{
        "name":"Sony",
        "website":"sony.com"
    }
};