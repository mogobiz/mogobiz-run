package com.mogobiz.run.vo

/**
 *
 * Created by Christophe on 27/04/2014.
 */

/*abstract class BaseError(val code:Int,val message:String)
case class CommentCreationError(override val code:Int,override val message:String) extends BaseError(code,message)
*/

case class MogoError(code:Int, message:String)