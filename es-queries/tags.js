/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

//STATUS : OK
// StoreController
restpath = "/api/store/{storeCode}/tags?lang=fr";

/* TODO dans StoreController
 ne récupérer que la source
 */

//Requete ES
method = "POST";
curl = "http://localhost:9200/mogobiz/tag/_search";
//si lang=_all alors lang_prefix=* sinon =fr
esquery = {
    "_source": {
        "include": [
            "id",
            "<lang_prefix>.*"
        ]
    }
};