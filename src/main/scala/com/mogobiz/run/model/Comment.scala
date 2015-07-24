/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.model

import java.util.Date

import com.mogobiz.run.utils.PagingParams

case class Comment(id:String, userId:String
                   , surname:String, notation:Int
                   , subject:String, comment:String
                   , externalCode: Option[String]
                   , created:Date, productId:Long
                   , useful:Int=0, notuseful:Int=0)






