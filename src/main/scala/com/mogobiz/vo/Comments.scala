package com.mogobiz.vo

import java.util.Date
import com.mogobiz.{CommentException, Copying}

/**
 * Created by Christophe on 24/04/2014.
 */
object Comments {

}

case class Comment(val id:Option[String], val userId:String
                   ,val surname:String,val notation:Int
                   ,val subject:String,val comment:String
                   ,val created:Date, val productId:Long
                   ,val useful:Int=0,val notuseful:Int=0) //with Copying[Comment]

case class CommentRequest(val userId:String, val surname: String,val notation:Int, val subject:String,val comment:String, val created:Date = new Date){
  def validate() {
    if(!(notation>=0 && notation<=5)) throw CommentException(CommentException.BAD_NOTATION)
    //if((subject.size + comment.size) < 130) throw CommentException(CommentException.COMMENT_TOO_SHORT)

  }
}

case class CommentGetRequest(override val maxItemPerPage:Option[Int],override val pageOffset:Option[Int] ) extends PagingParams //(maxItemPerPage,pageOffset)

case class CommentPutRequest(val note:Int)