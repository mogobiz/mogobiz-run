package com.mogobiz.services

import com.mogobiz.MogobizRouteTest
import org.specs2.matcher._
import org.json4s.native.JsonParser
import org.json4s.JsonAST._

class PromotionServiceSpec extends MogobizRouteTest {

  "The Promotion service" should {
    "return promotions" in {
      Get("/store/" + STORE + "/promotions") ~> sealRoute(routes) ~> check {

        val response: JValue = JsonParser.parse(responseAs[String])
        println(response)
        //Paging[T](val list:List[T],val pageSize:Int,val totalCount:Int,val maxItemsPerPage:Int,val pageOffset:Int = 0,val pageCount:Int,val hasPrevious:Boolean=false,val hasNext:Boolean=false)
        //JObject(List((list,JArray(List())), (pageSize,JInt(0)), (totalCount,JInt(0)), (maxItemsPerPage,JInt(100)), (pageOffset,JInt(0)), (pageCount,JInt(0)), (hasPrevious,JBool(false)), (hasNext,JBool(false))))
        response \ "totalCount" must be_==(JInt(0))
      }
    }
  }
}
