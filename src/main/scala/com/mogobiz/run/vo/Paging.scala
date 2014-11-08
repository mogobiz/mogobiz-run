package com.mogobiz.run.vo

import org.json4s.JsonAST.JValue
import org.json4s._

/**
 *
 * Created by Christophe on 24/04/2014.
 */

object Paging {

  def add[T](total:Int, results:List[T],pagingParams:PagingParams):Paging[T] = {
    val paging = get(total, pagingParams)
    val pagedResults = new Paging(results, results.size, paging.totalCount, paging.maxItemsPerPage, paging.pageOffset, paging.pageCount, paging.hasPrevious, paging.hasNext)
    pagedResults
  }

  /**
   * Get the paging object without the results and the pageSize
   * @param total
   * @param paging
   * @return
   */
  def get(total:Int,paging:PagingParams) : Paging[Any] = {

    val size = paging.maxItemPerPage.getOrElse(100)
    val from = paging.pageOffset.getOrElse(0) * size

    val pageCount = Math.rint(((total * 1d) / size) + 0.5)

    val hasPrevious = from > size
    val hasNext = (from + size) < total

    val p = new Paging[Any](List[Any](),0,total,size,from,pageCount.toInt,hasPrevious,hasNext)
    p
  }


  /**
   * Get the paging Structure as JSON
   * @param total
   * @param pagingParams
   * @return
   */
  def getWrapper(total:Int, pagingParams:PagingParams) : JValue = {
    import org.json4s.native.JsonMethods._
    import org.json4s.native.Serialization.write
    implicit def json4sFormats: Formats = DefaultFormats + FieldSerializer[Paging[Any]]()

    val paging = get(total, pagingParams)

    val pagingObj = parse(write(paging))
//    println(pretty(render(pagingObj )))
    pagingObj
  }

  /**
   * Wrap the JSON results with in a Paging JSON structure
   * @param total
   * @param results
   * @param pagingParams
   * @return
   */
  def wrap(total:Int,results:JValue,pagingParams:PagingParams):JValue = {
    import org.json4s.JsonDSL._
    import org.json4s._
    implicit def json4sFormats: Formats = DefaultFormats

    val pagingWrapper = getWrapper(total, pagingParams)
    val resultWrapper = JObject(List(JField("list",results))) //parse("""{"list":$results"") //(("list"->results))
    val pageSizeWrapper = JObject(List(JField("pageSize",results.children.size)))

    val merged = resultWrapper merge pagingWrapper merge pageSizeWrapper
    merged

  }
}

/**
 *
 * @param list : paged results
 * @param pageSize : number of results for this page
 * @param totalCount : number of total results for the query
 * @param maxItemsPerPage : number of results requested per page
 * @param pageOffset : index of the current page
 * @param pageCount : total number of pages
 * @param hasPrevious : does a previous page exist
 * @param hasNext : does a next page exist
 * @tparam T type of results
 */
class Paging[T](val list:List[T],val pageSize:Int,val totalCount:Int,val maxItemsPerPage:Int,val pageOffset:Int = 0,val pageCount:Int,val hasPrevious:Boolean=false,val hasNext:Boolean=false)


trait PagingParams {
  val maxItemPerPage:Option[Int]
  val pageOffset:Option[Int]
}
