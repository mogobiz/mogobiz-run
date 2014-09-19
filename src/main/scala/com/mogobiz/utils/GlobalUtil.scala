package com.mogobiz.utils

import spray.http.HttpResponse

import scala.concurrent.{ExecutionContext, Future}

object GlobalUtil {
  def now = new java.util.Date()

  def newUUID = java.util.UUID.randomUUID().toString

  // From: http://stackoverflow.com/a/1227643/604041
  def caseClassToMap(cc: AnyRef) =
    (Map[String, Any]() /: cc.getClass.getDeclaredFields) { (a, f) =>
      f.setAccessible(true)
      a + (f.getName -> f.get(cc))
    }

  def mapToQueryString(m: Map[String, Any]): String = {
    mapToQueryString(m.toList)
  }

  def mapToQueryString(m: List[(String, Any)]): String = {
    m.map { case (k, v) =>
      println(s"$k=$v")
      s"$k=$v"
    }.mkString("&")
  }

  def fromHttResponse(response: Future[HttpResponse])(implicit ev: ExecutionContext): Future[Map[String, String]] = {
    response map { response =>
      val data = response.entity.asString.trim
      println(s"data $data")
      val pairs = data.split('&')
      val tuples = (pairs map { pair =>
        val tab = pair.split('=')
        tab(0) -> (if (tab.length == 1) "" else tab(1))
      }).toMap
      tuples
    }
  }
}