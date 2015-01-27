package com.mogobiz.run.model

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration


object UserAction extends Enumeration {
  type UserAction = Value
  val Purchase = Value("purchase")
  val View = Value("view")
}

class UserActionRef extends TypeReference[UserAction.type]

import java.util.Calendar
import java.util.Date

import UserAction._

case class UserItemAction(val trackingid: String,
                          val itemid: String,
                          @JsonScalaEnumeration(classOf[UserActionRef])
                          action: UserAction,
                          timestamp: Date = Calendar.getInstance().getTime)

case class CartAction(val trackingid: String, val itemids: String, timestamp: Date = Calendar.getInstance().getTime)
