import org.json4s._
import org.json4s.JsonAST.{JObject, JString, JField}
import org.json4s.native.JsonMethods._
import org.json4s.JsonDSL._


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
                  "name": "Made in",
                  "value": "China"
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
                     "value": "Full HD"
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
                  "name": "Made in",
                  "value": "France"
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
      }
   ]
} """.stripMargin
val json = parse(response)
val docsArray = (json \ "docs")
val formated = compact(render(docsArray.children.children))
docsArray.children
val result :  List[(String, String)] = for {
  JObject(result) <- docsArray
  JField("value",JString(value)) <- result
  JField("name",JString(name)) <- result
} yield (name -> value)
//
//compact(render(result))
//result.toMap
//compact(render(result))
val res = result.groupBy(_._1).map {
  case (_feature,v) => {
    val valueList = v.map(_._2)
    val diff = if(valueList.toSet.size == 1) "0" else "1"
    (_feature, valueList, diff)
  }
}
//val formated_result = compact(render(res))
//parse(formated_result)

