/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

// STATUS : OK
// StoreController
restpath = "/api/store/{storeCode}/countries?lang=fr";

/* TODO dans StoreController
 récuperer le _source
 */

//Requete ES
method = "POST";
curl = "http://localhost:9200/mogobiz/country/_search"; //en GET rajouter ?fields=code,name,fr.*
esquery = {
    "_source": {
        "include": [
            "code",
            "name",
            "<lang_prefix>.*"
        ]
    }
};