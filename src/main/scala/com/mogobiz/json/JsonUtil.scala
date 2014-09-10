package com.mogobiz.json

import org.json4s.JsonAST._
import org.json4s.JsonAST.JInt
import org.json4s.JsonAST.JArray

/**
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

}
