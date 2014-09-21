package com.mogobiz.utils

import spray.http.{StatusCodes, StatusCode}

case class DuplicateException(message: String) extends Exception

object Exceptions {
  def toHTTPResponse(t: Throwable): StatusCode = t match {
    case e: DuplicateException => StatusCodes.Conflict
    case _ => StatusCodes.InternalServerError
  }
}