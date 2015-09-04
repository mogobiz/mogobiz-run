/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.learning

import java.util.{UUID, Calendar}

import com.mogobiz.es.EsClient
import com.mogobiz.run.config.Settings
import com.mogobiz.run.model.Learning.UserAction.UserAction
import com.mogobiz.run.model.Learning.UserItemAction


object UserActionRegistration {
  def esLearning(store: String): String = s"${store}_learning"

  val esIndexRotate = Settings.learning.rotate

  private def indexName(esIndex:String): String = {
    def dateFormat(date: Calendar, dateFormat: String) = {
      val format = new java.text.SimpleDateFormat(dateFormat)
      val str = format.format(date.getTime())
      str
    }
    val format = esIndexRotate match {
      case "DAILY" => Some("yyyy-MM-dd")
      case "MONTHLY" => Some("yyyy-MM")
      case "YEARLY" => Some("yyyy")
      case _ => None
    }
    val suffix = format map (dateFormat(Calendar.getInstance(), _)) map ("_" + _) getOrElse ("")
    esIndex + suffix
  }


  def register(store: String, trackingid: String, itemid: String, action: UserAction): String = {
    EsClient.index(indexName(esLearning(store)), UserItemAction(trackingid, itemid, action), false, Some(UUID.randomUUID().toString))
  }
}