package com.mogobiz

/**
 * Created by Christophe on 17/02/14.
 */

case class Tag(id:Int,name:String,translations:List[String])

case class TagRequest(hidden:Boolean,inactive:Boolean, lang:String)