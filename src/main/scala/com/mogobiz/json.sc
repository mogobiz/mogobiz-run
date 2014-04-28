import org.json4s._
import org.json4s.JsonAST.{JObject, JString, JField}
import org.json4s.native.JsonMethods._
import org.json4s.JsonDSL._
val text =
  """
    |{
    |   "took": 6,
    |   "timed_out": false,
    |   "_shards": {
    |      "total": 5,
    |      "successful": 5,
    |      "failed": 0
    |   },
    |   "hits": {
    |      "total": 2,
    |      "max_score": 3.1560497,
    |      "hits": [
    |         {
    |            "_index": "mogobiz_v2",
    |            "_type": "product",
    |            "_id": "71",
    |            "_score": 3.1560497,
    |            "_source": {
    |               "id": "71"
    |            },
    |            "highlight": {
    |               "fr.name": [
    |                  "Pull <em>Nike</em>"
    |               ],
    |               "name": [
    |                  "Pull <em>Nike</em>"
    |               ],
    |               "en.name": [
    |                  "Pull <em>Nike</em> Anglais"
    |               ]
    |            }
    |         },
    |         {
    |            "_index": "mogobiz_v2",
    |            "_type": "brand",
    |            "_id": "28",
    |            "_score": 2.7724056,
    |            "_source": {
    |               "id": "28"
    |            },
    |            "highlight": {
    |               "fr.name": [
    |                  "<em>Nike</em>"
    |               ],
    |               "name": [
    |                  "<em>Nike</em>"
    |               ]
    |            }
    |         }
    |      ]
    |   }
    |}  """.stripMargin
val json = parse(text)
(json \ "hits" \ "hits").children.children
val formated = compact(render((json \ "hits" \ "hits").children.children))
val result = for {
  JObject(result) <- (json \ "hits" \ "hits").children.children
  JField("_type", JString(_type)) <- result
  JField("_source",JObject(_source)) <- result
  JField("highlight",JObject(highlight)) <- result
} yield (_type -> (_source ::: highlight))

compact(render(result))
result.toMap
compact(render(result))
val res = result.groupBy(_._1).map {
  case (_cat,v) => (_cat, v.map(_._2))
}
val formated_result = compact(render(res))
parse(formated_result)



