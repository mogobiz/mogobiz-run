package com.mogobiz.utils

import spray.http.{StatusCode, StatusCodes}

abstract class MogobizException(message: String, val code: StatusCode) extends Exception(message)

case class DuplicateException(message: String) extends MogobizException(message, StatusCodes.Conflict)

case class NotAuthorizedException(message: String) extends MogobizException(message, StatusCodes.Unauthorized)

case class NotFoundException(message: String) extends MogobizException(message, StatusCodes.NotFound)

object Exceptions {
  def toHTTPResponse(t: MogobizException): StatusCode = t match {
    case e: DuplicateException => StatusCodes.Conflict
    case e: NotAuthorizedException => StatusCodes.Unauthorized
    case e: NotFoundException => StatusCodes.NotFound
    case _ => StatusCodes.InternalServerError
  }
}