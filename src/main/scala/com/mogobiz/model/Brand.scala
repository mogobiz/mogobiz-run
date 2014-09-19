package com.mogobiz.model

case class Brand(id:Int, name:String, translations: List[String])

case class BrandRequest(hidden:Boolean, categoryPath: Option[String], lang:String)
