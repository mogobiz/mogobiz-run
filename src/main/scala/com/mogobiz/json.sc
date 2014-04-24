import org.json4s._
import org.json4s.JsonAST.{JString, JField}
import org.json4s.native.JsonMethods._
import org.json4s.JsonDSL._
val text =
  """
    |{
    |   "took": 3,
    |   "timed_out": false,
    |   "_shards": {
    |   },
    |   "hits": {
    |      "total": 5,
    |      "max_score": 1.4078506,
    |      "hits": [
    |         {
    |            "_index": "mogobiz_v2",
    |            "_type": "category",
    |            "_id": "18",
    |            "_score": 1.4078506,
    |            "_source": {
    |               "id": 18,
    |               "parentId": null,
    |               "keywords": null,
    |               "hide": false,
    |               "description": null,
    |               "name": "Hightech",
    |               "path": "hightech",
    |               "fr": {
    |                  "keywords": null,
    |                  "description": null,
    |                  "name": "Hightech"
    |               },
    |               "uuid": "ddc14325-d921-46f8-87c9-7a1b6587ba45",
    |               "imported": "2014-04-03T02:09:00Z"
    |            }
    |         },
    |         {
    |            "_index": "mogobiz_v2",
    |            "_type": "category",
    |            "_id": "19",
    |            "_score": 0.8183087,
    |            "_source": {
    |               "id": 19,
    |               "parentId": 18,
    |               "keywords": null,
    |               "hide": false,
    |               "description": null,
    |               "name": "Télévisions",
    |               "path": "hightech/televisions",
    |               "fr": {
    |                  "keywords": null,
    |                  "description": null,
    |                  "name": "Télévisions"
    |               },
    |               "uuid": "abf9103a-2bf2-4133-a4d0-94571b10dd48",
    |               "imported": "2014-04-03T02:09:00Z"
    |            }
    |         },
    |         {
    |            "_index": "mogobiz_v2",
    |            "_type": "product",
    |            "_id": "47",
    |            "_score": 0.25994354,
    |            "_source": {
    |               "startDate": "2014-01-01T00:00:00Z",
    |               "stopDate": "2014-12-31T23:59:00Z",
    |               "taxRate": {
    |                  "id": 11,
    |                  "name": "TaxRate",
    |                  "localTaxRates": [
    |                     {
    |                        "id": 10,
    |                        "rate": 19.6,
    |                        "countryCode": "FR",
    |                        "stateCode": null
    |                     }
    |                  ]
    |               },
    |               "stopFeatureDate": "2014-04-30T00:00:00Z",
    |               "stockDisplay": true,
    |               "code": "TV_SS_1",
    |               "imported": "2014-04-03T02:09:00Z",
    |               "calendarType": "NO_DATE",
    |               "nbSales": 0,
    |               "id": 47,
    |               "creationDate": "2014-04-03T02:07:15Z",
    |               "shipping": {
    |                  "amount": 0,
    |                  "free": false,
    |                  "height": 110,
    |                  "weight": 25,
    |                  "width": 120,
    |                  "fr": {
    |                     "name": null
    |                  },
    |                  "depth": 15
    |               },
    |               "sanitizedName": "tv-100-full-hd",
    |               "category": {
    |                  "id": 19,
    |                  "parentId": 18,
    |                  "keywords": null,
    |                  "hide": false,
    |                  "description": null,
    |                  "name": "Télévisions",
    |                  "path": "hightech/televisions",
    |                  "fr": {
    |                     "keywords": null,
    |                     "description": null,
    |                     "name": "Télévisions"
    |                  },
    |                  "uuid": "abf9103a-2bf2-4133-a4d0-94571b10dd48"
    |               },
    |               "price": 30000,
    |               "hide": false,
    |               "description": "Full HD 100\" Television",
    |               "xtype": "PRODUCT",
    |               "name": "TV 100\" Full HD",
    |               "brand": {
    |                  "id": 22,
    |                  "hide": false,
    |                  "website": "http://www.samsung.com/fr",
    |                  "name": "Samsung",
    |                  "fr": {
    |                     "website": "http://www.samsung.com/fr",
    |                     "name": "Samsung"
    |                  }
    |               },
    |               "fr": {
    |                  "description": "Full HD 100\" Television",
    |                  "name": "TV 100\" Full HD",
    |                  "descriptionAsText": "Full HD 100\" Television"
    |               },
    |               "startFeatureDate": "2014-04-01T00:00:00Z",
    |               "uuid": null,
    |               "descriptionAsText": "Full HD 100\" Television"
    |            }
    |         }
    |      ]
    |   }
    |}
  """.stripMargin

val json = parse(text)
(json \ "hits" \ "hits").children.children
val formated = compact(render((json \ "hits" \ "hits").children.children))
val result = for {
  JObject(result) <- (json \ "hits" \ "hits").children.children
  JField("_type", JString(_type)) <- result
  JField("_source",JObject(_source)) <- result
} yield (_type -> _source)


compact(render(result))

result.toMap

val res = result.groupBy(_._1).map {
  case (_cat,v) => (_cat, v.map(_._2))
}

val formated_result = compact(render(res))

parse(formated_result)



