/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.exceptions

import spray.http.{StatusCode, StatusCodes}

abstract class MogobizException(message: String, val code: StatusCode) extends Exception(message)

case class SomeParameterIsMissingException(message: String) extends MogobizException(message, StatusCodes.BadRequest)

case class DuplicateException(message: String) extends MogobizException(message, StatusCodes.Conflict)

case class NotAuthorizedException(message: String) extends MogobizException(message, StatusCodes.Unauthorized)

case class NotFoundException(message: String) extends MogobizException(message, StatusCodes.NotFound)

case class MinMaxQuantityException(min: Long, max: Long) extends MogobizException(s"$min|$max", StatusCodes.BadRequest)

case class DateIsNullException() extends MogobizException("", StatusCodes.BadRequest)

case class UnsaleableDateException() extends MogobizException("", StatusCodes.BadRequest)

case class UnsaleableProductException() extends MogobizException("", StatusCodes.BadRequest)

case class NotEnoughRegisteredCartItemException() extends MogobizException("", StatusCodes.BadRequest)

case class InsufficientStockCartItemException() extends MogobizException("", StatusCodes.BadRequest)

case class InsufficientStockCouponException() extends MogobizException("", StatusCodes.BadRequest)

case class CommentAlreadyExistsException() extends MogobizException("", StatusCodes.Conflict)

case class IllegalStatusException() extends MogobizException("", StatusCodes.BadRequest)

object Exceptions {
  def toHTTPResponse(t: MogobizException): StatusCode = t match {
    case e: DuplicateException     => StatusCodes.Conflict
    case e: NotAuthorizedException => StatusCodes.Unauthorized
    case e: NotFoundException      => StatusCodes.NotFound
    case _                         => StatusCodes.InternalServerError
  }
}

object CommentException {
  val SUCCESS           = 0
  val UNEXPECTED_ERROR  = 1
  val BAD_NOTATION      = 2
  val COMMENT_TOO_SHORT = 3
  val UPDATE_ERROR      = 4

  def apply(errorCode: Int): CommentException = {
    errorCode match {
      case BAD_NOTATION      => CommentException(BAD_NOTATION, "Illegal notation value")
      case COMMENT_TOO_SHORT => CommentException(COMMENT_TOO_SHORT, "Comment text not long enough")
      case UPDATE_ERROR      => CommentException(UPDATE_ERROR, "Update error")
      case _                 => CommentException(UNEXPECTED_ERROR, "Unexpected error")
    }
  }

}

case class CommentException(code: Int, message: String) extends Exception {}
