q = {
    "notations": {
        "type": "nested",
        "properties": {
            "notation": {
                "type": "string"
            },
            "nbcomments": {
                "type": "long"
            }
        }
    }
}
//POST http://localhost:9200/mogobiz/product2/_mapping
p = {
    "product2":{
        "properties":{
            "name":{"type":"string"},
            "features":{
                "type" : "nested",
                "properties":{
                    "name":{"type":"string"},
                    "value":{"type":"string"}
                }
            },
            "notations": {
                "type": "nested",
                "properties": {
                    "notation": {"type": "string"},
                    "nbcomments": {"type": "long"}
                }
            }
        }
    }
}

//POST http://localhost:9200/mogobiz/product2/
var p1={
    "name":"canon 5D mk2",
    "features":[
        {
            "name":"type",
            "value":"fullframe"
        },
        {
            "name":"weight",
            "value":"1000"
        },
        {
            "name":"resolution",
            "value":"15 Mpx"
        }
    ],
    "notations": [
        {"notation":"5","nbcomments":10},
        {"notation":"4","nbcomments":50},
        {"notation":"3","nbcomments":80},
        {"notation":"2","nbcomments":30},
        {"notation":"1","nbcomments":0}
    ]
}



var p2={
    "name":"canon 7D",
    "features":[
        {
            "name":"type",
            "value":"APS-C"
        },
        {
            "name":"weight",
            "value":"1000"
        },
        {
            "name":"resolution",
            "value":"18 Mpx"
        }
    ],
    "notations": [
        {"notation":"5","nbcomments":10},
        {"notation":"4","nbcomments":50},
        {"notation":"3","nbcomments":80},
        {"notation":"2","nbcomments":30},
        {"notation":"1","nbcomments":15}
    ]
}

var p3={
    "name":"canon 60D",
    "features":[
        {
            "name":"type",
            "value":"APS-C"
        },
        {
            "name":"weight",
            "value":"750"
        },
        {
            "name":"resolution",
            "value":"14 Mpx"
        }
    ],
    "notations": [
        {"notation":"5","nbcomments":10},
        {"notation":"4","nbcomments":50},
        {"notation":"3","nbcomments":80},
        {"notation":"2","nbcomments":30},
        {"notation":"1","nbcomments":15}
    ]
}

var p4={
    "name":"canon 600D",
    "features":[
        {
            "name":"type",
            "value":"APS-C"
        },
        {
            "name":"weight",
            "value":"550"
        },
        {
            "name":"resolution",
            "value":"14 Mpx"
        }
    ],"notations": [
        {"notation":"5","nbcomments":10},
        {"notation":"4","nbcomments":50},
        {"notation":"3","nbcomments":80},
        {"notation":"2","nbcomments":30},
        {"notation":"1","nbcomments":15}
    ]
}

var qagg={
    "aggregations": {
    "notations": {
        "nested": {
            "path": "notations"
        },
        "aggs": {
            "notation": {
                "terms": {
                    "field": "notations.notation"
                },
                "aggs": {
                    "nbcomments": {
                        "sum": {
                            "field": "notations.nbcomments"
                        }
                    }
                }
            }
        }
    }
}
}