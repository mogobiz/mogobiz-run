package com.mogobiz

import org.json4s.JsonAST.{JObject, JInt, JArray, JValue}

/**
 *
 * Created by yoannbaudy on 10/09/14.
 */
trait JSonTest {

  def sortById(j: JValue): List[JValue] = {
    j match {
      case JArray(a) => a.sortWith((v1: JValue, v2: JValue) => {
        (v1 \ "id", v2 \ "id") match {
          case (JInt(id1), JInt(id2)) => id1 < id2
          case _ => true
        }
      })
      case JObject(v) => List(JObject(v))
      case _ => List(j)
    }
  }

}
