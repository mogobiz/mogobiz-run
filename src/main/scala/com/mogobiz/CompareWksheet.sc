import org.json4s._
import org.json4s.JsonAST.{JObject, JString, JField}
import org.json4s.native.JsonMethods._
import org.json4s.JsonDSL._
import scala.collection.immutable


//val ids = "49,56,63"
//
//val list = ids.split(",").toList
//val _lang = "fr"
// 
//def listAllLanguages(): List[String] = {
//  "fr" :: "en" :: "es" :: "de" :: Nil
//}
//def getLangFieldsWithPrefix(preField: String, field: String): String = {
//  val langs = listAllLanguages()
//  val langsTokens = langs.flatMap {
//    l => preField + l + "." + field :: Nil
//  }
//  langsTokens.mkString("\"", "\", \"", "\"")
//}
//def getIncludedFields(field: String, lang: String): String = {
//  getIncludedFieldWithPrefix("", field, lang)
//}
//def getIncludedFieldWithPrefix(preField: String, field: String, lang: String): String = {
//  {
//    if ("_all".equals(lang)) {
//      getLangFieldsWithPrefix(preField, field)
//    } else {
//      "\"" + preField + lang + "." + field + "\""
//    }
//  }
//}
//getIncludedFields("name", _lang)
//getIncludedFieldWithPrefix("features.", "name", _lang)
//def getFetchConfig(id: String, lang: String): String = {
//  val nameFields = getIncludedFields("name", lang)
//  val featuresNameField = getIncludedFieldWithPrefix("features.", "name", lang)
//  val featuresValueField = getIncludedFieldWithPrefix("features.", "value", lang)
//  s"""
//  {
//    "_type": "product",
//    "_id": "$id",
//    "_source": {
//      "include": [
//        "id",
//        "name",
//        "features.name",
//        "features.value",
//        $nameFields,
//        $featuresNameField,
//        $featuresValueField,
//        "category.path"
//      ]
//    }
//  }""".stripMargin
//}
//val fetchConfigsList = list.map(id => getFetchConfig(id, _lang))
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//val fetchConfigs = fetchConfigsList.filter {
//  str => !str.isEmpty
//}.mkString("[", ",", "]")
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//val multipleGetQueryTemplate = s"""
//{
//  "docs": $fetchConfigs
//}""".stripMargin
//
//
//
val response = s"""
    |{
   "docs": [
      {
         "_index": "mogobiz_v1",
         "_type": "product",
         "_id": "49",
         "_version": 1,
         "found": true,
         "_source": {
            "id": 49,
            "name": "TV 100 Full HD",
            "features": [
                {
                   "fr": {
                     "name": "Made in",
                     "value": "China"
                  }
               },
               {
                  "fr": {
                     "name": "Size",
                     "value": "100"
                  }
               },
               {
                  "fr": {
                     "name": "Resolution",
                     "value": "HD Ready"
                  }
               }
            ]
         }
      },
      {
         "_index": "mogobiz_v1",
         "_type": "product",
         "_id": "56",
         "_version": 1,
         "found": true,
         "_source": {
            "id": 56,
            "features": [
                {
                  "fr": {
                     "name": "Made in",
                     "value": "France"
                  }
               },
               {
                  "fr": {
                     "name": "Resolution",
                     "value": "HD Ready"
                  }
               }
            ]
         }
      },
      {
         "_index": "mogobiz_v1",
         "_type": "product",
         "_id": "75",
         "_version": 1,
         "found": true,
         "_source": {
            "id": 75,
            "features": [
                {
                  "fr": {
                     "name": "Made in",
                     "value": "France"
                  }
               },
               {
                  "fr": {
                     "name": "Resolution",
                     "value": "HD Ready"
                  }
               }
            ]
         }
      }
   ]
} """.stripMargin
val json = parse(response)
val docsArray = (json \ "docs" \ "_source" \ "features")

val formated = compact(render(docsArray.children.children))
docsArray.children
val features :  List[(String)] = for {
  //, String)] = for {
  JObject(result) <- (json \ "docs" \ "_source" \ "features" \ "fr" )
  //JField("value",JString(value)) <- result
  JField("name",JString(name)) <- result
} yield (name)//-> value)
val list : List[String] = features.toSet.toList
//  val result :  List[(String, List[JValue])] = for {
//    JObject(result) <- (json \\ "docs" \\ "_source")
//    JString(id) <- result \ "id"
//    JArray(features) <- (json \\ "docs" \\ "_source" \\ "features" )
//
//  } yield (id -> features)
//val result : List[(BigInt,String)] = for {
//  JObject(result) <- docsArray
//  JField("id",JInt(id)) <- result
//  JField("name",JString(name)) <- result
//} yield (id -> name)
  //
  //compact(render(result))
  //result.toMap
  //compact(render(result))
//  val res = result.groupBy(_._1).map {
//    case (_feature,v) => {
//      val valueList = v.map(_._2)
//      val diff = if(valueList.toSet.size == 1) "0" else "1"
//
//      (_feature, valueList.::(diff))
//    }
//  }
//}

json.values
val rawIds : List[(BigInt)] = for {
  JObject(result) <- (json \ "docs" \  "_source")
    JField("id",JInt(id)) <- result
} yield {
  (id)
}
val rawFeatures : List[(BigInt,List[JValue])] = for {
  JObject(result) <- (json \ "docs" \ "_source")
  JField("id",JInt(id)) <- result
  JField("features",JArray(features)) <- result
} yield (id -> features)
//type mismatch;
//found   : List[List[(String, org.json4s.JsonAST.JValue)]]
//required: List[(String, String)]
//JObject(result) <- v

val listofF1 = rawFeatures.toMap.get(49).get
val listofF2 = rawFeatures.toMap.get(56).get
//class StringTupleSerializer extends CustomSerializer[(String, String)](format => ( {
//  case JObject(List(JField(k, JString(v)))) => (k, v)
//}, {
//  case (s: String, t: String) => (s -> t)
//}))
//
//implicit val formats = DefaultFormats + new StringTupleSerializer
//case class FooMap(foo: String, baz: List[Map[String, String]]);
//val f = (json \ "docs" \ "_source" \ "features");
//  f.extract[FooMap]
val rawResult1 = for {
  //JObject(result) <- (json \ "docs" \ "_source"\"features")
  JObject(result) <- listofF1
  (lang,JObject(f)) <- result
  ("name",JString(name)) <- f
  ("value",JString(value)) <- f
} yield (name,value)
val rawResult2 = for {
//JObject(result) <- (json \ "docs" \ "_source"\"features")
  JObject(result) <- listofF2
  (lang,JObject(f)) <- result
  ("name",JString(name)) <- f
  ("value",JString(value)) <- f
} yield (name,value)
val r1 = rawResult1.toMap
val r2 = rawResult2.toMap
def zipper(map1: Map[String, String], map2: Map[String, String]) = {
  for(key <- map1.keys ++ map2.keys)
  yield (key -> (map1.getOrElse(key, "-")+","+map2.getOrElse(key, "-")))
}

val r3 = zipper(r1,r2).toMap
zipper(r3,r2).toMap
var result =  immutable.Map.empty[String, String]
for (id <- rawIds) {
  val listofF = rawFeatures.toMap.get(id).get
  val rawResult = for {
    JObject(result) <- listofF
    (lang,JObject(f)) <- result
    ("name",JString(name)) <- f
    ("value",JString(value)) <- f
  } yield (name,value)
  val mapResult = rawResult.toMap
  result = zipper(result,mapResult).toMap
}
result
result.map {
  case (k,v) => {
    val list = v.split(",").toList.tail
    val diff = if(list.toSet.size == 1) "0" else "1"
    (k,(list.::(diff)).reverse)
  }
}







































/***************************************************************************/
//val result = rawResult.groupBy(_._1).map {
//  case (_feature, v) => {
//    val valueList : List[String] = v.map(_._2)
//    val diff : String = if (valueList.toSet.size == 1) "0" else "1"
//    (_feature, valueList.::(diff))
//  }
//}
//val formated_result = compact(render(res))
//parse(formated_result)
("abc", "ABC", "12_").zipped foreach { (x, y, z) =>
  println(x.toString + y + z)
}


