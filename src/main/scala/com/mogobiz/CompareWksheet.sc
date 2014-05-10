/**
 * Created by dach on 10/05/2014.
 */


val ids = "49,56,63"

val list = ids.split(",").toList
val _lang = "_all"
 
def listAllLanguages(): List[String] = {
  "fr" :: "en" :: "es" :: "de" :: Nil
}
def getLangFieldsWithPrefix(preField: String, field: String): String = {
  val langs = listAllLanguages()
  val langsTokens = langs.flatMap {
    l => preField + l + "." + field :: Nil
  }
  langsTokens.mkString("\"", "\", \"", "\"")
}

def getIncludedFields(field: String, lang: String) : String = {
  getIncludedFieldWithPrefix("",field,lang)
}  
  
def getIncludedFieldWithPrefix(preField:  String, field: String, lang: String) : String = {
  {
    if ("_all".equals(lang)) {
      getLangFieldsWithPrefix(preField, field)
    } else {
      "\"" + preField + lang + "." + field + "\""
    }
  }
}

getIncludedFields("name", _lang)



getIncludedFieldWithPrefix("features.","name", _lang)



def getFetchConfig(id: String, lang: String) : String = {
  val nameFields = getIncludedFields("name", lang)
  val featuresNameField = getIncludedFieldWithPrefix("features.", "name",lang)
  val featuresValueField = getIncludedFieldWithPrefix("features.", "value",lang)
  s"""
  {
    "_type": "product",
    "_id": "$id",
    "_source": {
      "include": [
        "id",
        "name",
        "features.name",
        "features.value",
        $nameFields,
        $featuresNameField,
        $featuresValueField,
        "category.path"
      ]
    }
  }""".stripMargin
}
val fetchConfigsList = list.map(id => getFetchConfig(id,_lang))






































val fetchConfigs = fetchConfigsList.filter {
  str => !str.isEmpty
}.mkString("[", ",", "]")


































val multipleGetQueryTemplate = s"""
{
  "docs": $fetchConfigs
}""".stripMargin

































