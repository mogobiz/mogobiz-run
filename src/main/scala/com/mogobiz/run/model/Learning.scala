/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.model

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration

object Learning {
  def esPurchasePredictions(store: String, segment: Option[String]): String = s"${store}_predictions_purchase" + segment.map("_" + _).getOrElse("").toLowerCase()

  def esViewPredictions(store: String, segment: Option[String]): String = s"${store}_predictions_view" + segment.map("_" + _).getOrElse("").toLowerCase()

  def esLearningStorePattern(store: String): String = s"${store}_learning_.*"

  def esFISStore(store: String, segment: Option[String]): String = s"${store}_fis" + segment.map("_" + _).getOrElse("").toLowerCase()

  object UserAction extends Enumeration {
    type UserAction = Value
    val Purchase = Value("purchase")
    val View = Value("view")
  }

  class UserActionRef extends TypeReference[UserAction.type]

  import java.util.Calendar
  import java.util.Date

  import UserAction._

  case class UserItemAction(uuid: String,
                            itemid: String,
                            @JsonScalaEnumeration(classOf[UserActionRef]) action: UserAction,
                            count: Int,
                            var dateCreated: Date = Calendar.getInstance().getTime,
                            var lastUpdated: Date = Calendar.getInstance().getTime) {
    def toMap: Map[String, String] = {
      Map("uuid" -> uuid, "itemid" -> itemid, "action" -> action.toString, "dateCreated" -> dateCreated.getTime.toString, "lastUpdated" -> lastUpdated.getTime.toString)
    }
  }

  case class CartAction(uuid: String,
                        itemids: String,
                        var dateCreated: Date = Calendar.getInstance().getTime,
                        var lastUpdated: Date = Calendar.getInstance().getTime) {
    def toMap: Map[String, String] = {
      Map("uuid" -> uuid, "items" -> itemids, "dateCreated" -> dateCreated.getTime.toString, "lastUpdated" -> lastUpdated.getTime.toString)
    }
  }

  case class Prediction(uid: String, purchase: List[String], view: List[String], timestamp: Long)

  case class CartCombination(
                              uuid: String,
                              combinations: List[String],
                              counter: Long,
                              var dateCreated: Date = Calendar.getInstance().getTime,
                              var lastUpdated: Date = Calendar.getInstance().getTime)

}
