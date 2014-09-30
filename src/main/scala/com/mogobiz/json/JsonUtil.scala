package com.mogobiz.json

import org.json4s.JsonAST.JDecimal
import org.json4s.JsonAST.JDouble
import org.json4s.JsonAST.JField
import org.json4s.JsonAST.JNothing
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JString
import org.json4s.JsonAST.JValue
import org.json4s.JsonAST.JInt
import org.json4s.JsonAST.JArray
import org.json4s._

/**
 *
 * Created by yoannbaudy on 10/09/14.
 */
trait JsonUtil {

  def compareByProperty(v1: JValue, v2: JValue, p: String): Boolean = {
    (v1 \ p, v2 \ p) match {
      case (JInt(id1), JInt(id2)) => id1 == id2
      case (JDouble(id1), JDouble(id2)) => id1 == id2
      case (JDecimal(id1), JDecimal(id2)) => id1 == id2
      case (JString(id1), JString(id2)) => id1 == id2
      case _ => false
    }
  }

  def compareById(v1: JValue, v2: JValue): Boolean = compareByProperty(v1, v2, "id")

  def sortByProperty(j: JValue, p: String): List[JValue] = {
    j match {
      case JArray(a) => a.sortWith((v1: JValue, v2: JValue) => {
        (v1 \ p, v2 \ p) match {
          case (JInt(id1), JInt(id2)) => id1 < id2
          case (JDouble(id1), JDouble(id2)) => id1 < id2
          case (JDecimal(id1), JDecimal(id2)) => id1 < id2
          case (JString(id1), JString(id2)) => id1 < id2
          case _ => true
        }
      })
      case JObject(v) => List(JObject(v))
      case _ => List(j)
    }
  }

  def sortById(j: JValue): List[JValue] = sortByProperty(j, "id")

  def contains(list: List[JValue], v: JValue, p: String): Boolean = {
    // Boucle qui permet de tester le hd puis le tail en arretant dès que la valeur est trouvée
    def loop(list: List[JValue], v: JValue): Boolean = list match {
      case hd :: tail if compareByProperty(hd, v, p) => true
      case hd :: tail => loop(tail, v)
      case Nil => false
    }

    loop(list, v)
  }

  def distinctByProperty(j: JValue, p: String): List[JValue] = {
    j match {
      case JArray(a) =>
        // Boucle qui permet de créer la list distinct en vérifiant que chaque élément n'existe pas déjà
        def loop(list: List[JValue]): List[JValue] = list match {
          case hd :: tail if contains(tail, hd, p) => loop(tail)
          case hd :: tail => hd :: loop(tail)
          case Nil => Nil
        }
        loop(a)
      case JObject(v) => List(JObject(v))
      case _ => List(j)
    }
  }
  def distinctById(j: JValue): List[JValue] = distinctByProperty(j, "id")

  def extractJSonProperty(source: JValue, property: String): JValue = {
    extractJSonProperty(source, property, JNothing)
  }

  def extractJSonProperty(source: JValue, property: String, defaultValue: JValue): JValue = {
    source match {
      case o: JObject =>
        o.obj.find {p: JField => p._1 == property}.getOrElse(JField(property, defaultValue))._2
      case _ => defaultValue
    }
  }

  def removeFields(jValue: JValue, fields: List[String]): JValue = {
    if (fields.isEmpty) jValue
    else {
      removeFields(jValue removeField {
        f => f._1 == fields.head
      }, fields.tail)
    }
  }
}
