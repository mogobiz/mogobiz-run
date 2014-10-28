package com.mogobiz.model


/**
 * Created by hayssams on 28/10/14.
 */
object UserAction extends Enumeration {
  type UserAction = Value
  val Purchase = Value("purchase")
  val View = Value("view")
}            ·

import java.util.Calendar
import java.util.Date

import UserAction._

case class UserItemAction(val trackingid:String, val itemid:String, action:UserAction, timestamp : Date = Calendar.getInstance().getTime)
