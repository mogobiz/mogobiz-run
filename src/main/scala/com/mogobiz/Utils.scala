package com.mogobiz

import com.mogobiz.vo.{ PagingParams, Paging}
import org.json4s.JsonAST.JValue
import org.json4s.{FieldSerializer, DefaultFormats, Formats}

/**
 * Created by Christophe on 24/04/2014.
 */
object Utils {


}

/**
 * @see http://jayconrod.com/posts/32/convenient-updates-for-immutable-objects-in-scala
 * @tparam T
 */
trait Copying[T] {
  def copyWith(changes: (String, AnyRef)*): T = {
    val clas = getClass
    val constructor = clas.getDeclaredConstructors.head
    val argumentCount = constructor.getParameterTypes.size
    val fields = clas.getDeclaredFields
    val arguments = (0 until argumentCount) map { i =>
      val fieldName = fields(i).getName
      changes.find(_._1 == fieldName) match {
        case Some(change) => change._2
        case None => {
          val getter = clas.getMethod(fieldName)
          getter.invoke(this)
        }
      }
    }
    constructor.newInstance(arguments: _*).asInstanceOf[T]
  }
}
