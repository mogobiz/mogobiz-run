package com.mogobiz.utils

import spray.http.{StatusCode, StatusCodes}

case class DuplicateException(message: String) extends Exception

case class NotAuthorizedException(s: String) extends Exception

case class NotFoundException(s: String) extends Exception

object Exceptions {
  def toHTTPResponse(t: Throwable): StatusCode = t match {
    case e: DuplicateException => StatusCodes.Conflict
    case e: NotAuthorizedException => StatusCodes.Unauthorized
    case e: NotFoundException => StatusCodes.NotFound
    case _ => StatusCodes.InternalServerError
  }
}