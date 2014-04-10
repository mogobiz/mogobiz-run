package com.mogobiz

/**
 * Created by dach on 17/02/2014.
 */
case class Category(id: Int, code: String, name: String, translations: List[String])

case class CategoryRequest(hidden: Boolean, parent: Option[String], lang: String)