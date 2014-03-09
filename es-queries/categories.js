// STATUS : OK
// StoreController
restpath = "/api/store/{storeCode}/categories?parent=id&hidden=true&lang=fr"

//Requete ES
method = "POST"
curl = "http://localhost:9200/mogobiz/category/_search"
// si hidden=true alors supprimer la clause term
// si parent non renseigné alors supprimer la clause regexp
// si une des 2 clauses n'est pas renseigné alors supprimer aussi la clause "and"
esquery = {
    "query": {
        "filtered": {
            "filter": {
                "and":[
                    {
                    "term": {
                        "hide": false
                    }
                },{
                    "regexp":{
                        "path" : "(?)*<parent_name>*"
                    }
                }
            ]
            }
        }
    },
    "_source": {
        "include": [
            "id",
            "uuid",
            "path",
            "name",
            "description",
            "keywords",
            "<lang_prefix>.*"
        ]
    }
}
