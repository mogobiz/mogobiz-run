/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.handlers

import com.mogobiz.MogobizRouteTest
import com.mogobiz.run.model.RequestParameters.FacetRequest

class FacetHandlerSpec extends MogobizRouteTest {

  val handler = new FacetHandler

  val storeCode = "mogobiz"


  "the facet handler" should "send back aggregation with nested features" in {
    ???
    //      skipped
    val req = new FacetRequest(5000, "fr")

    val res = handler.getProductCriteria(storeCode, req)

    //println(res)

    res should not be null
  }

  it should "send back aggreation results of nbcomments per notation on all comments index" in {
    val res = handler.getCommentNotations(storeCode, None)

    res.size should not be 0

  }

  it should "send back aggreation results of nbcomments per notation of a specific product" in {
    val res = handler.getCommentNotations(storeCode, Some(61))
    //println(res)
    res.size should not be 0

  }
}

