package com.mogobiz.vo

import java.util.Date
import com.mogobiz.CommentException

/**
 *
 * Created by Christophe on 24/04/2014.
 */
object Comments {

}

case class Comment(id:Option[String], userId:String
                   , surname:String, notation:Int
                   , subject:String, comment:String
                   , created:Date, productId:Long
                   , useful:Int=0, notuseful:Int=0) //with Copying[Comment]

case class CommentRequest( userId:String, surname: String, notation:Int, subject:String, comment:String, created:Date = new Date){
  def validate() {
    if(!(notation>=0 && notation<=5)) throw CommentException(CommentException.BAD_NOTATION)
  }
}

case class CommentGetRequest(override val maxItemPerPage:Option[Int],override val pageOffset:Option[Int] ) extends PagingParams //(maxItemPerPage,pageOffset)

case class CommentPutRequest(note:Int)