import org.json4s._










import org.json4s.JArray
import org.json4s.JInt
import org.json4s.JsonAST._
import org.json4s.JsonAST.JField
import org.json4s.JsonAST.JNothing
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JString
import org.json4s.JValue
import org.json4s.native.JsonMethods._
import org.json4s.JsonDSL._
import scala.collection.immutable
import scala.collection.JavaConverters._


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
//    if ("_all".equals(lang)) {JField("id",JInt(id))
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
            "fr" : {
               "name": "TV 100 FRANCAIS"
            }
            "features": [
                {
                                     "name": "Made in",
                     "value": "China",
                   "fr": {
                     "name": "Fabriqué en ",
                     "value": "Chine"
                  }
               },
               {
                                    "name": "Size",
                     "value": "100",
                  "fr": {
                     "name": "taille",
                     "value": "100"
                  }
               },
               {
                                    "name": "Resolution",
                     "value": "HD Ready",
                  "fr": {
                     "name": "Résolution",
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
            "name": "TV 100 HD Ready",
            "fr" : {
               "name": "TV 100 HD FRANCAIS"
            }
            "features": [
                {
                                     "name": "Made in",
                     "value": "France",
                  "fr": {
                     "name": "Fabriqué en ",
                     "value": "France"
                  }
               },
               {
                                    "name": "Resolution",
                     "value": "HD Ready",
                  "fr": {
                     "name": "Résolution",
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
            "name": "TV 100 HD Ready",
            "fr" : {
               "name": "TV 100 HD FRANCAIS"
            }
            "features": [
                {
                                     "name": "Made in",
                     "value": "France",
                  "fr": {
                     "name": "Fabriqué en ",
                     "value": "France"
                  }
               },
               {
                                    "name": "Resolution",
                     "value": "HD Ready",
                  "fr": {
                     "name": "Résolution",
                     "value": "HD Ready",

                  }
               }
            ]
         }
      }
   ]
} """.stripMargin
implicit val formats = DefaultFormats
val json = parse(response)
val docsArray = (json \ "docs")
val _source = (json \ "docs" \ "_source")
/*
 */
val lang = "fr"
val rawIds: List[BigInt] = for {
  JObject(result) <- (docsArray \ "_source")
  JField("id", JInt(id)) <- result
} yield (id)
val rawFeatures: List[(BigInt, List[JValue])] = {
  for {
    JObject(result) <- (docsArray \ "_source")
    JField("id", JInt(id)) <- result
    JField("features", JArray(features)) <- result
  } yield (id -> features)
}
var result = immutable.Map.empty[String, String]

def zipper(map1: immutable.Map[String, String], map2: immutable.Map[String, String])= {
  for (key <- map1.keys ++ map2.keys)
  yield (key -> (map1.getOrElse(key, "-") + "," + map2.getOrElse(key, "-")))
}
def translate(lang: String, jv :JValue): JValue = {
  implicit val formats = DefaultFormats
  val map = collection.mutable.Map(jv.extract[Map[String, JValue]].toSeq: _*)
  println(map)
  if (!lang.equals("_all")) {
    val _jvLang: JValue  = map.getOrElse(lang,JNothing)
    if (_jvLang != JNothing){
        val _langMap = _jvLang.extract[Map[String, JValue]]
        _langMap foreach {
          case (k, v) => map(k) = v
        }
        map.remove(lang)
    }
    map foreach {
      case (k: String, v: JObject) => map(k) = translate(lang, v)
      case (k: String, v: JArray) => map(k) = v.children.toList map {
        jv => translate(lang,jv)
      }
      case (k: String, _) =>
    }
  }
  print("RESULTAT "+ map)
  JsonDSL.map2jvalue(map.toMap)
}
for (id <- rawIds) {
  val _featuresForId = rawFeatures.toMap.getOrElse(id, List.empty)
  val _translatedFeaturesForId = _featuresForId.map(f => {
    println(f)
    translate(lang,f)
  })
  val _resultForId :List[(String,String)] = for {
    _jfeature <- _translatedFeaturesForId
    JString(name) <- _jfeature \ "name"
    JString(value) <- _jfeature \ "value"
  } yield (name, value)
  //println(_resultForId)
  //println(_resultForId.toMap)
  result = zipper(result, _resultForId.toMap).toMap
}
println(result)


val newsource = _source.children.toList map {
  jv => {
    //println(jv)
    translate(lang, jv)
  }
}
compact(render(newsource))




































































