package com.mogobiz.run.model

import java.util.Date

import com.mogobiz.run.vo.PagingParams

/**
 *
 * Created by smanciot on 07/09/14.
 */
case class Comment(id:Option[String], userId:String
                    , surname:String, notation:Int
                    , subject:String, comment:String
                    , created:Date, productId:Long
                    , useful:Int=0, notuseful:Int=0)

case class CommentRequest( userId:String, surname: String, notation:Int, subject:String, comment:String, created:Date = new Date){
  def validate() {
    if(!(notation>=0 && notation<=5)) throw CommentException(CommentException.BAD_NOTATION)
  }
}

case class CommentGetRequest(override val maxItemPerPage:Option[Int],override val pageOffset:Option[Int] ) extends PagingParams //(maxItemPerPage,pageOffset)

case class CommentPutRequest(note:Int)

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

case class CommentException(code:Int, message:String) extends Exception {}
