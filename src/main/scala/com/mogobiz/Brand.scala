package com.mogobiz

/**
 * Created by Christophe on 17/02/14.
 */
case class Brand(id:Int, name:String, translations: List[String])

case class BrandRequest(hidden:Boolean,lang:String)