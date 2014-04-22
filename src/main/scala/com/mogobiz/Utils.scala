package com.mogobiz

import com.mogobiz.vo.{ PagingParams, Paging}
import org.json4s.JsonAST.JValue
import org.json4s.{DefaultFormats, Formats}

/**
 * Created by Christophe on 24/04/2014.
 */
object Utils {

  /* waiting an answer
  def addPaging[T](json:JValue,paging:PagingParams):Paging[T] = {
    implicit def json4sFormats: Formats = DefaultFormats

    val size = paging.maxItemPerPage.getOrElse(100)
    val from = paging.pageOffset.getOrElse(0) * size

    val hits = (json \"hits")
    val total : Int =  (hits \ "total").extract[Int]
    val subset = hits \ "hits" \ "_source"
    val results = subset.extract[List[T]]

    val pageCount = (size / total) +1
    val hasPrevious = from > size
    val hasNext = (from + size) < total

    val pagedResults = new Paging(results,results.size,total,size,from,pageCount,hasPrevious,hasNext)
    pagedResults
  }
  */

  /**
   * add paging to a results list
   * @param json
   * @param results
   * @param paging
   * @tparam T
   * @return
   */
  def addPaging[T](json:JValue,results:List[T],paging:PagingParams):Paging[T] = {
    implicit def json4sFormats: Formats = DefaultFormats

    val size = paging.maxItemPerPage.getOrElse(100)
    val from = paging.pageOffset.getOrElse(0) * size

    val hits = (json \"hits")
    val total : Int =  (hits \ "total").extract[Int]

    val pageCount = (size / total) +1
    val hasPrevious = from > size
    val hasNext = (from + size) < total

    val pagedResults = new Paging(results,results.size,total,size,from,pageCount,hasPrevious,hasNext)
    pagedResults
  }

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
