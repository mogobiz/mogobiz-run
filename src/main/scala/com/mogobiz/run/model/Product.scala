/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.model

import java.util.Date

import com.mogobiz.run.utils.PagingParams

class DatePeriod(val startDate:Date,val endDate:Date)
case class EndPeriod(start:Date,end:Date) extends DatePeriod(start,end)
case class IntraDayPeriod(override val startDate:Date,override val endDate:Date,
                          weekday1:Boolean,
                          weekday2:Boolean,
                          weekday3:Boolean,
                          weekday4:Boolean,
                          weekday5:Boolean,
                          weekday6:Boolean,
                          weekday7:Boolean
                           ) extends DatePeriod(startDate,endDate)




