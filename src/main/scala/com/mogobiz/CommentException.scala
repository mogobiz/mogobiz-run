package com.mogobiz

/**
 * Created by Christophe on 27/04/2014.
 */
object CommentException{
  val SUCCESS = 0
  val UNEXPECTED_ERROR = 1
  val BAD_NOTATION = 2
  val COMMENT_TOO_SHORT = 3
  val UPDATE_ERROR = 4


  def apply(errorCode:Int):CommentException = {
    errorCode match {
      case BAD_NOTATION => CommentException(BAD_NOTATION,"Illegal notation value")
      case COMMENT_TOO_SHORT => CommentException(COMMENT_TOO_SHORT,"Comment text not long enough")
      case UPDATE_ERROR => CommentException(UPDATE_ERROR,"Update error")
      case _ => CommentException(UNEXPECTED_ERROR,"Unexpected error")
    }
  }

}
case class CommentException(val code:Int,val message:String) extends Exception {

}
