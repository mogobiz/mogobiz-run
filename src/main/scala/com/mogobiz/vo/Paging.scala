package com.mogobiz.vo

/**
 * Created by Christophe on 24/04/2014.
 */

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


/**
 *
 * @param maxItemPerPage
 * @param pageOffset
 */
class PagingParams(val maxItemPerPage:Option[Int],val pageOffset:Option[Int])
